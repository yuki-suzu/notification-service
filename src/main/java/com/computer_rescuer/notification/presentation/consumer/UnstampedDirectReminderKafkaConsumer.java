package com.computer_rescuer.notification.presentation.consumer;

import com.computer_rescuer.notification.application.SendUnstampedDirectReminderUseCase;
import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent;
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
 * 勤怠未打刻者の個別ダイレクト通知トピックを購読するインバウンドメッセージリスナー。
 * <p>
 * 受信したイベント（対象従業員リスト）を検証後、{@link SendUnstampedDirectReminderUseCase} へディスパッチします。<br>
 * メッセージング障害や再試行上限超過時は DLT ハンドラーへ遷移し、{@link DltErrorHandler} を通じてシステム管理者に障害を通報します。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnstampedDirectReminderKafkaConsumer {

    /**
     * 未打刻者本人向けダイレクト通知ユースケース
     */
    private final SendUnstampedDirectReminderUseCase sendUnstampedDirectReminderUseCase;

    /**
     * DLT 退避時および致命的障害ハンドラー
     */
    private final DltErrorHandler dltErrorHandler;

    /**
     * 未打刻者ダイレクト通知トピックからメッセージを非同期に購読（Consume）します。
     * <p>
     * ペイロードのリスト検証を実施した上で、対象従業員群への個別 DM 配信ユースケースをキックします。
     * </p>
     *
     * @param event     受信した個別通知イベント（バリデーション適用済み）
     * @param topic     受信元トピック名
     * @param partition 受信元パーティション番号
     * @param offset    メッセージオフセット
     */
    @KafkaListener(
            topics = "${app.kafka.topics.unstamped-direct:unstamped-direct-topic}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void consume(
            @Payload @Valid UnstampedDirectReminderEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        // 💡 employees の件数を安全に取得してログ出力！
        int employeeCount = (event.employees() != null) ? event.employees().size() : 0;

        log.info("📥 [Kafka受信: 未打刻DM] Topic: {}, Partition: {}, Offset: {}, 配信対象者数: {} 名",
                topic, partition, offset, employeeCount);

        sendUnstampedDirectReminderUseCase.execute(event);
    }

    /**
     * 未打刻個別 DM 配信のリトライ上限到達時における DLT 退避ハンドラー。
     * <p>
     * 指数バックオフによる再試行がすべて失敗した場合に呼び出され、
     * システム管理者への緊急障害通知を実行します。
     * </p>
     *
     * @param event     受信失敗となったイベントペイロード
     * @param topic     受信元トピック名
     * @param throwable 発生した障害例外オブジェクト
     */
    @DltHandler
    public void handleDlt(
            @Payload UnstampedDirectReminderEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Throwable throwable
    ) {
        dltErrorHandler.handleError(topic, event, throwable);
    }
}
