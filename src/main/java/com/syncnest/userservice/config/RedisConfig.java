package com.syncnest.userservice.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulConnection; // <-- IMPORTANT
import io.lettuce.core.resource.DefaultClientResources;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    DefaultClientResources lettuceClientResources() {
        return DefaultClientResources.create();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties props,
                                                           DefaultClientResources clientResources) {

        // --- Standalone ---
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        standalone.setHostName(props.getHost());
        standalone.setPort(props.getPort());
        standalone.setDatabase(props.getDatabase());
        if (StringUtils.hasText(props.getUsername())) {
            standalone.setUsername(props.getUsername());
        }
        if (StringUtils.hasText(props.getPassword())) {
            standalone.setPassword(RedisPassword.of(props.getPassword()));
        }

        // --- Pool: MUST be typed to StatefulConnection<?, ?> ---
        GenericObjectPoolConfig<StatefulConnection<?, ?>> pool = new GenericObjectPoolConfig<>();
        if (props.getLettuce() != null && props.getLettuce().getPool() != null) {
            var p = props.getLettuce().getPool();
            // Depending on Spring Boot version, only one of these may exist.
            // Prefer getMaxActive() if present; otherwise getMaxTotal().
            try {
                pool.setMaxTotal(p.getMaxActive());
            } catch (NoSuchMethodError e) {
                // For newer Boot versions where getMaxActive() was replaced:
                try {
                    var m = p.getClass().getMethod("getMaxTotal");
                    Object v = m.invoke(p);
                    if (v instanceof Integer) pool.setMaxTotal((Integer) v);
                } catch (Exception ignore) { /* fall back to defaults */ }
            }
            pool.setMaxIdle(p.getMaxIdle());
            pool.setMinIdle(p.getMinIdle());
            // avoid setMaxWait due to signature differences across commons-pool2
        } else {
            pool.setMaxTotal(32);
            pool.setMaxIdle(16);
            pool.setMinIdle(2);
            pool.setTestOnBorrow(true);
            pool.setTestWhileIdle(true);
        }

        // --- Timeouts / SSL ---
        Duration cmdTimeout = (props.getTimeout() != null) ? props.getTimeout() : Duration.ofSeconds(5);
        boolean useSsl = resolveSsl(props);

        // --- Lettuce client configuration builder ---
        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
                LettucePoolingClientConfiguration.builder()
                        .clientResources(clientResources)
                        .commandTimeout(cmdTimeout)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .clientOptions(ClientOptions.builder()
                                .autoReconnect(true)
                                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT)
                                .timeoutOptions(TimeoutOptions.enabled())
                                .build())
                        .poolConfig(pool);

        // Your Lettuce builder exposes only the no-arg method; call conditionally.
        if (useSsl) {
            builder.useSsl(); // no-arg
        }

        LettuceClientConfiguration clientConfig = builder.build();
        return new LettuceConnectionFactory(standalone, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory cf) {
        StringRedisTemplate tpl = new StringRedisTemplate();
        tpl.setConnectionFactory(cf);
        StringRedisSerializer s = new StringRedisSerializer();
        tpl.setKeySerializer(s);
        tpl.setValueSerializer(s);
        tpl.setHashKeySerializer(s);
        tpl.setHashValueSerializer(s);
        tpl.afterPropertiesSet();
        return tpl;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory cf) {
        RedisTemplate<String, Object> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(cf);
        StringRedisSerializer keySer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valSer = new GenericJackson2JsonRedisSerializer();
        tpl.setKeySerializer(keySer);
        tpl.setHashKeySerializer(keySer);
        tpl.setValueSerializer(valSer);
        tpl.setHashValueSerializer(valSer);
        tpl.afterPropertiesSet();
        return tpl;
    }

    /**
     * Supports:
     *  - boolean RedisProperties#isSsl()
     *  - java.lang.Boolean RedisProperties#getSsl()
     *  - <SslObj> RedisProperties#getSsl() where SslObj#isEnabled() -> boolean
     */
    private static boolean resolveSsl(RedisProperties props) {
        try {
            Method mIs = RedisProperties.class.getMethod("isSsl");
            Object r = mIs.invoke(props);
            return (r instanceof Boolean) && (Boolean) r;
        } catch (NoSuchMethodException ignore) {
            try {
                Method mGet = RedisProperties.class.getMethod("getSsl");
                Object sslObj = mGet.invoke(props);
                if (sslObj == null) return false;
                if (sslObj instanceof Boolean b) return b;
                try {
                    Method mEnabled = sslObj.getClass().getMethod("isEnabled");
                    Object en = mEnabled.invoke(sslObj);
                    return (en instanceof Boolean) && (Boolean) en;
                } catch (NoSuchMethodException e2) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
