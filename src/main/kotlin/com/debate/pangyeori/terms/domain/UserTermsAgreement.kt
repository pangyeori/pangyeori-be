package com.debate.pangyeori.terms.domain

import com.debate.pangyeori.common.entity.BaseEntity
import com.debate.pangyeori.user.domain.User
import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity
@Table(
    name = "user_terms_agreements",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_terms_agreements_user_id_terms_id", columnNames = ["user_id", "terms_id"]),
    ],
    indexes = [
        Index(name = "idx_user_terms_agreements_terms_id", columnList = "terms_id"),
    ],
)
@SQLDelete(sql = "UPDATE user_terms_agreements SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class UserTermsAgreement private constructor(
    @Id
    @Tsid
    @Column(columnDefinition = "VARCHAR(13)")
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "terms_id",
        nullable = false,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    val terms: Terms,

    @Column(name = "agreed_at", nullable = false)
    var agreedAt: LocalDateTime,

    @Column(name = "withdrawn_at")
    var withdrawnAt: LocalDateTime? = null,
) : BaseEntity() {

    fun withdraw() {
        if (withdrawnAt != null) return
        withdrawnAt = LocalDateTime.now(ZoneOffset.UTC)
    }

    fun agreeAgain() {
        agreedAt = LocalDateTime.now(ZoneOffset.UTC)
        withdrawnAt = null
    }

    companion object {
        fun create(
            user: User,
            terms: Terms,
        ) = UserTermsAgreement(
            user = user,
            terms = terms,
            agreedAt = LocalDateTime.now(ZoneOffset.UTC),
        )
    }
}
