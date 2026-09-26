package io.github.meko123456.dayblocks.core.data.repository

import io.github.meko123456.dayblocks.core.common.AppDispatchers
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.domain.model.AppSettings
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.QuietHours
import io.github.meko123456.dayblocks.core.domain.model.ThemeMode
import io.github.meko123456.dayblocks.core.domain.repository.BackupRepository
import io.github.meko123456.dayblocks.core.domain.repository.ImportResult
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * [BackupRepository] on the database and the settings store.
 *
 * Import is all or nothing. The whole file is read and checked against the same rules the app
 * enforces everywhere else — a real title, a span inside a day, a known category — before a
 * single row changes; then the old plan is replaced in one transaction. A file that fails any
 * check leaves everything exactly as it was. Whether onboarding is done is the one thing a backup
 * never changes: restoring onto a new phone should not send anyone through it again.
 */
internal class SqlBackupRepository(
    private val database: DayBlocksDatabase,
    private val settings: SettingsRepository,
    private val dispatchers: AppDispatchers,
    private val time: TimeProvider,
) : BackupRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun export(): String = withContext(dispatchers.io) {
        val file = database.transactionWithResult {
            BackupFile(
                format = FORMAT,
                version = VERSION,
                exportedAt = time.now().toString(),
                blocks = database.blockQueries.selectAll().executeAsList().map {
                    BackupBlock(it.id, it.plan_date, it.title, it.category, it.start_minutes.toInt(), it.end_minutes.toInt(), it.note)
                },
                templates = database.templateQueries.selectAllWithBlocks().executeAsList().groupBy { it.id }.map { (id, rows) ->
                    BackupTemplate(
                        id = id,
                        name = rows.first().name,
                        blocks = rows.filter { it.position != null }.map {
                            BackupTemplateBlock(it.title!!, it.category!!, it.start_minutes!!.toInt(), it.end_minutes!!.toInt(), it.note)
                        },
                    )
                },
                weekdays = database.templateQueries.selectWeekdays().executeAsList().associate { it.weekday to it.template_id },
                records = database.blockRecordQueries.selectAll().executeAsList().map {
                    BackupRecord(it.block_id, it.answer, it.answered_at, it.outcome, it.outcome_at)
                },
            )
        }
        val buddy = settings.observeBuddy().first()
        val app = settings.observeApp().first()
        json.encodeToString(
            file.copy(
                buddy = BackupBuddy(buddy.name, buddy.tone.name, buddy.quietHours.start.toString(), buddy.quietHours.end.toString(), buddy.dailyCap),
                app = BackupApp(app.theme.name, app.timelineStartHour, app.timelineEndHour, app.categoryColors.mapKeys { it.key.name }),
            ),
        )
    }

    override suspend fun import(json: String): ImportResult = withContext(dispatchers.io) {
        val file = try {
            this@SqlBackupRepository.json.decodeFromString<BackupFile>(json)
        } catch (e: SerializationException) {
            return@withContext ImportResult.Rejected("This is not a DayBlocks backup.")
        } catch (e: IllegalArgumentException) {
            return@withContext ImportResult.Rejected("This is not a DayBlocks backup.")
        }
        if (file.format != FORMAT) return@withContext ImportResult.Rejected("This is not a DayBlocks backup.")
        if (file.version > VERSION) return@withContext ImportResult.Rejected("This backup was made by a newer DayBlocks. Update the app first.")
        val checked = try {
            file.checked()
        } catch (e: IllegalArgumentException) {
            return@withContext ImportResult.Rejected("This backup is damaged: ${e.message}")
        }

        database.transaction {
            database.blockQueries.deleteAll()
            database.templateQueries.deleteAllTemplates()
            checked.blocks.forEach {
                database.blockQueries.insert(it.id, it.date, it.title, it.category, it.start.toLong(), it.end.toLong(), it.note, time.now().toEpochMilliseconds())
            }
            checked.templates.forEach { template ->
                database.templateQueries.insert(template.id, template.name)
                template.blocks.forEachIndexed { position, b ->
                    database.templateQueries.insertBlock(template.id, position.toLong(), b.title, b.category, b.start.toLong(), b.end.toLong(), b.note)
                }
            }
            checked.weekdays.forEach { (weekday, templateId) -> database.templateQueries.assignWeekday(weekday, templateId) }
            checked.records.forEach { database.blockRecordQueries.insertRecord(it.blockId, it.answer, it.answeredAt, it.outcome, it.outcomeAt) }
        }
        file.buddy?.let { b ->
            val restored = runCatching {
                BuddySettings(
                    name = b.name,
                    tone = BuddyTone.valueOf(b.tone),
                    quietHours = QuietHours(LocalTime.parse(b.quietStart), LocalTime.parse(b.quietEnd)),
                    dailyCap = b.dailyCap,
                )
            }.getOrNull()
            if (restored != null) settings.updateBuddy { restored }
        }
        file.app?.let { a ->
            runCatching {
                AppSettings(
                    theme = ThemeMode.valueOf(a.theme),
                    timelineStartHour = a.timelineStartHour,
                    timelineEndHour = a.timelineEndHour,
                    categoryColors = a.categoryColors.mapKeys { Category.valueOf(it.key) },
                )
            }.getOrNull()?.let { restored -> settings.updateApp { restored.copy(onboarded = it.onboarded) } }
        }
        ImportResult.Imported(blocks = checked.blocks.size, templates = checked.templates.size, records = checked.records.size)
    }

    private companion object {
        const val FORMAT = "dayblocks-backup"
        const val VERSION = 1
    }

    /**
     * The file checked against the app's own rules — each block and template block through the
     * same constructors the app uses, which refuse a blank title or an impossible span.
     */
    private fun BackupFile.checked(): BackupFile {
        blocks.forEach { b ->
            LocalDate.parse(b.date)
            Category.valueOf(b.category)
            DaySpan(b.start, b.end)
            require(b.title.isNotBlank()) { "a block without a title" }
        }
        require(blocks.map { it.id }.toSet().size == blocks.size) { "two blocks share an id" }
        templates.forEach { t ->
            require(t.name.isNotBlank()) { "a template without a name" }
            t.blocks.forEach { b ->
                Category.valueOf(b.category)
                DaySpan(b.start, b.end)
                require(b.title.isNotBlank()) { "a template block without a title" }
            }
        }
        val templateIds = templates.map { it.id }.toSet()
        weekdays.forEach { (day, id) ->
            DayOfWeek.valueOf(day)
            require(id in templateIds) { "a weekday assigned to a template that is not in the file" }
        }
        records.forEach { r ->
            r.answer?.let { CheckInAnswer.valueOf(it) }
            r.outcome?.let { BlockOutcome.valueOf(it) }
        }
        return this
    }
}

