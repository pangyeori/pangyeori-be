package com.debate.pangyeori.email.sender

class FakeEmailSender : EmailSender {

    override fun send(
        to: String,
        subject: String,
        content: String,
        htmlContent: String,
        inlineImages: List<EmailInlineImage>,
    ) {
        // 테스트에서는 실제로 메일을 발송하지 않는다
    }
}
