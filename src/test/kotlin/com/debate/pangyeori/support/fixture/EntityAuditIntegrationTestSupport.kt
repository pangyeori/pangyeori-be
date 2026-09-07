package com.debate.pangyeori.support.fixture

import com.debate.pangyeori.common.entity.BaseEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.Table
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * createdAt은 BaseEntity의 protected set + updatable = false 컬럼이라
 * 영속화 이후에는 JPA로 값을 되돌릴 방법이 없다. 만료 시나리오 등을 재현하기 위해
 * SQL로 직접 backdate하는 로직을 이 클래스 하나로 제한해 테스트 코드 전반에 흩어지지 않도록 한다.
 */
@Component
class EntityAuditIntegrationTestSupport(
    private val jdbcTemplate: JdbcTemplate,
    private val entityManager: EntityManager,
) {
    fun <T : BaseEntity> backdateCreatedAt(
        entityClass: KClass<T>,
        id: String,
        hours: Long,
    ) {
        val tableName = entityClass.java.getAnnotation(Table::class.java).name

        entityManager.flush()
        jdbcTemplate.update(
            "UPDATE $tableName SET created_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL ? HOUR) WHERE id = ?",
            hours,
            id,
        )
        entityManager.clear()
    }
}
