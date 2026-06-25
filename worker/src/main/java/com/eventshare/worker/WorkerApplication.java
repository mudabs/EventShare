package com.eventshare.worker;

import com.eventshare.worker.config.WorkerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * EventShare media worker. Consumes upload/export events from RabbitMQ and does
 * the heavy lifting (thumbnailing, metadata extraction) off the request path.
 */
@SpringBootApplication
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
