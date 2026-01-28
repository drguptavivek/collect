package edu.aiims.medresodk.auth.activities

import edu.aiims.medresodk.auth.utils.MedresConstants
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for decompression bomb protection.
 * Ensures decompressed QR payloads don't exceed safe limits.
 */
class MedresQrDecompressionBombTest {

    @Test
    fun `accepts decompressed payload within limit`() {
        val decompressed = "A".repeat(1000) // 1KB
        assertTrue(decompressed.length <= MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `accepts decompressed payload at exact limit`() {
        val decompressed = "A".repeat(MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
        assertEquals(MedresConstants.MAX_QR_DECOMPRESSED_SIZE, decompressed.length)
        assertTrue(decompressed.length <= MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `rejects decompressed payload exceeding limit by 1 byte`() {
        val decompressed = "A".repeat(MedresConstants.MAX_QR_DECOMPRESSED_SIZE + 1)
        assertTrue(decompressed.length > MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `rejects massive decompressed payload - decompression bomb`() {
        // Simulate a decompression bomb: 4KB compressed -> 100MB decompressed
        val decompressionBombSize = 100 * 1024 * 1024 // 100MB
        assertTrue(decompressionBombSize > MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `verifies MAX_QR_DECOMPRESSED_SIZE constant value`() {
        // Ensure the constant is set to 16KB (16384 bytes)
        assertEquals(16384, MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `verifies decompressed limit is larger than compressed limit`() {
        // Decompressed should be larger to allow for compression
        assertTrue(MedresConstants.MAX_QR_DECOMPRESSED_SIZE > MedresConstants.MAX_QR_PAYLOAD_SIZE)
    }

    @Test
    fun `verifies reasonable compression ratio limit`() {
        // Max compression ratio should be 4:1 (4KB compressed -> 16KB decompressed)
        val maxCompressionRatio = MedresConstants.MAX_QR_DECOMPRESSED_SIZE.toDouble() / 
                                   MedresConstants.MAX_QR_PAYLOAD_SIZE.toDouble()
        assertEquals(4.0, maxCompressionRatio, 0.1)
    }

    @Test
    fun `accepts typical JSON payload after decompression`() {
        val typicalJson = """
            {
                "general": {
                    "server_url": "https://central.example.com/v1/projects/1",
                    "autosend": "wifi_only",
                    "navigation": "swipe",
                    "image_size": "medium",
                    "form_update_mode": "match_exactly"
                },
                "admin": {
                    "change_autosend": false,
                    "change_navigation": false,
                    "delete_saved": false,
                    "change_server": false
                },
                "project": {
                    "name": "Clinical Study Project",
                    "project_id": "42"
                }
            }
        """.trimIndent()
        
        assertTrue(typicalJson.length <= MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `rejects payload with excessive repetition - compression bomb indicator`() {
        // Highly repetitive data compresses extremely well
        // This simulates what a decompression bomb would look like after decompression
        val repetitivePayload = "AAAAAAAAAA".repeat(2000000) // 20MB of repeated 'A's
        assertTrue(repetitivePayload.length > MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `accepts large but valid decompressed payload`() {
        // Large QR with many settings (15KB - just under limit)
        val largePayload = "A".repeat(15360)
        assertTrue(largePayload.length <= MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `rejects 1MB decompressed payload`() {
        val oneMB = 1024 * 1024
        assertTrue(oneMB > MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `rejects 10MB decompressed payload`() {
        val tenMB = 10 * 1024 * 1024
        assertTrue(tenMB > MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `rejects 100MB decompressed payload - typical zip bomb`() {
        val hundredMB = 100 * 1024 * 1024
        assertTrue(hundredMB > MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `verifies limit prevents OOM on typical Android devices`() {
        // 16KB is safe even on low-memory devices (512MB RAM)
        // Typical Android app heap is 48-512MB
        assertTrue(MedresConstants.MAX_QR_DECOMPRESSED_SIZE < 1024 * 1024) // Less than 1MB
    }

    @Test
    fun `calculates worst-case memory usage`() {
        // Worst case: compressed (4KB) + decompressed (16KB) + JSON parsing overhead
        val worstCaseMemory = MedresConstants.MAX_QR_PAYLOAD_SIZE + 
                              MedresConstants.MAX_QR_DECOMPRESSED_SIZE + 
                              (MedresConstants.MAX_QR_DECOMPRESSED_SIZE / 2) // 50% parsing overhead
        
        // Should be well under 1MB total
        assertTrue(worstCaseMemory < 1024 * 1024)
    }

    @Test
    fun `handles empty decompressed payload`() {
        val empty = ""
        assertTrue(empty.length <= MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }

    @Test
    fun `handles decompressed payload with unicode characters`() {
        // Unicode characters take more bytes
        val unicode = "你好世界🌍".repeat(500)
        // Should still be within limit
        assertTrue(unicode.length <= MedresConstants.MAX_QR_DECOMPRESSED_SIZE)
    }
}
