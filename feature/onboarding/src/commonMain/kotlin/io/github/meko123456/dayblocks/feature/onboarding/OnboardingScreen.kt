package io.github.meko123456.dayblocks.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.meko123456.dayblocks.core.designsystem.buddy.BuddyFace
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import org.koin.compose.viewmodel.koinViewModel

/**
 * [onRequestReminders] is the platform's permission prompt, which the app shell owns; this screen
 * decides only when to ask — after explaining what the notifications are for.
 */
@Composable
fun OnboardingScreen(onRequestReminders: () -> Unit, onFinished: () -> Unit, viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.RequestReminders -> onRequestReminders()
                OnboardingEffect.Finished -> onFinished()
            }
        }
    }
    OnboardingContent(state, viewModel::onIntent)
}

@Composable
internal fun OnboardingContent(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            BuddyFace(
                when (state.page) {
                    OnboardingPage.Meet -> BuddyMood.Happy
                    OnboardingPage.Tone -> BuddyMood.Encouraging
                    OnboardingPage.Reminders -> BuddyMood.Happy
                    OnboardingPage.FirstDay -> BuddyMood.Proud
                },
                size = 120.dp,
            )
            when (state.page) {
                OnboardingPage.Meet -> Meet(state, onIntent)
                OnboardingPage.Tone -> Tone(state, onIntent)
                OnboardingPage.Reminders -> Reminders(state, onIntent)
                OnboardingPage.FirstDay -> FirstDay(state, onIntent)
            }
            if (state.page != OnboardingPage.Meet) {
                TextButton(onClick = { onIntent(OnboardingIntent.BackTapped) }) { Text("‹ Back") }
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

@Composable
private fun Meet(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    Title("Hi! I'm ${state.chosenName}.")
    Body("I'll help you plan your day in blocks, then stick to it. I'll say when a block starts, check in halfway, and cheer you on.")
    OutlinedTextField(
        value = state.name,
        onValueChange = { onIntent(OnboardingIntent.NameChanged(it)) },
        label = { Text("What should I be called?") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onIntent(OnboardingIntent.NextTapped) }, modifier = Modifier.fillMaxWidth()) { Text("Nice to meet you") }
}

@Composable
private fun Tone(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    Title("How should I talk to you?")
    BuddyTone.entries.forEach { tone ->
        Card(
            onClick = { onIntent(OnboardingIntent.TonePicked(tone)) },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.tone == tone) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.tone == tone, onClick = { onIntent(OnboardingIntent.TonePicked(tone)) })
                Column {
                    Text(tone.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("“${state.samples[tone].orEmpty()}”", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Button(onClick = { onIntent(OnboardingIntent.NextTapped) }, modifier = Modifier.fillMaxWidth()) { Text("That's the one") }
}

@Composable
private fun Reminders(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    Title("Can I tap you on the shoulder?")
    Body(
        "I'll send a notification when a block starts, a check-in halfway through the long ones, and a nudge " +
            "if you got distracted. Quiet at night, never more than a handful a day, and nothing leaves your phone.",
    )
    Button(onClick = { onIntent(OnboardingIntent.AllowRemindersTapped) }, modifier = Modifier.fillMaxWidth()) { Text("Allow notifications") }
    OutlinedButton(onClick = { onIntent(OnboardingIntent.NotNowTapped) }, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
}

@Composable
private fun FirstDay(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    Title("Let's plan today.")
    Body("Start from an example day and change what doesn't fit — it's saved as your first template too. Or begin with a blank page.")
    Button(onClick = { onIntent(OnboardingIntent.ExampleDayTapped) }, modifier = Modifier.fillMaxWidth()) { Text("Start from an example day") }
    OutlinedButton(onClick = { onIntent(OnboardingIntent.OwnPlanTapped) }, modifier = Modifier.fillMaxWidth()) { Text("I'll plan my own") }
}
