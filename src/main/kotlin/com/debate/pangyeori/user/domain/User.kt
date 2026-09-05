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
    var email: String,

    @Column(name = "password", nullable = false, length = 255)
    var password: String,

    @Column(nullable = false, length = 20)
    var nickname: String,

    @Column(name = "profile_image_key", length = 500)
    var profileImageKey: String?,

    @Column(nullable = false, length = 20)
    val role: UserRole,

    @Column(nullable = false, length = 20)
    var status: UserStatus,
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

    fun removeProfileImage() {
        profileImageKey = null
    }

    /**
     * 회원 탈퇴 상태로 변경하고 개인정보 식별에 사용되는 값을 비식별화한다.
     * 실제 soft delete(`deleted_at`)는 repository의 `@SQLDelete`가 담당한다.
     *
     * - `status`를 `WITHDRAWN`으로 변경해 soft delete된 회원의 탈퇴 상태를 표시한다.
     * - soft delete 후에도 unique 제약은 남으므로, `email`/`nickname`을
     *   기존 활성 값과 충돌하지 않는 형태(`@` 없음 / `_` 포함)로 변경해 재가입을 허용한다.
     */
    fun withdraw() {
        status = UserStatus.WITHDRAWN
        email = "withdrawn-$id"
        nickname = "del_$id"
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
