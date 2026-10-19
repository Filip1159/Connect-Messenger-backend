package com.example.connectmessenger.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base for tests that need the real thing: the application on a MySQL in a container, with the schema built by the
 * Flyway migrations and checked against the entities by Hibernate (`ddl-auto=validate`), so a migration that does not
 * match the mappings fails every test that extends this class. Needs Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "custom.secret=integration-secret-integration-secret-1234",
        "custom.allowed-origins=http://localhost:3000",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers(disabledWithoutDocker = true)
abstract class MySqlIntegrationTest {
    /**
     * One container for the whole test run: Spring caches the application context between test classes, and the
     * cached context keeps its connection pool, so a container that is stopped after the first class would break
     * the next one. It is stopped by Testcontainers' Ryuk when the JVM ends.
     */
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (!MYSQL.isRunning()) MYSQL.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
