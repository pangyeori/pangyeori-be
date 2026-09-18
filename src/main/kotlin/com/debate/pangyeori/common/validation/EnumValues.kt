package com.debate.pangyeori.common.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [EnumValuesValidator::class])
annotation class EnumValues(
    val enumClass: KClass<out Enum<*>>,
    val message: String = "유효하지 않은 값입니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
