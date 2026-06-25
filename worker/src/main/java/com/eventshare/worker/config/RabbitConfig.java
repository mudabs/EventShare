package com.eventshare.worker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mirrors the API's messaging topology. Declarations are idempotent: whichever
 * service starts first creates the durable exchange/queues; the other no-ops.
 * The JSON converter bean is auto-applied to the listener container by Boot.
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

    /**
     * The API stamps its own class name in the message type header. The worker has the
     * same record under a different package, so we deserialize by the inferred
     * listener-parameter type and ignore the inbound type header.
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("com.eventshare.worker.messaging");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
