package com.eventshare.api.media.messaging;

import com.eventshare.api.config.RabbitConfig;
import com.eventshare.api.media.Media;
import com.eventshare.api.media.MediaType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class MediaEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MediaEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUploaded(Media media) {
        MediaUploadedEvent event = new MediaUploadedEvent(
                media.getId(),
                media.getEventId(),
                media.getObjectKey(),
                media.getContentType(),
                media.getMediaType().name(),
                media.getSizeBytes(),
                media.getSha256(),
                media.getOriginalFilename());

        String routingKey = media.getMediaType() == MediaType.VIDEO
                ? RabbitConfig.RK_VIDEO_UPLOADED
                : RabbitConfig.RK_PHOTO_UPLOADED;

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
    }
}
