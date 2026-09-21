package com.computer_rescuer.notification.presentation.consumer;

import com.computer_rescuer.notification.application.SendNotificationUseCase;
import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.presentation.consumer.handler.DltErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 汎用通知要求トピックを購読するインバウンドメッセージリスナー。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralNotificationKafkaConsumer {

    private final SendNotificationUseCase sendNotificationUseCase;
    private final DltErrorHandler dltErrorHandler;

    /**
     * 汎用通知要求メッセージを受信し、ユースケースへディスパッチします。
     *
     * @param event     受信コマンド
     * @param topic     受信元トピック名
     * @param partition パーティション番号
     * @param offset    オフセット
     */
    @KafkaListener(
            topics = "${app.kafka.topics.notification:notification-topic}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void consume(
            @Payload NotificationCommand event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("📥 [Kafka受信: 汎用通知] Topic: {}, Partition: {}, Offset: {}, Payload: {}",
                topic, partition, offset, event);

        sendNotificationUseCase.send(event);
    }

    /**
     * 汎用通知のリトライ上限到達時における DLT 退避ハンドラー。
     *
     * @param event     失敗メッセージ
     * @param topic     DLTトピック名
     * @param throwable 発生した障害例外オブジェクト
     */
    @DltHandler
    public void handleDlt(
            @Payload NotificationCommand event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Throwable throwable
    ) {
        dltErrorHandler.handleError(topic, event, throwable);
    }
}
