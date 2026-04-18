package com.proxyman.atlantis

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AtlantisCallTimingTest {

    @After
    fun tearDown() {
        Atlantis.setDelegate(null)
        setAtlantisRunning(false)
        setPrivateField("configuration", null)
        setPrivateField("transporter", null)
        CallTimingStore.clear()
    }

    @Test
    fun `test event listener callStart registers metadata`() {
        setAtlantisRunning(true)

        val call = newCall("https://example.com/register")
        val listener = Atlantis.getEventListenerFactory().create(call)

        listener.callStart(call)

        val timing = CallTimingStore.get(call)
        assertNotNull(timing)
        assertFalse(timing!!.requestId.isBlank())
        assertTrue(timing.startAt > 0.0)
    }

    @Test
    fun `test event listener callStart registers metadata even when Atlantis is not running`() {
        setAtlantisRunning(false)

        val call = newCall("https://example.com/not-running")
        val listener = Atlantis.getEventListenerFactory().create(call)

        listener.callStart(call)

        val timing = CallTimingStore.get(call)
        assertNotNull(timing)
        assertFalse(timing!!.requestId.isBlank())
        assertTrue(timing.startAt > 0.0)
    }

    @Test
    fun `test event listener cleanup removes metadata on callEnd callFailed and canceled`() {
        setAtlantisRunning(true)

        val endCall = newCall("https://example.com/end")
        val endListener = Atlantis.getEventListenerFactory().create(endCall)
        endListener.callStart(endCall)
        endListener.callEnd(endCall)
        assertNull(CallTimingStore.get(endCall))

        val failedCall = newCall("https://example.com/failed")
        val failedListener = Atlantis.getEventListenerFactory().create(failedCall)
        failedListener.callStart(failedCall)
        failedListener.callFailed(failedCall, IOException("boom"))
        assertNull(CallTimingStore.get(failedCall))

        val canceledCall = newCall("https://example.com/canceled")
        val canceledListener = Atlantis.getEventListenerFactory().create(canceledCall)
        canceledListener.callStart(canceledCall)
        canceledListener.canceled(canceledCall)
        assertNull(CallTimingStore.get(canceledCall))
    }

    @Test
    fun `test interceptor reuses tracked id and startAt when event listener metadata exists`() {
        val request = Request.Builder().url("https://example.com/success").build()
        val call = newCall(request.url.toString())
        val listener = Atlantis.getEventListenerFactory().create(call)
        setAtlantisRunning(true)

        listener.callStart(call)
        val trackedTiming = CallTimingStore.get(call)!!
        val capturedPackage = captureInterceptedPackage(
            interceptor = AtlantisInterceptor(),
            chain = StubChain(
                request = request,
                call = call,
                response = successResponse(request)
            )
        )

        assertEquals(trackedTiming.requestId, capturedPackage.id)
        assertEquals(trackedTiming.startAt, capturedPackage.startAt, 0.0)
        assertNull(CallTimingStore.get(call))
    }

    @Test
    fun `test stop does not clear in flight call timing metadata`() {
        setAtlantisRunning(true)

        val call = newCall("https://example.com/in-flight")
        val listener = Atlantis.getEventListenerFactory().create(call)
        listener.callStart(call)
        val trackedTiming = CallTimingStore.get(call)

        setAtlantisRunning(false)

        assertEquals(trackedTiming, CallTimingStore.get(call))
    }

    @Test
    fun `test interceptor reuses tracked id and startAt across stop and restart`() {
        val request = Request.Builder().url("https://example.com/restart").build()
        val call = newCall(request.url.toString())
        val listener = Atlantis.getEventListenerFactory().create(call)
        setAtlantisRunning(true)

        listener.callStart(call)
        val trackedTiming = CallTimingStore.get(call)!!

        setAtlantisRunning(false)
        setAtlantisRunning(true)

        val capturedPackage = captureInterceptedPackage(
            interceptor = AtlantisInterceptor(),
            chain = StubChain(
                request = request,
                call = call,
                response = successResponse(request)
            )
        )

        assertEquals(trackedTiming.requestId, capturedPackage.id)
        assertEquals(trackedTiming.startAt, capturedPackage.startAt, 0.0)
        assertNull(CallTimingStore.get(call))
    }

    @Test
    fun `test interceptor falls back to interceptor entry timing when no event listener metadata exists`() {
        setAtlantisRunning(true)

        val request = Request.Builder().url("https://example.com/fallback").build()
        val call = newCall(request.url.toString())
        val before = System.currentTimeMillis() / 1000.0
        val capturedPackage = captureInterceptedPackage(
            interceptor = AtlantisInterceptor(),
            chain = StubChain(
                request = request,
                call = call,
                response = successResponse(request)
            )
        )
        val after = System.currentTimeMillis() / 1000.0

        assertFalse(capturedPackage.id.isBlank())
        assertTrue(capturedPackage.startAt in before..after)
        assertNull(CallTimingStore.get(call))
    }

    @Test
    fun `test interceptor error path reuses tracked id and startAt`() {
        val request = Request.Builder().url("https://example.com/error").build()
        val call = newCall(request.url.toString())
        val listener = Atlantis.getEventListenerFactory().create(call)
        setAtlantisRunning(true)

        listener.callStart(call)
        val trackedTiming = CallTimingStore.get(call)!!
        val capturedPackage = AtomicReference<TrafficPackage?>()
        val delegate = object : AtlantisDelegate {
            override fun onTrafficCaptured(trafficPackage: TrafficPackage) {
                capturedPackage.set(trafficPackage)
            }
        }
        Atlantis.setDelegate(delegate)

        try {
            AtlantisInterceptor().intercept(
                StubChain(
                    request = request,
                    call = call,
                    failure = IOException("network down")
                )
            )
        } catch (expected: IOException) {
            // Expected path.
        }

        val trafficPackage = capturedPackage.get() ?: error("Expected one captured error package")
        assertEquals(trackedTiming.requestId, trafficPackage.id)
        assertEquals(trackedTiming.startAt, trafficPackage.startAt, 0.0)
        assertNotNull(trafficPackage.error)
        assertNull(CallTimingStore.get(call))
    }

    @Test
    fun `test wrapped event listener factory composes with existing listener`() {
        val call = newCall("https://example.com/wrapped")
        val recordedEvents = mutableListOf<String>()
        val delegateFactory = EventListener.Factory {
            RecordingEventListener(recordedEvents)
        }

        val listener = Atlantis.getEventListenerFactory(delegateFactory).create(call)
        listener.callStart(call)
        listener.callEnd(call)

        val expectedEvents = listOf(
            "delegate-callStart",
            "delegate-callEnd"
        )
        assertEquals(expectedEvents, recordedEvents)
    }

    @Test
    fun `test wrapped event listener factory forwards lifecycle callbacks to delegate listener`() {
        val call = newCall("https://example.com/forwarding")
        val recordedEvents = mutableListOf<String>()
        val delegateFactory = EventListener.Factory {
            RecordingEventListener(recordedEvents)
        }

        val listener = Atlantis.getEventListenerFactory(delegateFactory).create(call)
        listener.callStart(call)
        listener.callFailed(call, IOException("boom"))
        listener.canceled(call)

        assertEquals(
            listOf("delegate-callStart", "delegate-callFailed", "delegate-canceled"),
            recordedEvents
        )
        assertNull(CallTimingStore.get(call))
    }

    private fun captureInterceptedPackage(
        interceptor: AtlantisInterceptor,
        chain: Interceptor.Chain
    ): TrafficPackage {
        val capturedPackage = AtomicReference<TrafficPackage?>()
        val delegate = object : AtlantisDelegate {
            override fun onTrafficCaptured(trafficPackage: TrafficPackage) {
                capturedPackage.set(trafficPackage)
            }
        }
        Atlantis.setDelegate(delegate)

        interceptor.intercept(chain)

        return capturedPackage.get() ?: error("Expected one captured package")
    }

    private fun successResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"ok":true}""".toResponseBody())
            .build()
    }

    private fun newCall(url: String): Call {
        return OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
            .newCall(Request.Builder().url(url).build())
    }

    private fun setAtlantisRunning(enabled: Boolean) {
        val field = Atlantis::class.java.getDeclaredField("isEnabled")
        field.isAccessible = true
        val isEnabled = field.get(Atlantis) as AtomicBoolean
        isEnabled.set(enabled)
    }

    private fun setPrivateField(fieldName: String, value: Any?) {
        val field = Atlantis::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(Atlantis, value)
    }

    private class StubChain(
        private val request: Request,
        private val call: Call,
        private val response: Response? = null,
        private val failure: IOException? = null
    ) : Interceptor.Chain {
        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            failure?.let { throw it }
            return response ?: error("StubChain requires either response or failure")
        }

        override fun connection(): Connection? = null

        override fun call(): Call = call

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private class RecordingEventListener(
        private val recordedEvents: MutableList<String>
    ) : EventListener() {
        override fun callStart(call: Call) {
            recordedEvents.add("delegate-callStart")
        }

        override fun callEnd(call: Call) {
            recordedEvents.add("delegate-callEnd")
        }

        override fun callFailed(call: Call, ioe: IOException) {
            recordedEvents.add("delegate-callFailed")
        }

        override fun canceled(call: Call) {
            recordedEvents.add("delegate-canceled")
        }
    }
}
