package com.debate.pangyeori.debate.websocket

import com.debate.pangyeori.debate.repository.DebatePresenceRedisRepository
import io.hypersistence.tsid.TSID
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DebateInstance(
    private val presenceRepository: DebatePresenceRedisRepository,
) {
    val id: String = TSID.fast().toString()

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MILLIS)
    fun heartbeat() {
        presenceRepository.refreshInstance(id)
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 5_000L
    }
}