/**
 * [format] and [version] have no defaults on purpose. With defaults, any JSON object at all — `{}` —
 * decoded as an empty backup, and importing it would have replaced the whole plan with nothing.
 * A backup has to say what it is.
 */
@Serializable
internal data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: String = "",
    val blocks: List<BackupBlock> = emptyList(),
    val templates: List<BackupTemplate> = emptyList(),
    val weekdays: Map<String, String> = emptyMap(),
    val records: List<BackupRecord> = emptyList(),
    val buddy: BackupBuddy? = null,
    val app: BackupApp? = null,
)

@Serializable
internal data class BackupBlock(val id: String, val date: String, val title: String, val category: String, val start: Int, val end: Int, val note: String? = null)

@Serializable
internal data class BackupTemplate(val id: String, val name: String, val blocks: List<BackupTemplateBlock>)

@Serializable
internal data class BackupTemplateBlock(val title: String, val category: String, val start: Int, val end: Int, val note: String? = null)

@Serializable
internal data class BackupRecord(val blockId: String, val answer: String? = null, val answeredAt: Long? = null, val outcome: String? = null, val outcomeAt: Long? = null)

@Serializable
internal data class BackupBuddy(val name: String, val tone: String, val quietStart: String, val quietEnd: String, val dailyCap: Int)

@Serializable
internal data class BackupApp(val theme: String, val timelineStartHour: Int, val timelineEndHour: Int, val categoryColors: Map<String, Long> = emptyMap())
