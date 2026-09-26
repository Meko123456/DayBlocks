package io.github.meko123456.dayblocks.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.meko123456.dayblocks.core.common.formatClock
import io.github.meko123456.dayblocks.core.designsystem.CategoryPalette
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

@Composable
internal fun SettingsContent(state: SettingsState, snackbar: SnackbarHostState, onIntent: (SettingsIntent) -> Unit) {
    val is24Hour = rememberIs24HourFormat()
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onIntent(SettingsIntent.BackTapped) }) { Text("‹ Back") }
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
                    )
                }
                Text("Tone", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Category.entries.forEach { category -> ColorRow(category, custom = category in state.app.categoryColors, onIntent) }
            }

            Section("Your data") {
                Text(
                    "Everything stays on this phone. A backup is one file with every block, template and check-in in it — to keep, or to move to a new phone.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            confirmButton = { TextButton(onClick = { onIntent(SettingsIntent.ImportConfirmed) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { onIntent(SettingsIntent.ImportDismissed) }) { Text("Cancel") } },
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

@Composable
private fun Stepper(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        val tint = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
        FilledTonalIconButton(onClick = onLess, colors = tint, modifier = Modifier.semantics { contentDescription = "Less: $label" }) { Text("−") }
        Text(value, Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold)
        FilledTonalIconButton(onClick = onMore, colors = tint, modifier = Modifier.semantics { contentDescription = "More: $label" }) { Text("+") }
    }
}

@Composable
private fun ColorRow(category: Category, custom: Boolean, onIntent: (SettingsIntent) -> Unit) {
    val current = category.color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(category.name, Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium)
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
        if (custom) TextButton(onClick = { onIntent(SettingsIntent.CategoryColorPicked(category, null)) }) { Text("Reset") }
    }
}

private fun tone(tone: BuddyTone): String = when (tone) {
    BuddyTone.Gentle -> "Soft words, and only long blocks get a check-in."
    BuddyTone.Normal -> "Friendly, with a check-in halfway through blocks of an hour or more."
    BuddyTone.Pushy -> "Loud and proud: sooner check-ins, twice on long blocks, two follow-ups."
}

private val kotlinx.datetime.LocalTime.minutes: Int get() = hour * 60 + minute

