package com.debate.pangyeori.common.util

fun String.maskEmail(): String {
    val atIndex = indexOf('@')
    if (atIndex <= 0) return MASKED_EMAIL_FALLBACK

    val localPart = substring(0, atIndex)
    val domain = substring(atIndex)
    val visibleLength = minOf(EMAIL_MASK_VISIBLE_LENGTH, localPart.length)

    return localPart.take(visibleLength) + "*".repeat(localPart.length - visibleLength) + domain
}

private const val EMAIL_MASK_VISIBLE_LENGTH = 2
private const val MASKED_EMAIL_FALLBACK = "***"
