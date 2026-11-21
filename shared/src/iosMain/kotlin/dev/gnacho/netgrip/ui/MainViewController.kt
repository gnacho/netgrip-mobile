package dev.gnacho.netgrip.ui

import androidx.compose.ui.window.ComposeUIViewController
import dev.gnacho.netgrip.core.storage.IosSecureStorage
import platform.UIKit.UIViewController

/** Punto de entrada del framework compartido para la futura app iOS. */
fun mainViewController(): UIViewController = ComposeUIViewController { App(IosSecureStorage()) }
