package com.myvpn.app

object VpnState {
    @Volatile var running = false
    @Volatile var error: String? = null
    @Volatile var listener: (() -> Unit)? = null

    fun set(value: Boolean) {
        running = value
        listener?.invoke()
    }
}
