package com.debate.pangyeori.debate.stream.scheduler

import com.debate.pangyeori.debate.stream.registry.DebateSseRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DebateSseHeartbeatScheduler(
    private val debateSseRegistry: DebateSseRegistry,
) {
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MILLIS)
    fun sendHeartbeats() {
        debateSseRegistry.sendHeartbeats()
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 15_000L
    }
}
