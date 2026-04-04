package io.quarkiverse.jimmer.it;

import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class RedisTestResource implements QuarkusTestResourceLifecycleManager {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @Override
    public Map<String, String> start() {
        REDIS.start();
        String redisUrl = "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
        return Map.of(
                "quarkus.redis.hosts", redisUrl,
                "quarkus.redisson.single-server-config.address", redisUrl);
    }

    @Override
    public void stop() {
        REDIS.stop();
    }
}
