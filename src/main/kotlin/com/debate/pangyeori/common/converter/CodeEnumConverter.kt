package com.debate.pangyeori.common.converter

import jakarta.persistence.AttributeConverter

abstract class CodeEnumConverter<T>(
    private val enumConstants: Array<T>,
) : AttributeConverter<T, String> where T : Enum<T>, T : CodeEnum {
    override fun convertToDatabaseColumn(
        attribute: T?,
    ): String? = attribute?.code

    override fun convertToEntityAttribute(
        dbData: String?,
    ): T? = dbData?.let { data ->
        enumConstants.firstOrNull { it.code == data }
            ?: throw IllegalStateException("알 수 없는 enum 코드입니다: $data")
    }
}
