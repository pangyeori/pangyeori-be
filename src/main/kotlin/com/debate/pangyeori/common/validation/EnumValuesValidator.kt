package com.debate.pangyeori.common.validation

import com.debate.pangyeori.common.converter.CodeEnum
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class EnumValuesValidator : ConstraintValidator<EnumValues, Collection<String>> {

    private var acceptedCodes: Set<String> = emptySet()

    override fun initialize(
        constraintAnnotation: EnumValues,
    ) {
        acceptedCodes = constraintAnnotation.enumClass.java.enumConstants
            .map { (it as CodeEnum).code }
            .toSet()
    }

    override fun isValid(
        value: Collection<String>?,
        context: ConstraintValidatorContext,
    ): Boolean = value == null || value.all { it in acceptedCodes }
}
