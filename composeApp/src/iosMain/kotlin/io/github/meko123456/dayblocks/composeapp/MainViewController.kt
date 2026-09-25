package io.github.meko123456.dayblocks.composeapp

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The entry point iosApp's SwiftUI layer wraps. Everything below it is shared Kotlin. */
fun mainViewController(): UIViewController = ComposeUIViewController { App() }
