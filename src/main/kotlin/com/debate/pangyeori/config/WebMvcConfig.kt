package com.debate.pangyeori.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig : WebMvcConfigurer {

    override fun configureApiVersioning(
        configurer: ApiVersionConfigurer,
    ) {
        configurer.usePathSegment(API_VERSION_PATH_SEGMENT_INDEX)
    }

    companion object {
        private const val API_VERSION_PATH_SEGMENT_INDEX = 1 // api/{version}/...
    }
}
