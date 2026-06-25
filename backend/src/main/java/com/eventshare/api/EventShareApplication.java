package com.eventshare.api;

import com.eventshare.api.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * EventShare API. A modular monolith: each domain (user, event, media) is an
 * independent package with its own controller/service/repository, but all run
 * inside one deployable. Heavy/async work is delegated to the worker via RabbitMQ.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class EventShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventShareApplication.class, args);
    }
}
