package com.debate.pangyeori.terms.repository

import com.debate.pangyeori.support.containers.TestContainersInitializer
import com.debate.pangyeori.terms.domain.Terms
import com.debate.pangyeori.terms.domain.enums.TermsType
import io.kotest.matchers.collections.shouldContainExactly
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@SpringBootTest
@ContextConfiguration(initializers = [TestContainersInitializer::class])
@Transactional
class TermsRepositoryTest {

    @Autowired
    private lateinit var termsRepository: TermsRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun now() = LocalDateTime.now(ZoneOffset.UTC)

    private fun createTerms(
        type: TermsType,
        version: Int,
        effectiveAt: LocalDateTime = now().minusDays(1),
    ) = termsRepository.save(
        Terms.create(
            type = type,
            version = version,
            title = "${type.code} v$version",
            content = "${type.code} v$version 본문",
            required = type != TermsType.MARKETING,
            effectiveAt = effectiveAt,
        ),
    )

    @Test
    fun `타입별로 가장 높은 버전의 약관만 조회한다`() {
        createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            version = 1,
        )
        val latestService = createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            version = 2,
        )
        val privacy = createTerms(
            type = TermsType.PRIVACY_POLICY,
            version = 1,
        )
        val marketing = createTerms(
            type = TermsType.MARKETING,
            version = 1,
        )

        val result = termsRepository.findAllLatestEffective(
            currentTime = now(),
        )

        result.map { it.id } shouldContainExactly listOf(latestService.id, privacy.id, marketing.id)
    }

    @Test
    fun `시행일이 지나지 않은 버전은 제외하고 직전 시행 버전을 조회한다`() {
        val effectiveService = createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            version = 1,
        )
        createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            version = 2,
            effectiveAt = now().plusDays(7),
        )

        val result = termsRepository.findAllLatestEffective(
            currentTime = now(),
        )

        result.map { it.id } shouldContainExactly listOf(effectiveService.id)
    }

    @Test
    fun `삭제된 버전은 제외하고 직전 버전을 조회한다`() {
        val previousService = createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            version = 1,
        )
        val deletedService = createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            version = 2,
        )
        entityManager.flush()
        termsRepository.delete(deletedService)
        entityManager.flush()
        entityManager.clear()

        val result = termsRepository.findAllLatestEffective(
            currentTime = now(),
        )

        result.map { it.id } shouldContainExactly listOf(previousService.id)
    }
}
