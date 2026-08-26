package com.debate.pangyeori.user.domain.converter

import com.debate.pangyeori.common.converter.CodeEnumConverter
import com.debate.pangyeori.user.domain.enums.UserRole
import jakarta.persistence.Converter

@Converter(autoApply = true)
class UserRoleConverter : CodeEnumConverter<UserRole>(
    enumConstants = UserRole.entries.toTypedArray(),
)
