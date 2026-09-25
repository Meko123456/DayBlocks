package io.github.meko123456.dayblocks.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder while the skeleton is stood up. The real screen is an MVI triple — State, Intent,
 * Effect — with a ViewModel exposing StateFlow<State> and a Flow<Effect>; see the module README.
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Stats")
    }
}
