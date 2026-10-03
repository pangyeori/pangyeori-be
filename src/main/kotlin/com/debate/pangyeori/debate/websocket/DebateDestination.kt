package com.debate.pangyeori.debate.websocket

object DebateDestination {
    private const val PREFIX = "/sub/debates/"

    fun of(
        debateId: String,
    ) = "$PREFIX$debateId"

    fun debateIdOf(
        destination: String?,
    ): String? = destination
        ?.takeIf { it.startsWith(PREFIX) }
        ?.removePrefix(PREFIX)
        ?.takeIf { it.isNotBlank() && '/' !in it }
}
