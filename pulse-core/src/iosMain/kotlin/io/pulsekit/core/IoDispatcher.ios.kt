package io.pulsekit.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Kotlin/Native has no Dispatchers.IO; PulseKit's I/O is light (in-memory + small
// files), so Default is an appropriate backing dispatcher on iOS.
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
