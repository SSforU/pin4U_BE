package io.github.ssforu.pin4u;

import org.springframework.boot.SpringApplication;

public class TestPin4uApplication {

	public static void main(String[] args) {
		SpringApplication.from(Pin4uApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
