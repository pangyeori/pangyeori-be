package com.debate.pangyeori.user.dto

import com.fasterxml.jackson.annotation.JsonIgnore

data class SignInResponse(
    val accessToken: String,
    @get:JsonIgnore
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val accessTokenExpiresIn: Long,
    val refreshTokenExpiresIn: Long,
)
