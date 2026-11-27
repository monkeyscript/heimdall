package com.messagate.app.util

import java.util.Locale

/**
 * Single source of truth for text normalization used by the detection engine,
 * the receiver, inbox import and re-evaluation.
 *
 * Spammers evade naive filters by inserting zero-width / formatting characters
 * between letters (e.g. "l\u200Bo\u200Ba\u200Bn"). Every rule evaluation path must
 * strip them the same way, otherwise results differ between live SMS,
 * re-evaluation and the initial inbox import.
 */
object TextNormalizer {

    /** Invisible formatting characters: zero-width space/joiners, BOM, soft hyphen, etc. */
    private val INVISIBLE_CHARS = Regex("[\\p{Cf}\\u200B-\\u200D\\uFEFF]")

    /** Upper bound for evaluated body text. Keeps a hostile/oversized SMS cheap to scan. */
    const val MAX_BODY_LENGTH = 4096

    fun stripInvisible(text: String): String = INVISIBLE_CHARS.replace(text, "")

    fun normalizeBody(text: String): String =
        stripInvisible(text).take(MAX_BODY_LENGTH)

    fun lower(text: String): String = text.lowercase(Locale.ROOT)
}
