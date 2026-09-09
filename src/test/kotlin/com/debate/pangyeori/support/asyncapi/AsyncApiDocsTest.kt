package com.debate.pangyeori.support.asyncapi

import com.debate.pangyeori.support.asyncapi.dsl.SseDocumentationDsl
import com.debate.pangyeori.support.containers.TestContainersInitializer
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 비동기 메시징 통합 테스트 + AsyncAPI 문서화의 공통 베이스.
 *
 * `RestDocsMvcTest`와 달리 실제 서블릿 컨테이너(`RANDOM_PORT`)로 앱을 띄운다. MockMvc는 SSE 스트리밍을
 * 제대로 다루지 못하고, 이벤트가 `@TransactionalEventListener(AFTER_COMMIT)` → Redis pub/sub를 거쳐
 * 전파되므로 트리거가 실제로 커밋되어야 하기 때문이다. 같은 이유로 `@Transactional` 롤백을 쓰지 못하고,
 * 각 테스트가 끝나면 [cleanUp]이 DB 테이블과 Redis를 직접 비운다.
 *
 * 필요한 빈은 서브클래스에 `@Autowired` 필드로 직접 선언한다. 통합 테스트는 plain JUnit 5(`@Test`)로 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [TestContainersInitializer::class])
abstract class AsyncApiDocsTest {

    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisConnectionFactory: RedisConnectionFactory

    private val httpClient = HttpClient.newHttpClient()

    protected fun documentSse(
        identifier: String,
        block: SseDocumentationDsl.() -> Unit,
    ) {
        SseDocumentationDsl(
            identifier = identifier,
            baseUrl = "http://localhost:$port",
            objectMapper = objectMapper,
        ).apply(block).execute()
    }

    @AfterEach
    fun cleanUp() {
        val tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
            String::class.java,
        )
        tables.forEach { table ->
            jdbcTemplate.execute("TRUNCATE TABLE `$table`")
        }
        redisConnectionFactory.connection.use { it.serverCommands().flushDb() }
    }
}
