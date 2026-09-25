package io.github.meko123456.dayblocks.core.domain.repository

import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DayOfWeek

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
}
