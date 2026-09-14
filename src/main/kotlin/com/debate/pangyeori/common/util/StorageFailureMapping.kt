package com.debate.pangyeori.common.util

import com.debate.pangyeori.common.exception.StorageUnavailableException
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.dao.DataAccessException

/**
 * 폴백 원본이 없는 유일 저장소(Redis 등) 호출을 감싼다.
 * `DataAccessException`(연결 실패·타임아웃 등)을 [StorageUnavailableException](503)으로 변환한다.
 *
 * JPA 호출과 섞인 메서드에서는 저장소 호출 지점만 좁게 감싼다. 메서드 전체를 감싸면
 * JPA의 `DataIntegrityViolationException`(unique 제약 위반 등)까지 저장소 장애로 오분류된다.
 */
inline fun <T> mapStorageFailure(
    logger: KLogger,
    context: String,
    block: () -> T,
): T = try {
    block()
} catch (e: DataAccessException) {
    logger.error(e) { "$context 저장소 접근에 실패했습니다." }
    throw StorageUnavailableException()
}
