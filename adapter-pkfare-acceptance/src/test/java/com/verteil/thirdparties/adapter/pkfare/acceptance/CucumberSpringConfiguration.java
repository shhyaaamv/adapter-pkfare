package com.verteil.thirdparties.adapter.pkfare.acceptance;

import com.verteil.thirdparties.adapter.pkfare.acceptance.config.TestConfig;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = TestConfig.class)
public class CucumberSpringConfiguration {
}
