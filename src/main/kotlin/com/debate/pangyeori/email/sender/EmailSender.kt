package com.debate.pangyeori.email.sender

interface EmailSender {
    fun send(
        to: String,
        subject: String,
        content: String,
    )
}
