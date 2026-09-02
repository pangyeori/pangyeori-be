package com.debate.pangyeori.user.domain

import com.debate.pangyeori.common.entity.BaseEntity
import com.debate.pangyeori.user.domain.enums.UserRole
import com.debate.pangyeori.user.domain.enums.UserStatus
import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_email", columnNames = ["email"]),
        UniqueConstraint(name = "uk_users_nickname", columnNames = ["nickname"]),
    ],
    indexes = [
        Index(name = "idx_users_status", columnList = "status"),
    ],
)
@SQLDelete(sql = "UPDATE users SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class User private constructor(
    @Id
    @Tsid
    @Column(columnDefinition = "VARCHAR(13)")
    val id: String? = null,

    @Column(nullable = false, length = 255)
    val email: String,

    @Column(name = "password", nullable = false, length = 255)
    var password: String,

    @Column(nullable = false, length = 12)
    var nickname: String,

    @Column(name = "profile_image_key", length = 500)
    var profileImageKey: String?,

    @Column(nullable = false, length = 20)
    val role: UserRole,

    @Column(nullable = false, length = 20)
    val status: UserStatus,
) : BaseEntity() {
    fun changePassword(
        newPassword: String,
    ) {
        password = newPassword
    }

    fun changeNickname(
        newNickname: String,
    ) {
        nickname = newNickname
    }

    fun changeProfileImage(
        newKey: String,
    ) {
        profileImageKey = newKey
    }

    companion object {
        fun create(
            email: String,
            password: String,
            nickname: String,
        ) = User(
            email = email,
            password = password,
            nickname = nickname,
            profileImageKey = null,
            role = UserRole.USER,
            status = UserStatus.ACTIVE,
        )
    }
}
