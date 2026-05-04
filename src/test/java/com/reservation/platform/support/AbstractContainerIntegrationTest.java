package com.reservation.platform.support;

import com.reservation.platform.booking.infrastructure.BookingJpaRepository;
import com.reservation.platform.order.infrastructure.OrderJpaRepository;
import com.reservation.platform.payment.infrastructure.PaymentJpaRepository;
import com.reservation.platform.payment.infrastructure.PaymentTransactionJpaRepository;
import com.reservation.platform.point.infrastructure.PointJpaRepository;
import com.reservation.platform.point.infrastructure.PointTransactionJpaRepository;
import com.reservation.platform.product.infrastructure.ProductJpaRepository;
import com.reservation.platform.stock.infrastructure.StockJpaRepository;
import com.reservation.platform.user.infrastructure.UserJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(AbstractContainerIntegrationTest.TestRedissonConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.com.reservation.platform=INFO",
        "logging.level.org.hibernate.SQL=WARN"
})
public abstract class AbstractContainerIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("reservation_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestRedissonConfig {

        @Bean(destroyMethod = "shutdown")
        @Primary
        RedissonClient redissonClient() {
            Config config = new Config();
            config.useSingleServer()
                    .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
            return Redisson.create(config);
        }
    }

    @Autowired
    protected UserJpaRepository userJpaRepository;

    @Autowired
    protected ProductJpaRepository productJpaRepository;

    @Autowired
    protected StockJpaRepository stockJpaRepository;

    @Autowired
    protected PointJpaRepository pointJpaRepository;

    @Autowired
    protected OrderJpaRepository orderJpaRepository;

    @Autowired
    protected BookingJpaRepository bookingJpaRepository;

    @Autowired
    protected PaymentJpaRepository paymentJpaRepository;

    @Autowired
    protected PaymentTransactionJpaRepository paymentTransactionJpaRepository;

    @Autowired
    protected PointTransactionJpaRepository pointTransactionJpaRepository;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected EntityManager entityManager;

    @BeforeEach
    void cleanData() {
        entityManager.clear();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        pointTransactionJpaRepository.deleteAllInBatch();
        paymentTransactionJpaRepository.deleteAllInBatch();
        bookingJpaRepository.deleteAllInBatch();
        paymentJpaRepository.deleteAllInBatch();
        orderJpaRepository.deleteAllInBatch();
        pointJpaRepository.deleteAllInBatch();
        stockJpaRepository.deleteAllInBatch();
        productJpaRepository.deleteAllInBatch();
        userJpaRepository.deleteAllInBatch();
        entityManager.clear();
    }
}
