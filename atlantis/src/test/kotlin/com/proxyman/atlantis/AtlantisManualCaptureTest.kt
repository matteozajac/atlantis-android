package com.proxyman.atlantis

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AtlantisManualCaptureTest {

    private val request = Request.fromOkHttp(
        url = "https://example.com/functions/joinWedding",
        method = "POST",
        headers = mapOf("Content-Type" to "application/json"),
        body = """{"weddingCode":"ABC123"}""".toByteArray()
    )

    private val response = Response.fromOkHttp(
        statusCode = 200,
        headers = mapOf("Content-Type" to "application/json")
    )

    @After
    fun tearDown() {
        Atlantis.setDelegate(null)
        setAtlantisRunning(false)
        setPrivateField("configuration", null)
        setPrivateField("transporter", null)
    }

    @Test
    fun `test manual capture package has generated non-empty id`() {
        val capturedPackage = captureManualPackage()

        assertFalse(capturedPackage.id.isBlank())
        assertNotNull(UUID.fromString(capturedPackage.id))
    }

    @Test
    fun `test manual capture package uses HTTP package type`() {
        val capturedPackage = captureManualPackage()

        assertEquals(TrafficPackage.PackageType.HTTP, capturedPackage.packageType)
    }

    @Test
    fun `test manual capture populates response and uses same start and end time`() {
        val capturedPackage = captureManualPackage()

        assertEquals(response, capturedPackage.response)
        assertNotNull(capturedPackage.endAt)
        assertEquals(capturedPackage.startAt, capturedPackage.endAt!!, 0.0)
    }

    @Test
    fun `test manual capture Base64 encodes non-null response body`() {
        val responseBody = """{"success":true}""".toByteArray()
        val capturedPackage = captureManualPackage(responseBody)

        assertEquals(Base64Utils.encode(responseBody), capturedPackage.responseBodyData)
    }

    @Test
    fun `test manual capture uses empty response body data when response body is null`() {
        val capturedPackage = captureManualPackage(responseBody = null)

        assertEquals("", capturedPackage.responseBodyData)
    }

    @Test
    fun `test manual capture replaces oversized response body with sentinel payload`() {
        val oversizedBody = ByteArray(CaptureBodyPolicy.MAX_BODY_SIZE_BYTES.toInt() + 1) { 1 }
        val capturedPackage = captureManualPackage(oversizedBody)

        assertEquals(
            Base64Utils.encode(CaptureBodyPolicy.oversizedResponseBodyBytes()),
            capturedPackage.responseBodyData
        )
    }

    @Test
    fun `test manual capture while Atlantis is stopped does not throw`() {
        setAtlantisRunning(false)

        Atlantis.add(request = request, response = response, responseBody = """{"success":true}""".toByteArray())
    }

    private fun captureManualPackage(responseBody: ByteArray? = null): TrafficPackage {
        val capturedPackage = AtomicReference<TrafficPackage?>()
        Atlantis.setDelegate(object : AtlantisDelegate {
            override fun onTrafficCaptured(trafficPackage: TrafficPackage) {
                capturedPackage.set(trafficPackage)
            }
        })
        setAtlantisRunning(true)
        setPrivateField("configuration", null)
        setPrivateField("transporter", null)

        Atlantis.add(request = request, response = response, responseBody = responseBody)

        return capturedPackage.get() ?: error("Expected Atlantis.add() to capture one package")
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
}
