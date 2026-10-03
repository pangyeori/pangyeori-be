package com.debate.pangyeori.config

import com.debate.pangyeori.debate.websocket.PingWebSocketHandlerDecorator
import com.debate.pangyeori.debate.websocket.interceptor.StompChannelInterceptor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.TaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompChannelInterceptor: StompChannelInterceptor,
    @param:Value("\${cors.allowed-origins}") private val allowedOrigins: List<String>,
    @param:Qualifier(SchedulingConfig.DEBATE_CONNECTION_SCHEDULER) private val taskScheduler: TaskScheduler,
) : WebSocketMessageBrokerConfigurer {

    override fun configureWebSocketTransport(
        registration: WebSocketTransportRegistration,
    ) {
        registration.addDecoratorFactory { handler ->
            PingWebSocketHandlerDecorator(handler, taskScheduler)
        }
    }

    override fun registerStompEndpoints(
        registry: StompEndpointRegistry,
    ) {
        registry.addEndpoint(ENDPOINT)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())
    }

    override fun configureMessageBroker(
        registry: MessageBrokerRegistry,
    ) {
        registry.enableSimpleBroker(SUBSCRIBE_PREFIX)
        registry.setApplicationDestinationPrefixes(PUBLISH_PREFIX)
    }

    override fun configureClientInboundChannel(
        registration: ChannelRegistration,
    ) {
        registration.interceptors(stompChannelInterceptor)
    }

    companion object {
        const val ENDPOINT = "/ws"
        const val SUBSCRIBE_PREFIX = "/sub"
        const val PUBLISH_PREFIX = "/pub"
    }
}
