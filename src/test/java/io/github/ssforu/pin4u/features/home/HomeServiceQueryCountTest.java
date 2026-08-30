package io.github.ssforu.pin4u.features.home;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ssforu.pin4u.TestcontainersConfiguration;
import io.github.ssforu.pin4u.features.home.application.HomeService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("integration")
@SpringBootTest(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import(TestcontainersConfiguration.class)
class HomeServiceQueryCountTest {

    @Autowired
    private HomeService homeService;

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private JdbcTemplate jdbc;

    private Statistics stats;

    @BeforeEach
    void setUp() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        jdbc.execute("DELETE FROM request_place_aggregates");
        jdbc.execute("DELETE FROM requests");
        jdbc.execute("DELETE FROM stations");
        jdbc.execute(
                "INSERT INTO users (id, nickname, preference_text, created_at, updated_at) "
                        + "VALUES (999, 'test-user', 'test', now(), now()) ON CONFLICT (id) DO NOTHING");

        jdbc.execute("""
                INSERT INTO stations (code, name, line, lat, lng) VALUES
                ('S0101', '강남', '2호선', 37.4979, 127.0276),
                ('S0102', '역삼', '2호선', 37.5007, 127.0365)
                """);

        for (int i = 1; i <= 20; i++) {
            String slug = "test-slug-" + i;
            String stCode = i % 2 == 0 ? "S0101" : "S0102";
            jdbc.update(
                    "INSERT INTO requests (slug, station_code, owner_user_id, request_message, created_at) "
                            + "VALUES (?, ?, ?, ?, now())",
                    slug, stCode, 999L, "msg-" + i);
        }
    }

    @Test
    @DisplayName("요청 20건 조회 시 쿼리 수가 10개 이하 (N+1 없음)")
    void dashboard_20requests_queryCountBelow10() {
        stats.clear();

        homeService.dashboard(999L);

        long queryCount = stats.getQueryExecutionCount() + stats.getEntityLoadCount();
        long prepareCount = stats.getPrepareStatementCount();

        assertThat(prepareCount)
                .as("요청 20건에 대해 prepared statement 10개 이하 (N+1 방지)")
                .isLessThanOrEqualTo(10);
    }
}
