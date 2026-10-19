package com.debate.pangyeori.email.sender

import org.springframework.core.io.Resource

data class EmailInlineImage(
    val contentId: String,
    val resource: Resource,
    val contentType: String,
)
