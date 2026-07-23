package com.eventshare.api;

import com.eventshare.api.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * EventShare API. A modular monolith: each domain (user, event, media) is an
 * independent package with its own controller/service/repository, all running
 * inside one deployable. Media processing (thumbnails, video posters) runs
 * in-process via a status-driven scheduler rather than a separate worker.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class EventShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventShareApplication.class, args);
    }
}
