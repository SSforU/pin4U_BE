package io.github.ssforu.pin4u;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class Pin4uApplicationTests {

	@Test
	void contextLoads() {
	}

}
