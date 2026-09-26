package io.github.meko123456.dayblocks.composeapp.settings

import androidx.compose.runtime.Composable
import io.github.meko123456.dayblocks.feature.settings.BackupFiles

/** A document the user picks, each platform its own way: the system picker, or the share sheet and Files. */
@Composable
expect fun rememberBackupFiles(): BackupFiles
