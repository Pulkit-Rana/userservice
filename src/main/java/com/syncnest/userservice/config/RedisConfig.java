package com.syncnest.userservice.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
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
        // Standalone
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

        // Pool (compatible with older commons-pool2)
        GenericObjectPoolConfig<Object> pool = new GenericObjectPoolConfig<>();
        if (props.getLettuce() != null && props.getLettuce().getPool() != null) {
            var p = props.getLettuce().getPool();
            pool.setMaxTotal(p.getMaxActive());   // primitives in some Boot versions
            pool.setMaxIdle(p.getMaxIdle());
            pool.setMinIdle(p.getMinIdle());
            // DO NOT call setMaxWait(...) to avoid signature differences across pool2 versions
        } else {
            pool.setMaxTotal(32);
            pool.setMaxIdle(16);
            pool.setMinIdle(2);
            pool.setTestOnBorrow(true);
            // DO NOT call setTestOnCreate(...) for widest compatibility
            pool.setTestWhileIdle(true);
        }

        Duration cmdTimeout = (props.getTimeout() != null) ? props.getTimeout() : Duration.ofSeconds(5);
        boolean useSsl = resolveSsl(props); // handles isSsl(), getSsl():Boolean, getSsl().isEnabled()

        LettuceClientConfiguration clientConfig =
                LettucePoolingClientConfiguration.builder()
                        .clientResources(clientResources)
                        .commandTimeout(cmdTimeout)
                        .shutdownTimeout(Duration.ofSeconds(2))
                        .clientOptions(ClientOptions.builder()
                                .autoReconnect(true)
                                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT)
                                .timeoutOptions(TimeoutOptions.enabled())
                                .build())
                        .useSsl(useSsl)
                        .poolConfig(pool)
                        .build();

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
                // Case A: getSsl(): Boolean
                if (sslObj instanceof Boolean b) return b;
                // Case B: getSsl(): <SslObject> with isEnabled()
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
