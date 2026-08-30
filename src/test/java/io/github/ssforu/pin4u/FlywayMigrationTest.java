package io.github.ssforu.pin4u;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void allMigrations_applySuccessfully_noPendingOrFailed() {
        var info = flyway.info();
        MigrationInfo[] all = info.all();

        assertThat(all).as("At least one migration should exist").isNotEmpty();

        for (MigrationInfo migration : all) {
            assertThat(migration.getState())
                    .as("Migration %s should be SUCCESS, got %s", migration.getVersion(), migration.getState())
                    .isEqualTo(MigrationState.SUCCESS);
        }

        assertThat(info.pending())
                .as("No pending migrations should remain")
                .isEmpty();

        assertThat(info.current()).isNotNull();
        assertThat(info.current().getState()).isEqualTo(MigrationState.SUCCESS);
    }
}
