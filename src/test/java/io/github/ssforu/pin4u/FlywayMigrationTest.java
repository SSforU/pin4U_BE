package io.github.ssforu.pin4u;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void allMigrations_applySuccessfully() {
        var info = flyway.info();
        MigrationInfo[] applied = info.applied();

        assertThat(applied).isNotEmpty();

        for (MigrationInfo migration : applied) {
            assertThat(migration.getState().isFailed())
                    .as("Migration %s should not be failed", migration.getVersion())
                    .isFalse();
        }

        assertThat(info.current()).isNotNull();
        assertThat(info.current().getState().isFailed()).isFalse();
    }
}
