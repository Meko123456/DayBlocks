package io.github.meko123456.dayblocks.core.designsystem.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The shape every DayBlocks screen shares.
 *
 * - [state] is the one immutable description of the screen. The UI renders it and nothing else.
 * - An intent — something the user did — enters through [onIntent], and only there.
 * - State changes only through [reduce], so there is exactly one path by which the screen can
 *   change, and a test asserts on it by sending intents and reading states.
 * - [effects] carry one-off events that are not state: navigate, show a toast. They are a
 *   [Channel], not a StateFlow, because an effect must be delivered once. A navigation stored in
 *   state would fire again after rotation, or never if two arrived before the UI collected.
 *
 * Kept in :core:designsystem rather than :core:common because it depends on AndroidX Lifecycle,
 * and :core:domain depends on :core:common — putting a framework base class there would leak it
 * into the one layer that must stay pure. *
 * [scope] is a constructor parameter so tests can own it. Production passes nothing and gets what
 * `viewModelScope` would have been — Main-immediate plus a SupervisorJob, closed when the
 * ViewModel is cleared. A test passes `runTest`'s `backgroundScope`, which is cancelled when the
 * test ends and never holds it open: that is what makes a screen with an endless clock ticker
 * testable at all, since `advanceUntilIdle` on an infinite loop never returns.
 */
abstract class MviViewModel<S : Any, I : Any, E : Any>(
    initial: S,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val effectChannel = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    /** The single entry point for everything the user does. */
    fun onIntent(intent: I) {
        viewModelScope.launch { handle(intent) }
    }

    protected abstract suspend fun handle(intent: I)

    /** The single path by which state changes. Atomic, so concurrent reducers cannot lose an update. */
    protected fun reduce(transform: S.() -> S) {
        mutableState.update(transform)
    }

    protected suspend fun emit(effect: E) {
        effectChannel.send(effect)
    }

    /** For work that reacts to data rather than to an intent — observing a repository, a clock. */
    protected fun launchInScope(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
