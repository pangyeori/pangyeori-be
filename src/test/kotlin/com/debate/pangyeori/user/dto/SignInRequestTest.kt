package com.debate.pangyeori.user.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignInRequestTest {
    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `올바른 이메일과 비밀번호는 검증을 통과한다`() {
        val request = SignInRequest("user@pangyeori.com", "password123!")
        assertTrue(validator.validate(request).isEmpty())
    }

    @Test
    fun `이메일이 없으면 검증에 실패한다`() {
        val violations = validator.validate(SignInRequest(null, "password123!"))
        assertEquals(setOf("email"), violations.map { it.propertyPath.toString() }.toSet())
    }

    @Test
    fun `이메일 형식이 올바르지 않으면 검증에 실패한다`() {
        val violations = validator.validate(SignInRequest("invalid-email", "password123!"))
        assertEquals(setOf("email"), violations.map { it.propertyPath.toString() }.toSet())
    }

    @Test
    fun `비밀번호가 없으면 검증에 실패한다`() {
        val violations = validator.validate(SignInRequest("user@pangyeori.com", " "))
        assertEquals(setOf("password"), violations.map { it.propertyPath.toString() }.toSet())
    }

    @Test
    fun `비밀번호가 64자를 초과하면 검증에 실패한다`() {
        val violations = validator.validate(SignInRequest("user@pangyeori.com", "a".repeat(65)))
        assertEquals(setOf("password"), violations.map { it.propertyPath.toString() }.toSet())
    }
}
