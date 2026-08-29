package com.heimdall.app.util

enum class MessageCategory(val emoji: String, val label: String) {
    SPAM("⚠️", "SPAM"),
    OTP("🔑", "OTP"),
    TRAVEL("✈️", "TRVL"),
    DELIVERY("📦", "PKG"),
    CARD("💳", "CARD"),
    BANK("🏦", "BANK"),
    CHAT("💬", "MSG");

    companion object {
        fun fromString(name: String?): MessageCategory {
            return try {
                if (name != null) valueOf(name) else CHAT
            } catch (e: Exception) {
                CHAT
            }
        }
    }
}

object CategoryHelper {

    /**
     * Pre-computes the semantic category for an incoming message once on arrival.
     */
    fun detectCategory(sender: String, body: String, isSpam: Boolean): MessageCategory {
        // 1. Spam matches active filter rules
        if (isSpam) return MessageCategory.SPAM

        val lowerBody = body.lowercase()
        val lowerSender = sender.lowercase()

        // 2. Order Deliveries (e.g. Decathlon, Amazon, Swiggy, Couriers)
        val isDeliveryIntent = lowerBody.contains("ready for pickup") ||
                lowerBody.contains("collect your order") ||
                lowerBody.contains("out for delivery") ||
                lowerBody.contains("order dispatched") ||
                lowerBody.contains("order delivered") ||
                lowerBody.contains("order placed") ||
                lowerBody.contains("order confirmed") ||
                lowerBody.contains("order(") ||
                lowerBody.contains("order #") ||
                lowerBody.contains("track package") ||
                lowerBody.contains("track your order") ||
                lowerBody.contains("item(s) shipped") ||
                lowerBody.contains("on its way to the store") ||
                lowerBody.contains("delivery agent") ||
                lowerBody.contains("delivered to your") ||
                lowerSender.contains("dcthln") ||
                lowerSender.contains("delhivery") ||
                lowerSender.contains("bluedart")

        if (isDeliveryIntent && (lowerBody.contains("order") || lowerBody.contains("pickup") || lowerBody.contains("delivery") || lowerBody.contains("package") || lowerSender.contains("dcthln"))) {
            return MessageCategory.DELIVERY
        }

        // 3. OTP & 2FA authentication codes (top action priority)
        val extractedOtp = OtpHelper.extractOtp(body)
        val isOtpKeyword = lowerBody.contains("otp") ||
                lowerBody.contains("verification code") ||
                lowerBody.contains("secret code") ||
                lowerBody.contains("one time password") ||
                lowerBody.contains("security code") ||
                lowerBody.contains("login code") ||
                lowerBody.contains("auth code")

        if (extractedOtp != null || isOtpKeyword) {
            return MessageCategory.OTP
        }

        // 4. Travel & Transit (Bus, Train, Flight, Cab, Hotel)
        if (lowerBody.contains("pnr:") || lowerBody.contains("pnr ") ||
            lowerBody.contains("bus no:") || lowerBody.contains("bus no ") ||
            lowerBody.contains("flight") || lowerBody.contains("boarding pass") ||
            lowerBody.contains("boarding point") || lowerBody.contains("terminal ") ||
            lowerBody.contains("train ") || lowerBody.contains("coach ") ||
            lowerBody.contains("seat no:") || lowerBody.contains("seat no ") ||
            lowerBody.contains("irctc") || lowerBody.contains("travels") ||
            lowerBody.contains("tourists") || lowerBody.contains("passenger") ||
            lowerBody.contains("next stop is") || lowerBody.contains("upcoming bus") ||
            lowerBody.contains("bus travel") || lowerBody.contains("uber") ||
            lowerBody.contains("ola ride") || lowerBody.contains("rapido") ||
            lowerSender.contains("bitlaa") || lowerSender.contains("clrtrp") ||
            lowerSender.contains("irctc")) {
            return MessageCategory.TRAVEL
        }

        // 5. Credit Card (Spends, Available Limit, Statements, CC Bills)
        if (lowerBody.contains("credit card") ||
            lowerBody.contains("card ending with") ||
            lowerBody.contains("card ending") ||
            lowerBody.contains("card no.") ||
            lowerBody.contains("card no ") ||
            lowerBody.contains("card xx") ||
            lowerBody.contains("block cc") ||
            lowerBody.contains("avl limit") ||
            lowerBody.contains("available limit") ||
            lowerBody.contains("credit limit") ||
            lowerBody.contains("card usage limits") ||
            lowerBody.contains("min amt due") ||
            lowerBody.contains("minimum due") ||
            lowerBody.contains("statement generated") ||
            lowerBody.contains("cclimitincrease") ||
            lowerBody.contains("axisbankcc") ||
            lowerBody.contains("mycards") ||
            (lowerBody.contains("card ") && lowerBody.contains("spent"))) {
            return MessageCategory.CARD
        }

        // 6. Bank / Savings / Current Account (Debits, Credits, Balances, UPI, MPIN)
        if (lowerBody.contains("debited") ||
            lowerBody.contains("credited") ||
            lowerBody.contains("from a/c") ||
            lowerBody.contains("from ac") ||
            lowerBody.contains("to a/c") ||
            lowerBody.contains("to ac") ||
            lowerBody.contains("a/c xx") ||
            lowerBody.contains("ac xx") ||
            lowerBody.contains("a/c x") ||
            lowerBody.contains("ac x") ||
            lowerBody.contains("bal rs") ||
            lowerBody.contains("balance rs") ||
            lowerBody.contains("savings account") ||
            lowerBody.contains("savings a/c") ||
            lowerBody.contains("current a/c") ||
            lowerBody.contains("via upi") ||
            lowerBody.contains("upi ref") ||
            lowerBody.contains("fedmobile") ||
            lowerBody.contains("mpin") ||
            lowerBody.contains("netbanking") ||
            lowerBody.contains("block dc") ||
            lowerBody.contains("withdrawn rs") ||
            lowerBody.contains("deposited") ||
            lowerBody.contains("imps") ||
            lowerBody.contains("neft") ||
            lowerBody.contains("rtgs") ||
            lowerSender.contains("fedbnk") ||
            lowerSender.contains("hdfcbk") ||
            lowerSender.contains("axisbk")) {
            return MessageCategory.BANK
        }

        // 7. General Chat fallback
        return MessageCategory.CHAT
    }
}
