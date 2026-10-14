package io.github.meko123456.dayblocks.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.meko123456.dayblocks.core.common.formatClock
import io.github.meko123456.dayblocks.core.designsystem.CategoryPalette
import io.github.meko123456.dayblocks.core.designsystem.accentOutlinedTextFieldColors
import io.github.meko123456.dayblocks.core.designsystem.accentTextButtonColors
import io.github.meko123456.dayblocks.core.designsystem.buddy.BuddyFace
import io.github.meko123456.dayblocks.core.designsystem.color
import io.github.meko123456.dayblocks.core.designsystem.time.rememberIs24HourFormat
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.ThemeMode
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(onClose: () -> Unit, files: BackupFiles, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.Close -> onClose()
                is SettingsEffect.Export -> files.save(effect.fileName, effect.json)
                SettingsEffect.ChooseImport -> files.open { json -> viewModel.onIntent(SettingsIntent.ImportChosen(json)) }
                is SettingsEffect.Message -> snackbar.showSnackbar(effect.text)
            }
        }
    }
    SettingsContent(state, snackbar, viewModel::onIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsContent(state: SettingsState, snackbar: SnackbarHostState, onIntent: (SettingsIntent) -> Unit) {
    val is24Hour = rememberIs24HourFormat()
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onIntent(SettingsIntent.BackTapped) }, colors = accentTextButtonColors()) { Text("‹ Back") }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Section("Your buddy") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BuddyFace(BuddyMood.Happy, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = state.nameDraft,
                        onValueChange = { onIntent(SettingsIntent.NameChanged(it)) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = accentOutlinedTextFieldColors(),
                    )
                }
                Text("Tone", style = MaterialTheme.typography.labelLarge)
                // Wraps rather than squeezing: three abreast at the largest text size, "Pushy" broke
                // into "Pus hy".
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuddyTone.entries.forEach { tone ->
                        Choice(tone.name, selected = state.buddy.tone == tone) { onIntent(SettingsIntent.TonePicked(tone)) }
                    }
                }
                Text(tone(state.buddy.tone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }

            Section("Reminders") {
                Stepper("Quiet from", formatClock(state.buddy.quietHours.start.minutes, is24Hour), { onIntent(SettingsIntent.QuietStartMoved(-30)) }, { onIntent(SettingsIntent.QuietStartMoved(30)) })
                Stepper("Quiet until", formatClock(state.buddy.quietHours.end.minutes, is24Hour), { onIntent(SettingsIntent.QuietEndMoved(-30)) }, { onIntent(SettingsIntent.QuietEndMoved(30)) })
                Stepper("At most, a day", "${state.buddy.dailyCap}", { onIntent(SettingsIntent.CapMoved(-1)) }, { onIntent(SettingsIntent.CapMoved(1)) })
                Text(
                    "Blocks you plan inside quiet hours still say when they start. Everything else waits, or is let go.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            Section("Today") {
                Stepper("Timeline from", formatClock(state.app.timelineStartHour * 60, is24Hour), { onIntent(SettingsIntent.TimelineStartMoved(-1)) }, { onIntent(SettingsIntent.TimelineStartMoved(1)) })
                Stepper("Timeline to", if (state.app.timelineEndHour == 24) "Midnight" else formatClock(state.app.timelineEndHour * 60, is24Hour), { onIntent(SettingsIntent.TimelineEndMoved(-1)) }, { onIntent(SettingsIntent.TimelineEndMoved(1)) })
            }

            Section("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Choice(mode.name, selected = state.app.theme == mode) { onIntent(SettingsIntent.ThemePicked(mode)) }
                    }
                }
                Text("Category colours", style = MaterialTheme.typography.labelLarge)
                val nameWidth = categoryNameWidth()
                Category.entries.forEach { category -> ColorRow(category, nameWidth, custom = category in state.app.categoryColors, onIntent) }
            }

            Section("Your data") {
                Text(
                    "Everything stays on this phone. A backup is one file with every block, template and check-in in it — to keep, or to move to a new phone.",
                    style = MaterialTheme.typography.bodySmall,
                )
                // Wraps rather than squeezing: side by side at 2x text, "Import" was pressed into a
                // strip one letter wide.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onIntent(SettingsIntent.ExportTapped) }) { Text("Export a backup") }
                    OutlinedButton(onClick = { onIntent(SettingsIntent.ImportTapped) }) { Text("Import") }
                }
            }
        }
    }

    if (state.confirmingImport != null) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.ImportDismissed) },
            title = { Text("Replace everything?") },
            text = { Text("Every block, template and check-in on this phone will be replaced by the backup's. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onIntent(SettingsIntent.ImportConfirmed) }, colors = accentTextButtonColors()) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { onIntent(SettingsIntent.ImportDismissed) }, colors = accentTextButtonColors()) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * A label with its controls on the right. The label takes the room the controls leave, and moves
 * above them when even its longest word would not fit there: squeezed, it broke words in two, as
 * "Timel ine to" at the largest iOS text size.
 */
@Composable
private fun Stepper(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    val tint = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Text(label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onLess, colors = tint, modifier = Modifier.semantics { contentDescription = "Less: $label" }) { Text("−") }
                Text(value, Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold)
                FilledTonalIconButton(onClick = onMore, colors = tint, modifier = Modifier.semantics { contentDescription = "More: $label" }) { Text("+") }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val controls = measurables[1].measure(Constraints(maxWidth = width))
        val room = (width - controls.width).coerceAtLeast(0)
        val stacked = measurables[0].minIntrinsicWidth(Constraints.Infinity) > room
        val text = measurables[0].measure(Constraints(maxWidth = if (stacked) width else room))
        if (stacked) {
            layout(width, text.height + controls.height) {
                text.place(0, 0)
                controls.place(width - controls.width, text.height)
            }
        } else {
            val height = maxOf(text.height, controls.height)
            layout(width, height) {
                // Centred exactly as the Row this replaces centred them, rounding included.
                text.place(0, Alignment.CenterVertically.align(text.height, height))
                controls.place(width - controls.width, Alignment.CenterVertically.align(controls.height, height))
            }
        }
    }
}

/**
 * One width for the category names: 88dp, or the widest name in the current text style, so every
 * row's swatches start in the same place. A fixed 88dp broke "Reading" into "Readin g" at the
 * largest iOS text size.
 */
@Composable
private fun categoryNameWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val widest = with(LocalDensity.current) {
        Category.entries.maxOf { measurer.measure(it.name, style, maxLines = 1, softWrap = false).size.width }.toDp()
    }
    return maxOf(88.dp, widest + 4.dp)
}

@Composable
private fun ColorRow(category: Category, nameWidth: Dp, custom: Boolean, onIntent: (SettingsIntent) -> Unit) {
    val current = category.color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(category.name, Modifier.width(nameWidth), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryPalette.forEach { swatch ->
                val chosen = swatch == current
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(if (chosen) 3.dp else 0.dp, if (chosen) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                        .clickable(onClickLabel = "Use this colour for ${category.name}") {
                            onIntent(SettingsIntent.CategoryColorPicked(category, swatch.toArgb().toLong() and 0xFFFFFFFFL))
                        },
                )
            }
        }
        if (custom) TextButton(onClick = { onIntent(SettingsIntent.CategoryColorPicked(category, null)) }, colors = accentTextButtonColors()) { Text("Reset") }
    }
}

private fun tone(tone: BuddyTone): String = when (tone) {
    BuddyTone.Gentle -> "Soft words, and only long blocks get a check-in."
    BuddyTone.Normal -> "Friendly, with a check-in halfway through blocks of an hour or more."
    BuddyTone.Pushy -> "Loud and proud: sooner check-ins, twice on long blocks, two follow-ups."
}

private val kotlinx.datetime.LocalTime.minutes: Int get() = hour * 60 + minute

