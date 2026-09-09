package com.debate.pangyeori.config

import com.debate.pangyeori.debate.stream.publisher.RedisDebateStreamPublisher
import com.debate.pangyeori.debate.stream.subscriber.DebateStreamRedisSubscriber
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
class RedisSubscriptionConfig {

    @Bean
    fun debateStreamListenerContainer(
        connectionFactory: RedisConnectionFactory,
        debateStreamRedisSubscriber: DebateStreamRedisSubscriber,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(
            debateStreamRedisSubscriber,
            ChannelTopic(RedisDebateStreamPublisher.CHANNEL),
        )
        return container
    }
}
