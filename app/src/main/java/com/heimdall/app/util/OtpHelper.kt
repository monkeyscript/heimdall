package com.heimdall.app.util

object OtpHelper {
    private val OTP_PATTERNS = listOf(
        // "otp is 123456", "otp: 123456", "code is 123456", "verification code 1234"
        Regex("""(?i)(?:otp|code|passcode|pin|verification|auth|secret)[^\d]{0,15}\b(\d{4,8})\b"""),
        // "123456 is your otp", "123456 is the verification code"
        Regex("""(?i)\b(\d{4,8})\b[^\d]{0,15}(?:is your|is the|to verify|as your|for|verification)"""),
        // Fallback for 4-8 digit numbers in OTP-like messages
        Regex("""\b(\d{4,8})\b""")
    )

    fun extractOtp(messageBody: String): String? {
        val lower = messageBody.lowercase()
        val isOtpContext = lower.contains("otp") || lower.contains("code") ||
                lower.contains("verification") || lower.contains("verify") ||
                lower.contains("passcode") || lower.contains("pin") ||
                lower.contains("one time password") || lower.contains("one-time")

        if (!isOtpContext) return null

        for (pattern in OTP_PATTERNS) {
            val match = pattern.find(messageBody)
            if (match != null) {
                val group = match.groups[1]?.value
                if (group != null && group.length in 4..8) {
                    // Ignore current year matches (e.g. 2024, 2025, 2026) unless explicitly preceded by OTP
                    if (group.length == 4 && (group == "2024" || group == "2025" || group == "2026") && !match.value.lowercase().contains("otp")) {
                        continue
                    }
                    return group
                }
            }
        }
        return null
    }
}
