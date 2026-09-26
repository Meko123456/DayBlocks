package io.github.meko123456.dayblocks.core.designsystem.buddy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood

/**
 * The buddy and what it is saying: its face, and a speech bubble whose corner points back at it.
 * Tapping the face is how it is renamed.
 */
@Composable
fun BuddySays(name: String, mood: BuddyMood, line: String, onFaceTapped: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BuddyFace(
            mood,
            Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = "Rename $name", onClick = onFaceTapped)
                .semantics { contentDescription = "$name, ${mood.spoken}" },
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** How a screen reader says the mood. */
val BuddyMood.spoken: String
    get() = when (this) {
        BuddyMood.Happy -> "happy"
        BuddyMood.Proud -> "proud"
        BuddyMood.Encouraging -> "encouraging"
        BuddyMood.Worried -> "worried"
        BuddyMood.Disappointed -> "a little disappointed"
        BuddyMood.Sleepy -> "sleepy"
    }
