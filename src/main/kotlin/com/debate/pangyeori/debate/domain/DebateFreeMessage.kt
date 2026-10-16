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
    name = "debate_free_messages",
    indexes = [
        Index(name = "idx_debate_free_messages_debate_id", columnList = "debate_id"),
        Index(name = "idx_debate_free_messages_user_id", columnList = "user_id"),
    ],
)
@SQLDelete(sql = "UPDATE debate_free_messages SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class DebateFreeMessage private constructor(
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

    @Column(nullable = false, length = 500)
    val content: String,
) : BaseEntity() {
    companion object {
        fun create(
            debate: Debate,
            user: User,
            role: DebateUserRole,
            content: String,
        ) = DebateFreeMessage(
            debate = debate,
            user = user,
            role = role,
            content = content,
        )
    }
}
