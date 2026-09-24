package com.debate.pangyeori.notification.message

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.notification.domain.enums.NotificationType

object NotificationMessages {
    fun build(
        type: NotificationType,
        debate: Debate,
        requesterNickname: String? = null,
    ): String = when (type) {
        NotificationType.QUEUE_REQUEST_ADDED -> "${requesterNickname}님이 '${debate.title}' 토론 참가를 신청했습니다."
        NotificationType.QUEUE_REQUEST_REMOVED -> "${requesterNickname}님이 '${debate.title}' 토론 참가 신청을 취소했습니다."
        NotificationType.QUEUE_REQUEST_ACCEPTED -> "'${debate.title}' 토론 참가가 확정되었습니다."
        NotificationType.QUEUE_REQUEST_REJECTED -> "'${debate.title}' 토론 참가 요청이 거절되었습니다."
        NotificationType.DEBATE_CANCELLED -> "'${debate.title}' 토론이 취소되었습니다."
    }
}
