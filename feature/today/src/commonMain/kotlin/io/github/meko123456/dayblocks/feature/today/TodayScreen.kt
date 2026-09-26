package io.github.meko123456.dayblocks.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.meko123456.dayblocks.core.common.formatClock
import io.github.meko123456.dayblocks.core.common.formatDuration
import io.github.meko123456.dayblocks.core.designsystem.buddy.BuddySays
import io.github.meko123456.dayblocks.core.designsystem.color
import io.github.meko123456.dayblocks.core.designsystem.time.rememberIs24HourFormat
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/**
 * Today. The ViewModel owns every decision; this only draws [TodayState] and turns taps into
 * [TodayIntent]s. Navigation arrives as [TodayEffect]s and is handed to the caller, because only
 * :composeApp knows where "the editor" is.
 */
@Composable
fun TodayScreen(
    onOpenEditor: (date: LocalDate, blockId: BlockId?, prefill: DaySpan?) -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    notice: @Composable () -> Unit = {},
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TodayEffect.OpenEditor -> onOpenEditor(effect.date, effect.blockId, effect.prefill)
                TodayEffect.OpenCheckIn -> onOpenCheckIn()
                TodayEffect.OpenTemplates -> onOpenTemplates()
                TodayEffect.OpenStats -> onOpenStats()
                TodayEffect.OpenSettings -> onOpenSettings()
            }
        }
    }

    TodayContent(state = state, onIntent = viewModel::onIntent, notice = notice)
    RenameDialog(state, viewModel::onIntent)
}

@Composable
internal fun TodayContent(state: TodayState, onIntent: (TodayIntent) -> Unit, notice: @Composable () -> Unit = {}) {
    val is24Hour = rememberIs24HourFormat()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onIntent(TodayIntent.AddBlockTapped) }) {
                Text("+  Add block")
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val scroll = rememberScrollState()
        val hourHeight = 72.dp
        val pxPerMinute = with(LocalDensity.current) { hourHeight.toPx() } / 60f

        // Open on the present, a little above it, rather than at 06:00 every time.
        LaunchedEffect(state.planDate) {
            state.nowMinute?.let { minute ->
                val offsetMinutes = (minute - state.window.startMinutes - 60).coerceAtLeast(0)
                scroll.scrollTo((offsetMinutes * pxPerMinute).toInt())
            }
        }

        // The header and the Now card stay put; only the timeline scrolls. The first version
        // scrolled everything to the present on open — which pushed the Now card, the one thing
        // this screen exists to show, straight off the top.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(state, onIntent)
            BuddySays(
                name = state.buddy.name,
                mood = state.buddy.mood,
                line = state.buddy.line,
                onFaceTapped = { onIntent(TodayIntent.BuddyTapped) },
            )
            // A slot the app shell fills — today, the "reminders are off" strip — so this screen
            // shows it without knowing anything about notifications.
            notice()
            NowCardView(state.now, is24Hour, modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Spacer(Modifier.height(8.dp))
                Timeline(state, is24Hour, hourHeight, onIntent, modifier = Modifier.padding(end = 16.dp))
                Spacer(Modifier.height(88.dp)) // clear of the FAB
            }
        }
    }
}

@Composable
private fun RenameDialog(state: TodayState, onIntent: (TodayIntent) -> Unit) {
    val draft = state.renaming ?: return
    // Opens focused with the old name selected, so typing a new one replaces it. The selection is
    // this dialog's business; the ViewModel only ever sees the text.
    var field by remember { mutableStateOf(TextFieldValue(draft, selection = TextRange(0, draft.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = { onIntent(TodayIntent.RenameDismissed) },
        title = { Text("Rename ${state.buddy.name}") },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = it
                    onIntent(TodayIntent.RenameChanged(it.text))
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onIntent(TodayIntent.RenameConfirmed) }, enabled = draft.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { onIntent(TodayIntent.RenameDismissed) }) { Text("Cancel") } },
    )
}

