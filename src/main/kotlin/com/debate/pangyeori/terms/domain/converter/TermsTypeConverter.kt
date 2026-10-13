package com.debate.pangyeori.terms.domain.converter

import com.debate.pangyeori.common.converter.CodeEnumConverter
import com.debate.pangyeori.terms.domain.enums.TermsType
import jakarta.persistence.Converter

@Converter(autoApply = true)
class TermsTypeConverter : CodeEnumConverter<TermsType>(
    enumConstants = TermsType.entries.toTypedArray(),
)
