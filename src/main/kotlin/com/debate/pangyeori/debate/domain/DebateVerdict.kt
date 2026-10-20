package com.debate.pangyeori.debate.domain

import com.debate.pangyeori.common.entity.BaseEntity
import com.debate.pangyeori.debate.domain.enums.*
import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal

@Entity
@Table(
    name = "debate_verdicts",
    indexes = [
        Index(name = "idx_debate_verdicts_debate_id", columnList = "debate_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_debate_verdicts_debate_stage", columnNames = ["debate_id", "stage"]),
    ],
)
@SQLDelete(sql = "UPDATE debate_verdicts SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at is null")
class DebateVerdict private constructor(
    @Id
    @Tsid
    @Column(columnDefinition = "VARCHAR(13)")
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debate_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    val debate: Debate,

    @Column(nullable = false, length = 30)
    val stage: DebateStage,

    @Column(nullable = false, length = 20)
    val status: DebateVerdictStatus,

    @Column(name = "winner_position", length = 20)
    val winnerPosition: WinnerPosition?,

    @Column(columnDefinition = "TEXT")
    val reasoning: String?,

    @Column(name = "participant_pros_score", precision = 4, scale = 1)
    val participantProsScore: BigDecimal? = null,

    @Column(name = "participant_cons_score", precision = 4, scale = 1)
    val participantConsScore: BigDecimal? = null,
) : BaseEntity() {
    companion object {
        fun success(
            debate: Debate,
            stage: DebateStage,
            winnerPosition: WinnerPosition,
            reasoning: String,
            participantProsScore: BigDecimal? = null,
            participantConsScore: BigDecimal? = null,
        ) = DebateVerdict(
            debate = debate,
            stage = stage,
            status = DebateVerdictStatus.SUCCESS,
            winnerPosition = winnerPosition,
            reasoning = reasoning,
            participantProsScore = participantProsScore,
            participantConsScore = participantConsScore,
        )

        fun failure(
            debate: Debate,
            stage: DebateStage,
        ) = DebateVerdict(
            debate = debate,
            stage = stage,
            status = DebateVerdictStatus.FAILED,
            winnerPosition = null,
            reasoning = null,
        )
    }
}
