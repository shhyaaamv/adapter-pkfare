package com.verteil.thirdparties.adapter.pkfare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({
        "com.verteil.thirdparties.adapter.pkfare",
        "com.verteil.thirdparties.common.metric"})
public class AdapterPkfareApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdapterPkfareApplication.class, args);
    }
}
