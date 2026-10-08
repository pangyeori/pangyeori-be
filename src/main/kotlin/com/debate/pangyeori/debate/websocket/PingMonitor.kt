package com.debate.pangyeori.debate.websocket

import java.time.Clock
import java.time.Duration
import java.time.Instant

class PingMonitor(
    private val pongTimeout: Duration,
    private val clock: Clock,
) {
    @Volatile
    private var lastPongAt: Instant = Instant.now(clock)

    fun recordPong() {
        lastPongAt = Instant.now(clock)
    }

    fun isExpired(): Boolean = Duration.between(lastPongAt, Instant.now(clock)) > pongTimeout
}
