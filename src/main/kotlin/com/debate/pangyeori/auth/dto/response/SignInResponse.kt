package com.debate.pangyeori.auth.dto.response

import com.fasterxml.jackson.annotation.JsonIgnore

data class SignInResponse(
    val accessToken: String,
    @get:JsonIgnore
    val refreshToken: String,
    val tokenType: String = "Bearer",
)
