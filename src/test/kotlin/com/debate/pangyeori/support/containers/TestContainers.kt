package com.debate.pangyeori.support.containers

import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

object TestContainers {
    val mysql: MySQLContainer = MySQLContainer(DockerImageName.parse("mysql:8.4"))
        .apply { start() }
}
