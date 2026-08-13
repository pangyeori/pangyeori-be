package com.debate.pangyeori.email

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Body
import software.amazon.awssdk.services.sesv2.model.Content
import software.amazon.awssdk.services.sesv2.model.Destination
import software.amazon.awssdk.services.sesv2.model.EmailContent
import software.amazon.awssdk.services.sesv2.model.Message
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest

@Component
@Profile("!local")
class SesEmailSender(
    private val sesV2Client: SesV2Client,
    @param:Value("\${aws.ses.from-address}") private val fromAddress: String,
) : EmailSender {

    @Retryable(includes = [SdkException::class], maxRetries = MAX_RETRIES, delay = RETRY_DELAY_MS)
    override fun send(
        to: String,
        subject: String,
        content: String,
    ) {
        val request = SendEmailRequest.builder()
            .fromEmailAddress(fromAddress)
            .destination(Destination.builder().toAddresses(to).build())
            .content(
                EmailContent.builder()
                    .simple(
                        Message.builder()
                            .subject(Content.builder().data(subject).build())
                            .body(Body.builder().text(Content.builder().data(content).build()).build())
                            .build(),
                    )
                    .build(),
            )
            .build()

        sesV2Client.sendEmail(request)
    }

    companion object {
        private const val MAX_RETRIES = 2L
        private const val RETRY_DELAY_MS = 500L
    }
}
