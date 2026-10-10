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
    name = "debate_histories",
    indexes = [
        Index(name = "idx_debate_histories_debate_id", columnList = "debate_id"),
        Index(name = "idx_debate_histories_user_id", columnList = "user_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_debate_histories_debate_stage", columnNames = ["debate_id", "stage"]),
    ],
)
@SQLDelete(sql = "UPDATE debate_histories SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class DebateHistory private constructor(
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

    @Column(nullable = false, length = 30)
    val stage: DebateStage,

    @Column(columnDefinition = "TEXT")
    val content: String?,
) : BaseEntity() {
    companion object {
        fun create(
            debate: Debate,
            user: User,
            stage: DebateStage,
            content: String?,
        ) = DebateHistory(
            debate = debate,
            user = user,
            stage = stage,
            content = content,
        )
    }
}
