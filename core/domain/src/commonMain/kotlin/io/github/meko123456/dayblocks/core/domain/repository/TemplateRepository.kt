package io.github.meko123456.dayblocks.core.domain.repository

import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Saved day templates and which weekday each one auto-fills. */
interface TemplateRepository {
    fun observeTemplates(): Flow<List<Template>>

    suspend fun template(id: TemplateId): Template?

    /** Saves the template and its blocks together; its previous blocks are replaced, not merged. */
    suspend fun upsert(template: Template)

    /** Deleting a template also clears any weekday it was assigned to. */
    suspend fun delete(id: TemplateId)

    fun observeWeekdayAssignments(): Flow<Map<DayOfWeek, TemplateId>>

    /** Assigns [template] to [day], or clears the day when [template] is null. */
    suspend fun assign(day: DayOfWeek, template: TemplateId?)

    /**
     * Records that [date] has had its one chance at auto-fill, returning true only to the first
     * caller. A day is filled from its weekday's template at most once, so clearing a filled day
     * — or planning it by hand before the app next opens — is never undone by the template
     * coming back.
     */
    suspend fun claimAutoFill(date: LocalDate, at: Instant): Boolean
}
