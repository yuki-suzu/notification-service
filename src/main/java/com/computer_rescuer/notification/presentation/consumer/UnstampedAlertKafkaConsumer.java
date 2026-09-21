package com.computer_rescuer.notification.presentation.consumer;

import com.computer_rescuer.notification.application.NotifyUnstampedAlertUseCase;
import com.computer_rescuer.notification.application.dto.UnstampedAlertEvent;
import com.computer_rescuer.notification.presentation.consumer.handler.DltErrorHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 勤怠未打刻検知トピックを購読するインバウンドメッセージリスナー。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnstampedAlertKafkaConsumer {

    private final NotifyUnstampedAlertUseCase notifyUnstampedAlertUseCase;
    private final DltErrorHandler dltErrorHandler;

    @KafkaListener(
            topics = "${app.kafka.topics.unstamped-alert:unstamped-alert-topic}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void consume(
            @Payload @Valid UnstampedAlertEvent event, // 💡 @Valid を追加！
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("📥 [Kafka受信: 未打刻検知] Topic: {}, Partition: {}, Offset: {}, 件数: {}",
                topic, partition, offset, event.employees().size());

        notifyUnstampedAlertUseCase.execute(event);
    }

    /**
     * 未打刻アラートのリトライ上限到達時における DLT 退避ハンドラー。
     *
     * @param event 受信イベント
     * @param topic 受信元トピック
     */
    @DltHandler
    public void handleDlt(
            @Payload UnstampedAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        // ハンドラーに丸投げして安全に対処！
        dltErrorHandler.handleError(topic, event, null);
    }
}
