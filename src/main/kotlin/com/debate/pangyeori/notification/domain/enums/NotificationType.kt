package com.debate.pangyeori.notification.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class NotificationType(
    override val code: String,
) : CodeEnum {
    QUEUE_REQUEST_ADDED("QUEUE_REQUEST_ADDED"),
    QUEUE_REQUEST_REMOVED("QUEUE_REQUEST_REMOVED"),
    QUEUE_REQUEST_ACCEPTED("QUEUE_REQUEST_ACCEPTED"),
    QUEUE_REQUEST_REJECTED("QUEUE_REQUEST_REJECTED"),
    DEBATE_CANCELLED("DEBATE_CANCELLED"),
}
