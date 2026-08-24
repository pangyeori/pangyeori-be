package com.debate.pangyeori.support.fixture

import com.debate.pangyeori.common.entity.BaseEntity
import java.time.LocalDateTime

/**
 * BaseEntity의 protected set 필드는 실제 영속화 없이는 채울 수 없으므로,
 * 영속성 컨텍스트가 없는 Mockk 기반 단위 테스트에서 리플렉션으로 직접 값을 채우는 함수
 */
fun <T : BaseEntity> setAuditFields(
    entity: T,
    createdAt: LocalDateTime = LocalDateTime.now(),
    modifiedAt: LocalDateTime = createdAt,
): T {
    BaseEntity::class.java
        .getDeclaredField("createdAt")
        .apply {
            isAccessible = true
            set(entity, createdAt)
        }

    BaseEntity::class.java
        .getDeclaredField("modifiedAt")
        .apply {
            isAccessible = true
            set(entity, modifiedAt)
        }

    return entity
}
