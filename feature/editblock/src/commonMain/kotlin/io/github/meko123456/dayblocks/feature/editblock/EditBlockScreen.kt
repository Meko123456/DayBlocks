package io.github.meko123456.dayblocks.feature.editblock

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.meko123456.dayblocks.core.designsystem.defaultColor
import io.github.meko123456.dayblocks.core.designsystem.time.formatClock
import io.github.meko123456.dayblocks.core.designsystem.time.formatDuration
import io.github.meko123456.dayblocks.core.designsystem.time.rememberIs24HourFormat
import io.github.meko123456.dayblocks.core.domain.model.Category
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EditBlockScreen(
    args: EditBlockArgs,
    onClose: () -> Unit,
    viewModel: EditBlockViewModel = koinViewModel(key = args.toString()) { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditBlockEffect.Close -> onClose()
            }
        }
    }
    EditBlockContent(state, viewModel::onIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditBlockContent(state: EditBlockState, onIntent: (EditBlockIntent) -> Unit) {
    val is24Hour = rememberIs24HourFormat()
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.isNew) "New block" else "Edit block",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onIntent(EditBlockIntent.CancelTapped) }) { Text("Cancel") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { onIntent(EditBlockIntent.TitleChanged(it)) },
                    label = { Text("What are you doing?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.suggestions.forEach { title ->
                            SuggestionChip(onClick = { onIntent(EditBlockIntent.SuggestionPicked(title)) }, label = { Text(title) })
                        }
                    }
                }
            }

            Section("Category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Category.entries.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { onIntent(EditBlockIntent.CategoryPicked(category)) },
                            label = { Text(category.name) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(category.defaultColor, CircleShape)) },
                        )
                    }
                }
            }

            Section("When") {
                TimeStepper("Starts", formatClock(state.span.startMinutes, is24Hour), { onIntent(EditBlockIntent.StartStepped(-1)) }, { onIntent(EditBlockIntent.StartStepped(1)) })
                TimeStepper(
                    "Ends",
                    formatClock(state.span.endMinutes, is24Hour) + if (state.span.crossesMidnight) "  (next day)" else "",
                    { onIntent(EditBlockIntent.EndStepped(-1)) },
                    { onIntent(EditBlockIntent.EndStepped(1)) },
                )
                Text(
                    formatDuration(state.span.durationMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            if (state.overlaps.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Overlaps with", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        state.overlaps.forEach { other ->
                            Text(
                                "${other.title} · ${formatClock(other.span.startMinutes, is24Hour)}–${formatClock(other.span.endMinutes, is24Hour)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            "You can still save it — sometimes two things really do happen at once.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = { onIntent(EditBlockIntent.NoteChanged(it)) },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!state.isNew) {
                    TextButton(onClick = { onIntent(EditBlockIntent.DeleteTapped) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onIntent(EditBlockIntent.SaveTapped) }, enabled = state.canSave) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun TimeStepper(label: String, value: String, onEarlier: () -> Unit, onLater: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = onEarlier) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            value,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        FilledTonalIconButton(onClick = onLater) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
