package com.eventshare.api.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Async media pipeline topology.
 *
 * <p>A topic exchange routes upload/export events to durable work queues. Every
 * work queue dead-letters to a fanout DLX so poison messages are retained for
 * inspection instead of being lost. The exchange/queue/routing-key names are the
 * contract shared with the worker service.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "eventshare.media";
    public static final String DLX = "eventshare.media.dlx";

    public static final String RK_PHOTO_UPLOADED = "media.photo.uploaded";
    public static final String RK_VIDEO_UPLOADED = "media.video.uploaded";
    public static final String RK_EXPORT_REQUESTED = "media.export.requested";

    public static final String Q_PHOTO = "media.photo.process";
    public static final String Q_VIDEO = "media.video.process";
    public static final String Q_EXPORT = "media.export.process";
    public static final String Q_DLQ = "eventshare.media.dlq";

    @Bean
    public TopicExchange mediaExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(Q_DLQ).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange());
    }

    @Bean
    public Queue photoQueue() {
        return QueueBuilder.durable(Q_PHOTO).deadLetterExchange(DLX).build();
    }

    @Bean
    public Queue videoQueue() {
        return QueueBuilder.durable(Q_VIDEO).deadLetterExchange(DLX).build();
    }

    @Bean
    public Queue exportQueue() {
        return QueueBuilder.durable(Q_EXPORT).deadLetterExchange(DLX).build();
    }

    @Bean
    public Binding photoBinding() {
        return BindingBuilder.bind(photoQueue()).to(mediaExchange()).with(RK_PHOTO_UPLOADED);
    }

    @Bean
    public Binding videoBinding() {
        return BindingBuilder.bind(videoQueue()).to(mediaExchange()).with(RK_VIDEO_UPLOADED);
    }

    @Bean
    public Binding exportBinding() {
        return BindingBuilder.bind(exportQueue()).to(mediaExchange()).with(RK_EXPORT_REQUESTED);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setExchange(EXCHANGE);
        return template;
    }
}
