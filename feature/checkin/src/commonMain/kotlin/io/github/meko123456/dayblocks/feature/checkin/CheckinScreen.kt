package io.github.meko123456.dayblocks.feature.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.meko123456.dayblocks.core.common.formatClock
import io.github.meko123456.dayblocks.core.designsystem.buddy.BuddySays
import io.github.meko123456.dayblocks.core.designsystem.color
import io.github.meko123456.dayblocks.core.designsystem.time.rememberIs24HourFormat
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CheckinScreen(args: CheckinArgs, onClose: () -> Unit, viewModel: CheckinViewModel = koinViewModel { parametersOf(args) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CheckinEffect.Close -> onClose()
            }
        }
    }
    CheckinContent(state, viewModel::onIntent)
}

@Composable
internal fun CheckinContent(state: CheckinState, onIntent: (CheckinIntent) -> Unit) {
    val is24Hour = rememberIs24HourFormat()
    Scaffold { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onIntent(CheckinIntent.BackTapped) }) { Text("‹ Back") }
                Column {
                    Text(
                        if (state.isToday) "How did today go?" else "How did ${state.date?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() }} go?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    state.date?.let { date ->
                        Text(
                            "${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${date.day} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            BuddySays(name = state.buddy.name, mood = state.buddy.mood, line = state.buddy.line)

            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = surfaceCard(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.score?.let { "$it%" } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (state.score == null) "Nothing rated yet" else "of the day's plan followed",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            state.rows.forEach { row -> BlockRow(row, is24Hour, onIntent) }

            if (state.hasSuggestions) {
                Button(onClick = { onIntent(CheckinIntent.ConfirmSuggestions) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("Looks right")
                }
            }
        }
    }
}

@Composable
private fun BlockRow(row: CheckinRow, is24Hour: Boolean, onIntent: (CheckinIntent) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = surfaceCard(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(row.block.category.color))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(row.block.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatClock(row.block.span.startMinutes, is24Hour)} – ${formatClock(row.block.span.endMinutes, is24Hour)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockOutcome.entries.forEach { outcome ->
                    FilterChip(
                        selected = row.shown == outcome,
                        onClick = { onIntent(CheckinIntent.OutcomePicked(row.block.id, outcome)) },
                        label = { Text(outcome.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
            if (row.outcome == null && row.suggested != null) {
                Text(
                    "From your answer during the day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private val BlockOutcome.label: String
    get() = when (this) {
        BlockOutcome.Done -> "Done"
        BlockOutcome.Partly -> "Partly"
        BlockOutcome.Skipped -> "Skipped"
    }

/** The warm white of Today's cards, rather than Material's default tint. */
@Composable
private fun surfaceCard() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
