package edu.aiims.medresodk.auth.qr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedresQrTransitionPolicyTest {

    @Test
    fun `switching from main project to draft requires session reset`() {
        val current = MedresQrTransitionPolicy.classifyCurrentServerUrl(
            "https://central.example.com/v1/key/TOKEN/projects/1"
        )
        val scanned = DraftFormQr(
            originalDraftUrl = "https://central.example.com/v1/test/DRAFT/projects/1/forms/form_b/draft",
            centralProjectId = "1",
            formId = "form_b",
            displayName = "[Draft] Form B",
            displayIcon = null,
            generalSettingsJson = "{}"
        )

        assertTrue(MedresQrTransitionPolicy.requiresSessionReset(current, scanned))
    }

    @Test
    fun `switching from one main project to another requires session reset`() {
        val current = MedresQrTransitionPolicy.classifyCurrentServerUrl(
            "https://central.example.com/v1/key/TOKEN/projects/1"
        )
        val scanned = MedresProjectQr(
            originalUrl = "https://central.example.com/v1/projects/2",
            authBaseUrl = "https://central.example.com/v1",
            centralProjectId = "2",
            projectName = "Project Two",
            usernameHint = "alice",
            generalSettingsJson = "{}",
            adminSettingsJson = "{}"
        )

        assertTrue(MedresQrTransitionPolicy.requiresSessionReset(current, scanned))
    }

    @Test
    fun `rescanning same draft form with new token does not require session reset`() {
        val current = MedresQrTransitionPolicy.classifyCurrentServerUrl(
            "https://central.example.com/v1/test/OLD/projects/1/forms/form_b/draft"
        )
        val scanned = DraftFormQr(
            originalDraftUrl = "https://central.example.com/v1/test/NEW/projects/1/forms/form_b/draft",
            centralProjectId = "1",
            formId = "form_b",
            displayName = "[Draft] Form B v3",
            displayIcon = null,
            generalSettingsJson = "{}"
        )

        assertFalse(MedresQrTransitionPolicy.requiresSessionReset(current, scanned))
    }

    @Test
    fun `first QR scan from no current project does not require session reset`() {
        val current = MedresQrTransitionPolicy.classifyCurrentServerUrl(null)
        val scanned = MedresProjectQr(
            originalUrl = "https://central.example.com/v1/projects/1",
            authBaseUrl = "https://central.example.com/v1",
            centralProjectId = "1",
            projectName = "Project One",
            usernameHint = "vivek",
            generalSettingsJson = "{}",
            adminSettingsJson = "{}"
        )

        assertFalse(MedresQrTransitionPolicy.requiresSessionReset(current, scanned))
    }
}
