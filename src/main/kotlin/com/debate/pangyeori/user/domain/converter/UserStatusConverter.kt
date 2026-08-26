package com.debate.pangyeori.user.domain.converter

import com.debate.pangyeori.common.converter.CodeEnumConverter
import com.debate.pangyeori.user.domain.enums.UserStatus
import jakarta.persistence.Converter

@Converter(autoApply = true)
class UserStatusConverter : CodeEnumConverter<UserStatus>(
    enumConstants = UserStatus.entries.toTypedArray(),
)
