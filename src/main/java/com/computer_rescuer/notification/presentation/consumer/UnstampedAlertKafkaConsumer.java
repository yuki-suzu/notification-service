package com.computer_rescuer.notification.presentation.consumer;

import com.computer_rescuer.notification.application.NotifyUnstampedAlertUseCase;
import com.computer_rescuer.notification.application.dto.UnstampedAlertEvent;
import com.computer_rescuer.notification.presentation.consumer.handler.DltErrorHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 勤怠未打刻検知トピックを購読するインバウンドメッセージリスナー。
 * <p>
 * 勤怠管理サービスから発行された未打刻検知イベントを受信し、バリデーション実施後に
 * アプリケーション層のユースケース（{@link NotifyUnstampedAlertUseCase}）へディスパッチします。<br> 再試行上限超過時は DLT
 * ハンドラーへ遷移し、システム管理者への障害通報を行います。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnstampedAlertKafkaConsumer {

  private final NotifyUnstampedAlertUseCase notifyUnstampedAlertUseCase;
  private final DltErrorHandler dltErrorHandler;

  /**
   * 未打刻検知イベントを購読し、アラート通知ユースケースを実行します。
   *
   * @param event     受信した未打刻検知イベント（バリデーション適用済み）
   * @param topic     受信元トピック名
   * @param partition 受信元パーティション番号
   * @param offset    メッセージオフセット
   */
  @KafkaListener(
      topics = "${app.kafka.topics.unstamped-alert:unstamped-alert-topic}",
      groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
  )
  public void consume(
      @Payload @Valid UnstampedAlertEvent event,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset
  ) {
    int employeeCount = (event.employees() != null) ? event.employees().size() : 0;
    log.info("📥 [Kafka受信: 未打刻検知] Topic: {}, Partition: {}, Offset: {}, 件数: {}",
        topic, partition, offset, employeeCount);

    notifyUnstampedAlertUseCase.execute(event);
  }
}
