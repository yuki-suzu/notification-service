package com.computer_rescuer.notification.presentation.consumer;

import com.computer_rescuer.notification.application.NotifyAttendanceIrregularityUseCase;
import com.computer_rescuer.notification.application.dto.AttendanceIrregularityAlertEvent;
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
 * 勤怠不良者検知トピックを購読するインバウンドメッセージリスナー。
 * <p>
 * 勤怠管理サービスから日次バッチで配信された検知イベント（{@link AttendanceIrregularityAlertEvent}）を受信・検証し、
 * 管理者トークルーム向けのアラート通知ユースケース（{@link NotifyAttendanceIrregularityUseCase}）へディスパッチします。<br>
 * 再試行上限超過時は DLT ハンドラーへ遷移し、システム管理者へ障害を通報します。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceIrregularityKafkaConsumer {

    /**
     * 勤怠不良アラート通知ユースケース
     */
    private final NotifyAttendanceIrregularityUseCase notifyAttendanceIrregularityUseCase;

    /**
     * DLT 退避時および致命的障害ハンドラー
     */
    private final DltErrorHandler dltErrorHandler;

    /**
     * 勤怠不良者検知トピックからメッセージを非同期に購読（Consume）します。
     *
     * @param event     受信した勤怠不良検知イベント（バリデーション適用済み）
     * @param topic     受信元トピック名
     * @param partition 受信元パーティション番号
     * @param offset    メッセージオフセット
     */
    @KafkaListener(
            topics = "${app.kafka.topics.attendance-irregularity:attendance-irregularity-topic}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void consume(
            @Payload @Valid AttendanceIrregularityAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        int employeeCount = (event.employees() != null) ? event.employees().size() : 0;
        log.info("📥 [Kafka受信: 勤怠不良検知] Topic: {}, Partition: {}, Offset: {}, 対象日: {}, 件数: {}",
                topic, partition, offset, event.targetDate(), employeeCount);

        notifyAttendanceIrregularityUseCase.execute(event);
    }

    /**
     * 勤怠不良アラートのリトライ上限到達時における DLT 退避ハンドラー。
     *
     * @param event     受信失敗となったイベントペイロード
     * @param topic     受信元トピック名
     * @param throwable 発生した障害例外オブジェクト
     */
    @DltHandler
    public void handleDlt(
            @Payload AttendanceIrregularityAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Throwable throwable
    ) {
        dltErrorHandler.handleError(topic, event, throwable);
    }
}
