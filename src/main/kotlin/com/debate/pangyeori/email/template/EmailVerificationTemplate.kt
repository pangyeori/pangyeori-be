package com.debate.pangyeori.email.template

import com.debate.pangyeori.email.sender.EmailInlineImage
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class EmailVerificationTemplate {

    private val template: String = ClassPathResource(TEMPLATE_PATH).inputStream.use {
        String(it.readAllBytes(), Charsets.UTF_8)
    }

    val inlineImages = listOf(
        EmailInlineImage(
            contentId = MASCOT_CONTENT_ID,
            resource = ClassPathResource(MASCOT_PATH),
            contentType = MASCOT_CONTENT_TYPE,
        ),
    )

    fun render(
        code: String,
        validityMinutes: Long,
    ) = template
        .replace(CODE_PLACEHOLDER, code)
        .replace(VALIDITY_MINUTES_PLACEHOLDER, validityMinutes.toString())

    companion object {
        private const val TEMPLATE_PATH = "email/templates/email-verification.html"
        private const val MASCOT_PATH = "email/images/pangyeori-mascot.png"
        private const val MASCOT_CONTENT_ID = "pangyeori-mascot"
        private const val MASCOT_CONTENT_TYPE = "image/png"
        private const val CODE_PLACEHOLDER = "{{code}}"
        private const val VALIDITY_MINUTES_PLACEHOLDER = "{{validityMinutes}}"
    }
}
