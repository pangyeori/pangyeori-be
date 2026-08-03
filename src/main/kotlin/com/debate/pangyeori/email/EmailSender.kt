package com.debate.pangyeori.email

interface EmailSender {
    fun send(
        to: String,
        subject: String,
        content: String,
    )
}
