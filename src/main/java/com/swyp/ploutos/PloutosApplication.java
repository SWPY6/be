package com.swyp.ploutos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PloutosApplication {

	public static void main(String[] args) {
		SpringApplication.run(PloutosApplication.class, args);
	}

}
