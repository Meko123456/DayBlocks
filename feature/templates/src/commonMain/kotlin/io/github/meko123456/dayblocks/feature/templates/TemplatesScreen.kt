package io.github.meko123456.dayblocks.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import kotlinx.datetime.DayOfWeek
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TemplatesScreen(onClose: () -> Unit, viewModel: TemplatesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TemplatesEffect.Message -> snackbar.showSnackbar(effect.text)
                TemplatesEffect.Close -> onClose()
            }
        }
    }
    TemplatesContent(state, snackbar, viewModel::onIntent)
}

@Composable
internal fun TemplatesContent(state: TemplatesState, snackbar: SnackbarHostState, onIntent: (TemplatesIntent) -> Unit) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onIntent(TemplatesIntent.BackTapped) }) { Text("‹ Back") }
                Text("Templates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today · ${plural(state.todayBlocks, "block")} planned", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { onIntent(TemplatesIntent.SaveTodayTapped) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save today as a template")
                    }
                    OutlinedButton(onClick = { onIntent(TemplatesIntent.CopyYesterdayTapped) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Copy yesterday (${plural(state.yesterdayBlocks, "block")})")
                    }
                }
            }

            Text("Your templates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.templates.isEmpty()) {
                Text(
                    "None yet. Plan a day you liked, then save it here — \"Weekday\", \"Weekend\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            state.templates.forEach { template ->
                val days = state.assignments.filterValues { it == template.id }.keys.sortedBy { it.ordinal }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            plural(template.blocks.size, "block") + if (days.isEmpty()) "" else " · fills " + days.joinToString { it.short() },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { onIntent(TemplatesIntent.ApplyTapped(template.id)) }) { Text("Apply to today") }
                            TextButton(onClick = { onIntent(TemplatesIntent.DeleteTapped(template.id)) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Text("Weekdays fill themselves", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "An empty day starts from its template, once. Clear it and it stays cleared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            DayOfWeek.entries.forEach { day ->
                WeekdayRow(day, state, onIntent)
            }
        }
    }

    if (state.naming) {
        AlertDialog(
            onDismissRequest = { onIntent(TemplatesIntent.NameDismissed) },
            title = { Text("Save today as") },
            text = {
                OutlinedTextField(
                    value = state.draftName,
                    onValueChange = { onIntent(TemplatesIntent.DraftNameChanged(it)) },
                    label = { Text("Template name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(TemplatesIntent.NameConfirmed) }, enabled = state.draftName.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { onIntent(TemplatesIntent.NameDismissed) }) { Text("Cancel") } },
        )
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = { onIntent(TemplatesIntent.ReplaceDismissed) },
            title = { Text("Replace today's plan?") },
            text = {
                Text(
                    when (pending) {
                        PendingReplace.CopyYesterday -> "Today's ${plural(state.todayBlocks, "block")} will be replaced by yesterday's plan."
                        is PendingReplace.ApplyTemplate -> "Today's ${plural(state.todayBlocks, "block")} will be replaced by “${pending.name}”."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { onIntent(TemplatesIntent.ReplaceConfirmed) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { onIntent(TemplatesIntent.ReplaceDismissed) }) { Text("Keep today") } },
        )
    }
}

@Composable
private fun WeekdayRow(day: DayOfWeek, state: TemplatesState, onIntent: (TemplatesIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val assigned: TemplateId? = state.assignments[day]
    val label = state.templates.firstOrNull { it.id == assigned }?.name ?: "Nothing"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(day.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text("$label ▾") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Nothing") }, onClick = { open = false; onIntent(TemplatesIntent.AssignPicked(day, null)) })
                state.templates.forEach { template ->
                    DropdownMenuItem(text = { Text(template.name) }, onClick = { open = false; onIntent(TemplatesIntent.AssignPicked(day, template.id)) })
                }
            }
        }
    }
}

private fun plural(n: Int, word: String): String = "$n $word" + if (n == 1) "" else "s"

private fun DayOfWeek.short(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
