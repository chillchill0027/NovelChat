package com.novelchat.ui.viewmodel

import kotlinx.coroutines.CancellationException

internal fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}
