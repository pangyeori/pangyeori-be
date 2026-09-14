package com.debate.pangyeori.common.validation

import com.debate.pangyeori.common.converter.CodeEnum
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class EnumValueValidator : ConstraintValidator<EnumValue, String> {

    private var acceptedCodes: Set<String> = emptySet()

    override fun initialize(
        constraintAnnotation: EnumValue,
    ) {
        acceptedCodes = constraintAnnotation.enumClass.java.enumConstants
            .map { (it as CodeEnum).code }
            .toSet()
    }

    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean = value == null || value in acceptedCodes
}
