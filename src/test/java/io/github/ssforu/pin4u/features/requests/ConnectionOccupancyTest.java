package io.github.ssforu.pin4u.features.requests;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.ssforu.pin4u.TestcontainersConfiguration;
import io.github.ssforu.pin4u.features.requests.application.AiSummaryServiceImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("integration")
@SpringBootTest(properties = {
    "app.ai.enabled=false",
    "spring.datasource.hikari.maximum-pool-size=5",
    "spring.datasource.hikari.minimum-idle=2"
})
@Import(TestcontainersConfiguration.class)
class ConnectionOccupancyTest {

    @Autowired
    private AiSummaryServiceImpl aiSummaryService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("AI 비활성 상태에서 동시 10건 generateAndSaveSummary 실행 시 active connection이 pool 크기(5) 이하")
    void concurrentSummary_doesNotExhaustPool() throws Exception {
        jdbc.execute("DELETE FROM place_summaries");
        jdbc.execute("DELETE FROM request_place_aggregates");
        jdbc.execute("DELETE FROM requests");
        jdbc.execute("DELETE FROM stations");
        jdbc.execute(
                "INSERT INTO users (id, nickname, preference_text, created_at, updated_at) "
                        + "VALUES (1, 'conn-test-user', 'test', now(), now()) ON CONFLICT (id) DO NOTHING");

        jdbc.execute("""
                INSERT INTO stations (code, name, line, lat, lng)
                VALUES ('S0101', '강남', '2호선', 37.4979, 127.0276)
                """);
        for (int i = 1; i <= 10; i++) {
            jdbc.update(
                    "INSERT INTO requests (slug, station_code, owner_user_id, request_message, created_at) "
                            + "VALUES (?, 'S0101', 1, 'msg', now())",
                    "conn-test-" + i);
        }

        HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 1; i <= 10; i++) {
            final String slug = "conn-test-" + i;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                aiSummaryService.generateAndSaveSummary(slug);
            }));
        }

        latch.countDown();

        Thread.sleep(200);
        int activeConnections = pool.getActiveConnections();

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(activeConnections)
                .as("트랜잭션 3단 분리 덕분에 동시 10건 실행 중에도 active connection은 pool 크기(5) 이하")
                .isLessThanOrEqualTo(5);
    }
}
