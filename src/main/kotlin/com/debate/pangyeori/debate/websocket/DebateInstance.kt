package com.debate.pangyeori.debate.websocket

import com.debate.pangyeori.debate.repository.DebatePresenceRedisRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.hypersistence.tsid.TSID
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DebateInstance(
    private val presenceRepository: DebatePresenceRedisRepository,
) {
    private val logger = KotlinLogging.logger {}

    val id: String = TSID.fast().toString()

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MILLIS)
    fun heartbeat() {
        runCatching {
            presenceRepository.refreshInstance(id)
        }.onFailure {
            logger.warn(it) { "인스턴스 생존 키 갱신에 실패했습니다. instanceId=$id" }
        }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 5_000L
    }
}
