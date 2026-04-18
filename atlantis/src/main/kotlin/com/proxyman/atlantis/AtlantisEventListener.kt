package com.proxyman.atlantis

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
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
        CallTimingStore.registerCallStart(call)
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

internal class CompositeAtlantisEventListener(
    private val atlantisListener: EventListener,
    private val delegateListener: EventListener
) : EventListener() {

    override fun callStart(call: Call) {
        atlantisListener.callStart(call)
        delegateListener.callStart(call)
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        atlantisListener.proxySelectStart(call, url)
        delegateListener.proxySelectStart(call, url)
    }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
        atlantisListener.proxySelectEnd(call, url, proxies)
        delegateListener.proxySelectEnd(call, url, proxies)
    }

    override fun dnsStart(call: Call, domainName: String) {
        atlantisListener.dnsStart(call, domainName)
        delegateListener.dnsStart(call, domainName)
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        atlantisListener.dnsEnd(call, domainName, inetAddressList)
        delegateListener.dnsEnd(call, domainName, inetAddressList)
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        atlantisListener.connectStart(call, inetSocketAddress, proxy)
        delegateListener.connectStart(call, inetSocketAddress, proxy)
    }

    override fun secureConnectStart(call: Call) {
        atlantisListener.secureConnectStart(call)
        delegateListener.secureConnectStart(call)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        atlantisListener.secureConnectEnd(call, handshake)
        delegateListener.secureConnectEnd(call, handshake)
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        atlantisListener.connectEnd(call, inetSocketAddress, proxy, protocol)
        delegateListener.connectEnd(call, inetSocketAddress, proxy, protocol)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        atlantisListener.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
        delegateListener.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
    }

    override fun connectionAcquired(call: Call, connection: okhttp3.Connection) {
        atlantisListener.connectionAcquired(call, connection)
        delegateListener.connectionAcquired(call, connection)
    }

    override fun connectionReleased(call: Call, connection: okhttp3.Connection) {
        atlantisListener.connectionReleased(call, connection)
        delegateListener.connectionReleased(call, connection)
    }

    override fun requestHeadersStart(call: Call) {
        atlantisListener.requestHeadersStart(call)
        delegateListener.requestHeadersStart(call)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        atlantisListener.requestHeadersEnd(call, request)
        delegateListener.requestHeadersEnd(call, request)
    }

    override fun requestBodyStart(call: Call) {
        atlantisListener.requestBodyStart(call)
        delegateListener.requestBodyStart(call)
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        atlantisListener.requestBodyEnd(call, byteCount)
        delegateListener.requestBodyEnd(call, byteCount)
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        atlantisListener.requestFailed(call, ioe)
        delegateListener.requestFailed(call, ioe)
    }

    override fun responseHeadersStart(call: Call) {
        atlantisListener.responseHeadersStart(call)
        delegateListener.responseHeadersStart(call)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        atlantisListener.responseHeadersEnd(call, response)
        delegateListener.responseHeadersEnd(call, response)
    }

    override fun responseBodyStart(call: Call) {
        atlantisListener.responseBodyStart(call)
        delegateListener.responseBodyStart(call)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        atlantisListener.responseBodyEnd(call, byteCount)
        delegateListener.responseBodyEnd(call, byteCount)
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        atlantisListener.responseFailed(call, ioe)
        delegateListener.responseFailed(call, ioe)
    }

    override fun callEnd(call: Call) {
        atlantisListener.callEnd(call)
        delegateListener.callEnd(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        atlantisListener.callFailed(call, ioe)
        delegateListener.callFailed(call, ioe)
    }

    override fun canceled(call: Call) {
        atlantisListener.canceled(call)
        delegateListener.canceled(call)
    }

    override fun satisfactionFailure(call: Call, response: Response) {
        atlantisListener.satisfactionFailure(call, response)
        delegateListener.satisfactionFailure(call, response)
    }

    override fun cacheHit(call: Call, response: Response) {
        atlantisListener.cacheHit(call, response)
        delegateListener.cacheHit(call, response)
    }

    override fun cacheMiss(call: Call) {
        atlantisListener.cacheMiss(call)
        delegateListener.cacheMiss(call)
    }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response) {
        atlantisListener.cacheConditionalHit(call, cachedResponse)
        delegateListener.cacheConditionalHit(call, cachedResponse)
    }
}
