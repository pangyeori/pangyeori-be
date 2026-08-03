package com.debate.pangyeori

import com.debate.pangyeori.support.containers.TestContainersInitializer
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [TestContainersInitializer::class])
class PangyeoriApplicationTests {

    @Test
    fun contextLoads() {
    }

}
