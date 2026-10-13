package com.debate.pangyeori.terms.domain

import com.debate.pangyeori.common.entity.BaseEntity
import com.debate.pangyeori.terms.domain.enums.TermsType
import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(
    name = "terms",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_terms_type_version", columnNames = ["type", "version"]),
    ],
)
@SQLDelete(sql = "UPDATE terms SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class Terms private constructor(
    @Id
    @Tsid
    @Column(columnDefinition = "VARCHAR(13)")
    val id: String? = null,

    @Column(nullable = false, length = 30)
    val type: TermsType,

    @Column(nullable = false)
    val version: Int,

    @Column(nullable = false, length = 100)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "is_required", nullable = false)
    val required: Boolean,

    @Column(name = "effective_at", nullable = false)
    val effectiveAt: LocalDateTime,
) : BaseEntity() {
    companion object {
        fun create(
            type: TermsType,
            version: Int,
            title: String,
            content: String,
            required: Boolean,
            effectiveAt: LocalDateTime,
        ) = Terms(
            type = type,
            version = version,
            title = title,
            content = content,
            required = required,
            effectiveAt = effectiveAt,
        )
    }
}
