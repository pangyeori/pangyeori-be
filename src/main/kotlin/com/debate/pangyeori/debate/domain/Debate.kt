package com.debate.pangyeori.debate.domain

import com.debate.pangyeori.common.entity.BaseEntity
import com.debate.pangyeori.debate.domain.enums.*
import com.debate.pangyeori.user.domain.User
import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "debates",
    indexes = [
        Index(name = "idx_debates_host_id", columnList = "host_id"),
        Index(name = "idx_debates_guest_id", columnList = "guest_id"),
        Index(name = "idx_debates_invite_token", columnList = "invite_token"),
    ],
)
@SQLDelete(sql = "UPDATE debates SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class Debate private constructor(
    @Id
    @Tsid
    @Column(columnDefinition = "VARCHAR(13)")
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    val host: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    val guest: User? = null,

    @Column(nullable = false, length = 100)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val description: String?,

    @Column(name = "host_position", nullable = false, length = 20)
    val hostPosition: DebatePosition,

    @Column(nullable = false, length = 20)
    val status: DebateStatus,

    @Column(name = "current_stage", nullable = false, length = 30)
    val currentStage: DebateStage,

    @Column(name = "turn_time_seconds", nullable = false)
    val turnTimeSeconds: Int,

    @Column(name = "free_debate_time_seconds", nullable = false)
    val freeDebateTimeSeconds: Int,

    @Column(name = "winner_position", length = 20)
    val winnerPosition: WinnerPosition? = null,

    @Column(name = "invite_token", length = 36)
    val inviteToken: String,
) : BaseEntity() {
    companion object {
        fun create(
            host: User,
            title: String,
            description: String?,
            hostPosition: DebatePosition,
            turnTimeSeconds: Int,
            freeDebateTimeSeconds: Int,
            inviteToken: String,
        ) = Debate(
            host = host,
            title = title,
            description = description,
            hostPosition = hostPosition,
            status = DebateStatus.WAITING,
            currentStage = DebateStage.NONE,
            turnTimeSeconds = turnTimeSeconds,
            freeDebateTimeSeconds = freeDebateTimeSeconds,
            inviteToken = inviteToken,
        )
    }
}
