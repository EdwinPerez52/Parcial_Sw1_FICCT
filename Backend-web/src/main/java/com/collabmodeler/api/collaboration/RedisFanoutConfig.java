package com.collabmodeler.api.collaboration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "app.collaboration.redis-enabled", havingValue = "true", matchIfMissing = true)
public class RedisFanoutConfig {
    @Bean
    RedisMessageListenerContainer collaborationRedisContainer(RedisConnectionFactory connections,
                                                               CollaborationEventPublisher publisher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.addMessageListener((message, pattern) -> publisher.receive(new String(message.getBody(), StandardCharsets.UTF_8)),
            new ChannelTopic(CollaborationEventPublisher.REDIS_CHANNEL));
        return container;
    }
}
