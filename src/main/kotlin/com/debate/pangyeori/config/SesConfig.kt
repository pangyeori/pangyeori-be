package com.debate.pangyeori.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client

@Configuration
class SesConfig {

    @Bean
    fun sesV2Client(
        @Value("\${aws.region}") region: String,
    ): SesV2Client = SesV2Client.builder()
        .region(Region.of(region))
        .build()
}
