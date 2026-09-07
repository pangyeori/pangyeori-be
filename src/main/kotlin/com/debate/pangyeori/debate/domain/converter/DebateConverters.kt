package com.debate.pangyeori.debate.domain.converter

import com.debate.pangyeori.common.converter.CodeEnumConverter
import com.debate.pangyeori.debate.domain.enums.*
import jakarta.persistence.Converter

@Converter(autoApply = true)
class DebatePositionConverter : CodeEnumConverter<DebatePosition>(
    enumConstants = DebatePosition.entries.toTypedArray(),
)

@Converter(autoApply = true)
class DebateStatusConverter : CodeEnumConverter<DebateStatus>(
    enumConstants = DebateStatus.entries.toTypedArray(),
)

@Converter(autoApply = true)
class DebateStageConverter : CodeEnumConverter<DebateStage>(
    enumConstants = DebateStage.entries.toTypedArray(),
)

@Converter(autoApply = true)
class DebateUserRoleConverter : CodeEnumConverter<DebateUserRole>(
    enumConstants = DebateUserRole.entries.toTypedArray(),
)

@Converter(autoApply = true)
class DebateUserStatusConverter : CodeEnumConverter<DebateUserStatus>(
    enumConstants = DebateUserStatus.entries.toTypedArray(),
)

@Converter(autoApply = true)
class WinnerPositionConverter : CodeEnumConverter<WinnerPosition>(
    enumConstants = WinnerPosition.entries.toTypedArray(),
)
