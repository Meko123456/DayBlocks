package io.github.meko123456.dayblocks.core.designsystem.time

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberIs24HourFormat(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
