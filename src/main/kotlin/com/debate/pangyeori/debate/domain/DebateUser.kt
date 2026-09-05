package com.debate.pangyeori.debate.domain

import com.debate.pangyeori.common.entity.BaseEntity
import com.debate.pangyeori.debate.domain.enums.*
import com.debate.pangyeori.user.domain.User
import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(
    name = "debate_users",
    uniqueConstraints = [UniqueConstraint(name = "uk_debate_users_debate_user", columnNames = ["debate_id", "user_id"])],
    indexes = [
        Index(name = "idx_debate_users_debate_status", columnList = "debate_id,status"),
        Index(name = "idx_debate_users_user_id", columnList = "user_id"),
    ],
)
@SQLDelete(sql = "UPDATE debate_users SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class DebateUser private constructor(
    @Id
    @Tsid
    @Column(columnDefinition = "VARCHAR(13)")
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debate_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    val debate: Debate,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    val user: User,

    @Column(nullable = false, length = 20)
    val role: DebateUserRole,

    @Column(nullable = false, length = 20)
    val position: DebatePosition,

    @Column(nullable = false, length = 20)
    val status: DebateUserStatus,

    @Column(name = "disconnect_count", nullable = false)
    val disconnectCount: Int,

    @Column(name = "joined_at")
    val joinedAt: LocalDateTime?,
) : BaseEntity() {
    companion object {
        fun create(
            debate: Debate,
            user: User,
            position: DebatePosition,
        ) = DebateUser(
            debate = debate,
            user = user,
            role = DebateUserRole.HOST,
            position = position,
            status = DebateUserStatus.ACCEPTED,
            disconnectCount = 0,
            joinedAt = LocalDateTime.now(),
        )
    }
}
