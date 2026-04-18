package com.proxyman.atlantis

import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class CallTimingMetadata(
    val requestId: String,
    val startAt: Double
)

internal object CallTimingStore {
    private val timings = ConcurrentHashMap<Call, CallTimingMetadata>()

    fun registerCallStart(call: Call): CallTimingMetadata {
        val timing = CallTimingMetadata(
            requestId = UUID.randomUUID().toString(),
            startAt = now()
        )
        timings.putIfAbsent(call, timing)
        return timings[call] ?: timing
    }

    fun get(call: Call): CallTimingMetadata? {
        return timings[call]
    }

    fun resolveOrCreate(call: Call): CallTimingMetadata {
        return timings[call] ?: CallTimingMetadata(
            requestId = UUID.randomUUID().toString(),
            startAt = now()
        )
    }

    fun remove(call: Call) {
        timings.remove(call)
    }

    fun clear() {
        timings.clear()
    }

    private fun now(): Double {
        return System.currentTimeMillis() / 1000.0
    }
}

internal class AtlantisEventListener : EventListener() {
    override fun callStart(call: Call) {
        if (Atlantis.isRunning()) {
            CallTimingStore.registerCallStart(call)
        }
    }

    override fun callEnd(call: Call) {
        CallTimingStore.remove(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        CallTimingStore.remove(call)
    }

    override fun canceled(call: Call) {
        CallTimingStore.remove(call)
    }
}
