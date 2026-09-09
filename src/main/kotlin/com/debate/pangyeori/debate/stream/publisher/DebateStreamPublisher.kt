package com.debate.pangyeori.debate.stream.publisher

import com.debate.pangyeori.debate.stream.message.DebateStreamMessage

interface DebateStreamPublisher {
    fun publish(
        message: DebateStreamMessage,
    )
}
