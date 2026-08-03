package com.debate.pangyeori.support

import com.debate.pangyeori.support.containers.TestContainersInitializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

/**
 * REST Docs + MockMvc 통합 테스트의 공통 설정을 담당하는 추상 클래스
 *
 * 상속하면 전체 Spring 컨텍스트 기반의 MockMvc가 자동으로 준비되고,
 * 각 테스트가 끝날 때 DB 변경이 자동으로 롤백된다.
 * MySQL/Redis(Valkey)는 TestContainers 싱글턴으로 기동되어 테스트 전체에서 공유된다.
 */
@SpringBootTest
@ContextConfiguration(initializers = [TestContainersInitializer::class])
@ExtendWith(RestDocumentationExtension::class)
@Transactional
abstract class RestDocsMvcTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc(
        provider: RestDocumentationContextProvider,
    ) {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(
                MockMvcRestDocumentation.documentationConfiguration(provider)
                    .operationPreprocessors()
                    .withRequestDefaults(Preprocessors.prettyPrint())
                    .withResponseDefaults(Preprocessors.prettyPrint())
            )
            .build()
    }
}
