package com.debate.pangyeori.notification.domain

import com.debate.pangyeori.common.entity.BaseEntity
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.notification.domain.enums.NotificationType
import com.debate.pangyeori.user.domain.User
import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity
@Table(
    name = "notifications",
    indexes = [
        Index(name = "idx_notifications_recipient_id", columnList = "recipient_id"),
        Index(name = "idx_notifications_debate_id", columnList = "debate_id"),
    ],
)
@SQLDelete(sql = "UPDATE notifications SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class Notification private constructor(
    @Id
    @Tsid
    @Column(columnDefinition = "VARCHAR(13)")
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    val recipient: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debate_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    val debate: Debate,

    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Column(nullable = false, length = 255)
    val message: String,

    @Column(name = "read_at")
    var readAt: LocalDateTime? = null,
) : BaseEntity() {
    val isRead: Boolean
        get() = readAt != null

    fun read() {
        if (isRead) return
        readAt = LocalDateTime.now(ZoneOffset.UTC)
    }

    companion object {
        fun create(
            recipient: User,
            debate: Debate,
            type: NotificationType,
            message: String,
        ) = Notification(
            recipient = recipient,
            debate = debate,
            type = type,
            message = message,
        )
    }
}