@Composable
private fun Header(state: TodayState, onIntent: (TodayIntent) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                state.planDate?.let { date ->
                    Text(
                        "${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${date.day} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            TextButton(onClick = { onIntent(TodayIntent.CheckInTapped) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Check-in") }
            TextButton(onClick = { onIntent(TodayIntent.TemplatesTapped) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Templates") }
            TextButton(onClick = { onIntent(TodayIntent.StatsTapped) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Stats") }
            TextButton(onClick = { onIntent(TodayIntent.SettingsTapped) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Settings") }
        }
    }
}

@Composable
private fun NowCardView(now: NowCard, is24Hour: Boolean, modifier: Modifier = Modifier) {
    val accent = now.current?.category?.color ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "NOW",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            val current = now.current
            if (current != null) {
                Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                now.minutesLeft?.let { left ->
                    Text("${formatDuration(left)} left", style = MaterialTheme.typography.titleMedium)
                    val total = current.span.durationMinutes.toFloat()
                    LinearProgressIndicator(
                        progress = { ((total - left) / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.18f),
                    )
                }
            } else {
                Text("Free right now", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val next = now.next
            Text(
                if (next != null) {
                    "Next · ${next.title} at ${formatClock(next.span.startMinutes, is24Hour)}" +
                        (now.minutesUntilNext?.let { " · in ${formatDuration(it)}" } ?: "")
                } else {
                    "Nothing else planned today"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

/** Wide enough for "10:00 AM" on one line; the 24-hour style needs less. */
private fun labelColumn(is24Hour: Boolean): Dp = if (is24Hour) 56.dp else 76.dp

@Composable
private fun Timeline(
    state: TodayState,
    is24Hour: Boolean,
    hourHeight: Dp,
    onIntent: (TodayIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = state.window
    val hours = window.durationMinutes / 60
    val labelWidth = labelColumn(is24Hour)
    val minuteHeight = hourHeight / 60
    fun yOf(minute: Int): Dp = minuteHeight * (minute - window.startMinutes)

    BoxWithConstraints(modifier.fillMaxWidth().height(hourHeight * hours)) {
        val gridColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
        // Hour grid and labels.
        for (h in 0..hours) {
            val minute = window.startMinutes + h * 60
            Row(Modifier.offset(y = yOf(minute) - 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatClock(minute, is24Hour),
                    modifier = Modifier.width(labelWidth).padding(start = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    maxLines = 1,
                    softWrap = false,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(gridColor))
            }
        }

        // Blocks and free time, clipped to the window: an overnight block that runs past the end
        // is drawn to the edge rather than stretching the timeline.
        for (item in state.timeline) {
            val start = maxOf(item.span.startMinutes, window.startMinutes)
            val end = minOf(item.span.endMinutes, window.endMinutes)
            if (end <= start) continue
            val itemModifier = Modifier
                .offset(x = labelWidth, y = yOf(start) + 1.dp)
                .width(maxWidth - labelWidth)
                .height((minuteHeight * (end - start)) - 2.dp)
            when (item) {
                is TimelineItem.Block -> BlockTile(item, is24Hour, itemModifier) { onIntent(TodayIntent.BlockTapped(item.block.id)) }
                is TimelineItem.FreeTime -> FreeTimeTile(item.span, itemModifier) { onIntent(TodayIntent.FreeTimeTapped(item.span)) }
            }
        }

        // The current-time line, drawn last so it sits over whatever block it crosses.
        state.nowMinute?.let { minute ->
            val lineColor = MaterialTheme.colorScheme.primary
            Row(
                Modifier.offset(x = labelWidth - 5.dp, y = yOf(minute) - 5.dp).width(maxWidth - labelWidth + 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(lineColor, CircleShape))
                Box(Modifier.weight(1f).height(2.dp).background(lineColor))
            }
        }
    }
}

@Composable
private fun BlockTile(item: TimelineItem.Block, is24Hour: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val block: TimeBlock = item.block
    val color = block.category.color
    val shape = RoundedCornerShape(12.dp)
    val current = item.status == BlockStatus.Current
    Box(
        modifier
            .alpha(if (item.status == BlockStatus.Past) 0.45f else 1f)
            .background(color.copy(alpha = if (current) 0.30f else 0.18f), shape)
            .then(if (current) Modifier.border(2.dp, color, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(4.dp).height(18.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    block.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (block.span.durationMinutes >= 45) {
                    Text(
                        "${formatClock(block.span.startMinutes, is24Hour)} – ${formatClock(block.span.endMinutes, is24Hour)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FreeTimeTile(span: DaySpan, modifier: Modifier, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    Box(
        modifier
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "Free time · ${formatDuration(span.durationMinutes)} · tap to plan",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            maxLines = 1,
        )
    }
}
