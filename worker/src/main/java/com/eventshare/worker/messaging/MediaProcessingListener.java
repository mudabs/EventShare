package com.eventshare.worker.messaging;

import com.eventshare.worker.config.RabbitConfig;
import com.eventshare.worker.processing.MediaProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MediaProcessingListener {

    private final MediaProcessor processor;

    public MediaProcessingListener(MediaProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = {RabbitConfig.Q_PHOTO, RabbitConfig.Q_VIDEO})
    public void onMediaUploaded(MediaUploadedEvent event) {
        processor.process(event);
    }
}
