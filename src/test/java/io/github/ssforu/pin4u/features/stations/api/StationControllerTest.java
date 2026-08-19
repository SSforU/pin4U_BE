package io.github.ssforu.pin4u.features.stations.api;

import io.github.ssforu.pin4u.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StationControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void search_returnsOk() throws Exception {
        mvc.perform(get("/api/stations/search").param("q", "강남"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("success"));
    }

    @Test
    void search_missingParam_returns400() throws Exception {
        mvc.perform(get("/api/stations/search"))
                .andExpect(status().isBadRequest());
    }
}
