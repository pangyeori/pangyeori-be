package com.debate.pangyeori.support.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

object TestContainers {
    val mysql: MySQLContainer = MySQLContainer(DockerImageName.parse("mysql:8.4"))
        .apply { start() }

    val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("valkey/valkey:latest"))
        .withExposedPorts(6379)
        .apply { start() }
}
