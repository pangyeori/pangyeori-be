package com.debate.pangyeori.user.domain

import com.debate.pangyeori.common.entity.BaseEntity
import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = ["token_hash"]),
    ],
    indexes = [
        Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
        Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at"),
    ],
)
@SQLDelete(sql = "UPDATE refresh_tokens SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class RefreshToken private constructor(
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

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,
) : BaseEntity() {

    fun revoke() {
        revokedAt = LocalDateTime.now(ZoneOffset.UTC)
    }

    fun isAvailable() = revokedAt == null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))

    companion object {
        private const val HASH_ALGORITHM = "SHA-256"

        fun create(
            user: User,
            token: String,
            expiresInSeconds: Long,
        ) = RefreshToken(
            user = user,
            tokenHash = hash(token),
            expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(expiresInSeconds),
        )

        fun hash(
            token: String,
        ): String = MessageDigest.getInstance(HASH_ALGORITHM)
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
