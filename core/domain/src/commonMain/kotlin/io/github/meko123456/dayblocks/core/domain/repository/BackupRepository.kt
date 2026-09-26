package io.github.meko123456.dayblocks.core.domain.repository

/**
 * Everything the user has made, as one JSON document: every block, template, weekday assignment,
 * recorded outcome and setting. What it is for is moving to a new phone and keeping a copy —
 * there is no account and no cloud, so this file is the backup.
 */
interface BackupRepository {
    suspend fun export(): String

    /**
     * Replaces everything with what [json] holds, or — if it is not a DayBlocks backup this
     * version can read — changes nothing at all.
     */
    suspend fun import(json: String): ImportResult
}

sealed interface ImportResult {
    data class Imported(val blocks: Int, val templates: Int, val records: Int) : ImportResult

    data class Rejected(val reason: String) : ImportResult
}
