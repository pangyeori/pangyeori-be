package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateFreeMessage
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor

class DebateFreeMessageQueryRepositoryImpl(
    private val kotlinJdslJpqlExecutor: KotlinJdslJpqlExecutor,
) : DebateFreeMessageQueryRepository {
    override fun findAllByDebateId(
        debateId: String,
        cursor: String?,
        limit: Int,
    ): List<DebateFreeMessage> = kotlinJdslJpqlExecutor.findAll(limit = limit) {
        select(
            entity(DebateFreeMessage::class),
        ).from(
            entity(DebateFreeMessage::class),
        ).whereAnd(
            path(DebateFreeMessage::debate)(Debate::id).equal(debateId),
            cursor?.let { path(DebateFreeMessage::id).lessThan(it) },
        ).orderBy(
            path(DebateFreeMessage::id).desc(),
        )
    }.filterNotNull()
}
