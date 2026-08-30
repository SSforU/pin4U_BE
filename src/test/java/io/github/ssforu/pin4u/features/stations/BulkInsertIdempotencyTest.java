package io.github.ssforu.pin4u.features.stations;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ssforu.pin4u.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BulkInsertIdempotencyTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("같은 code로 2회 upsert 시 행 수 불변 + 값 갱신")
    void upsert_sameCode_twice_rowCountUnchanged_valuesUpdated() {
        jdbc.execute("DELETE FROM request_place_aggregates");
        jdbc.execute("DELETE FROM requests");
        jdbc.execute("DELETE FROM stations");

        String upsertSql = """
                INSERT INTO stations (code, name, line, lat, lng)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    name = EXCLUDED.name,
                    line = EXCLUDED.line,
                    lat  = EXCLUDED.lat,
                    lng  = EXCLUDED.lng
                """;

        jdbc.update(upsertSql, "S0101", "강남", "2호선", 37.4979, 127.0276);
        jdbc.update(upsertSql, "S0102", "역삼", "2호선", 37.5007, 127.0365);
        jdbc.update(upsertSql, "S0103", "선릉", "2호선", 37.5045, 127.0489);

        int countAfterFirst = jdbc.queryForObject("SELECT count(*) FROM stations", Integer.class);
        assertThat(countAfterFirst).isEqualTo(3);

        jdbc.update(upsertSql, "S0101", "강남역(수정)", "2호선", 37.4980, 127.0277);
        jdbc.update(upsertSql, "S0102", "역삼역(수정)", "2호선", 37.5008, 127.0366);
        jdbc.update(upsertSql, "S0103", "선릉역(수정)", "2호선", 37.5046, 127.0490);

        int countAfterSecond = jdbc.queryForObject("SELECT count(*) FROM stations", Integer.class);
        assertThat(countAfterSecond)
                .as("2회 upsert 후에도 행 수는 동일해야 한다")
                .isEqualTo(3);

        String updatedName = jdbc.queryForObject(
                "SELECT name FROM stations WHERE code = 'S0101'", String.class);
        assertThat(updatedName)
                .as("ON CONFLICT DO UPDATE로 값이 갱신되어야 한다")
                .isEqualTo("강남역(수정)");
    }
}
