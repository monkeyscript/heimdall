package com.heimdall.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.regex.Pattern

object LinkifyHelper {

    // Supported top-level domains for strict, safe link identification
    private const val VALID_TLDS =
        "com|in|org|net|io|co|gov|edu|ai|app|dev|me|info|xyz|biz|cc|to|ly|is|gl|link|tech|club|store|online|site|live|tv|mobi|bank|nic|mil"

    // Strict URL Regex: requires http/https, www, or a recognized valid domain TLD
    private val STRICT_URL_REGEX: Pattern = Pattern.compile(
        """(?i)\b(?:https?://[^\s<>"{}|\\^`]+|www\.[^\s<>"{}|\\^`]+|(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:$VALID_TLDS)(?:/[^\s<>"{}|\\^`]*)?)"""
    )

    // Strict Phone Regex Patterns:
    // 1. Standard 10-digit mobile (with optional +91 or leading 0, starting with 6-9)
    // 2. Toll-free helpline (1800-xxx-xxx or 1900-xxx-xxx)
    // 3. International format with + country code (min 10 total digits)
    // 4. Landline with STD code (0xx-xxxxxxx)
    private val STRICT_PHONE_REGEX: Pattern = Pattern.compile(
        """(?:\b(?:\+91[-.\s]?|0)?[6-9]\d{4}[-.\s]?\d{5}\b)|(?:\b1[89]00[-.\s]?\d{3}[-.\s]?\d{3,4}\b)|(?:\+\d{1,3}[-.\s]?(?:\(?\d{2,4}\)?[-.\s]?)?\d{3,5}[-.\s]?\d{4,5})|(?:\b0\d{2,4}[-.\s]?\d{6,8}\b)"""
    )

    fun createAnnotatedMessage(text: String, context: Context): SpannableString {
        val spannable = SpannableString(text)
        val accentColor = 0xFFF5B718.toInt()

        val occupiedRanges = mutableListOf<IntRange>()

        // 1. Identify OTP location to strictly prevent it from being classified as a phone number
        val extractedOtp = OtpHelper.extractOtp(text)
        if (extractedOtp != null) {
            val otpIndex = text.indexOf(extractedOtp)
            if (otpIndex >= 0) {
                occupiedRanges.add(otpIndex until (otpIndex + extractedOtp.length))
            }
        }

        // 2. Detect and style URLs with strict domain validation & cleanup
        val urlMatcher = STRICT_URL_REGEX.matcher(text)
        while (urlMatcher.find()) {
            val start = urlMatcher.start()
            val matchedUrl = urlMatcher.group()

            // Clean trailing punctuation
            val cleanUrl = matchedUrl.trimEnd('.', ',', ')', '(', '!', '?', ';', ':', '>', ']', '[')
            val cleanEnd = start + cleanUrl.length

            if (cleanUrl.isNotEmpty()) {
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) { openUrl(context, cleanUrl) }
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        ds.color = accentColor
                        ds.isFakeBoldText = true
                        ds.isUnderlineText = true
                    }
                }, start, cleanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                occupiedRanges.add(start until cleanEnd)
            }
        }

        // 3. Detect and style Genuine Phone Numbers (excluding OTPs & URLs)
        val phoneMatcher = STRICT_PHONE_REGEX.matcher(text)
        while (phoneMatcher.find()) {
            val start = phoneMatcher.start()
            val end = phoneMatcher.end()
            val matchedPhone = phoneMatcher.group().trim()

            // Ensure it has at least 10 digits (or is an 1800/1900 toll free number)
            val digitCount = matchedPhone.count { it.isDigit() }
            val isTollFree = matchedPhone.startsWith("1800") || matchedPhone.startsWith("1900")
            val isGenuinePhone = (digitCount in 10..15) || (isTollFree && digitCount in 10..11)

            if (isGenuinePhone) {
                val overlaps = occupiedRanges.any { range -> start < range.last && end > range.first }
                if (!overlaps) {
                    // Check preceding characters to ensure it's not a price/currency (e.g. Rs. 5000000000)
                    val prefixSubstring = if (start >= 4) text.substring(start - 4, start).lowercase() else ""
                    val isCurrency = prefixSubstring.contains("rs") ||
                            prefixSubstring.contains("inr") ||
                            prefixSubstring.contains("₹") ||
                            prefixSubstring.contains("$")

                    if (!isCurrency) {
                        spannable.setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) { dialPhone(context, matchedPhone) }
                            override fun updateDrawState(ds: android.text.TextPaint) {
                                ds.color = accentColor
                                ds.isFakeBoldText = true
                                ds.isUnderlineText = true
                            }
                        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        occupiedRanges.add(start until end)
                    }
                }
            }
        }

        return spannable
    }

    /**
     * Safely opens the given URL enforcing HTTPS security.
     */
    fun openUrl(context: Context, rawUrl: String) {
        try {
            var url = rawUrl.trim()

            // Security: Enforce HTTPS protection on all web links
            if (url.startsWith("http://", ignoreCase = true)) {
                url = "https://" + url.substring(7)
            } else if (!url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }

            val parsedUri = Uri.parse(url)
            // Ensure scheme is strictly https
            if (parsedUri.scheme?.equals("https", ignoreCase = true) == true) {
                val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Blocked non-secure link", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link: $rawUrl", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Safely opens the phone dialer pre-filling the clean phone digits.
     */
    fun dialPhone(context: Context, rawPhone: String) {
        try {
            val cleanPhone = rawPhone.filter { it.isDigit() || it == '+' }
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open dialer for: $rawPhone", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ClickableLinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 22.sp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.toPx() }
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val spannable = remember(text) { LinkifyHelper.createAnnotatedMessage(text, context) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(0xFFF4F4F5.toInt())       // TextPrimary
                setLinkTextColor(0xFFF5B718.toInt())    // YellowAccent
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
                setLineSpacing(lineHeightPx - fontSizePx, 1f)
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = 0x40F5B718             // subtle yellow selection highlight
                typeface = Typeface.DEFAULT
            }
        },
        update = { tv ->
            tv.text = spannable
        }
    )
}
