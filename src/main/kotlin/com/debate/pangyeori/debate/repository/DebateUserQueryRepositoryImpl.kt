package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.user.domain.User
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor

class DebateUserQueryRepositoryImpl(
    private val kotlinJdslJpqlExecutor: KotlinJdslJpqlExecutor,
) : DebateUserQueryRepository {
    override fun findAllParticipating(
        userId: String,
        status: List<DebateStatus>?,
        role: DebateUserRole?,
        keyword: String?,
        cursor: String?,
        limit: Int,
    ): List<DebateUser> = kotlinJdslJpqlExecutor.findAll(limit = limit) {
        val host = entity(User::class, alias = HOST_ALIAS)
        val guest = entity(User::class, alias = GUEST_ALIAS)

        select(
            entity(DebateUser::class),
        ).from(
            entity(DebateUser::class),
            fetchJoin(DebateUser::debate),
            fetchJoin(Debate::host).`as`(host),
            leftFetchJoin(Debate::guest).`as`(guest),
        ).whereAnd(
            path(DebateUser::user)(User::id).equal(userId),
            path(DebateUser::status).`in`(PARTICIPATING_STATUSES),
            status?.let { path(Debate::status).`in`(it) },
            role?.let { path(DebateUser::role).equal(it) },
            keyword?.let {
                or(
                    path(Debate::title).like("%$it%"),
                    path(Debate::description).like("%$it%"),
                )
            },
            cursor?.let { path(Debate::id).lessThan(it) },
        ).orderBy(
            path(Debate::id).desc(),
        )
    }.filterNotNull()

    companion object {
        private const val HOST_ALIAS = "host"
        private const val GUEST_ALIAS = "guest"
        private val PARTICIPATING_STATUSES = listOf(DebateUserStatus.PENDING, DebateUserStatus.ACCEPTED)
    }
}
