package com.computer_rescuer.notification.presentation.consumer;

import com.computer_rescuer.notification.application.NotifyAttendanceIrregularityUseCase;
import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 勤怠異常・月次サマリートピックを購読するインバウンドメッセージリスナー。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceIrregularityKafkaConsumer {

  private final NotifyAttendanceIrregularityUseCase notifyAttendanceIrregularityUseCase;

  /**
   * 勤怠異常トピックからメッセージを非同期に購読（Consume）します。
   *
   * @param event     受信した月次勤怠サマリエベント（バリデーション適用済み）
   * @param topic     受信元トピック名
   * @param partition 受信元パーティション番号
   * @param offset    メッセージオフセット
   */
  @KafkaListener(
      topics = "${app.kafka.topics.attendance-irregularity:attendance-irregularity-topic}",
      groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
  )
  public void consume(
      @Payload @Valid AttendanceIrregularityEvent event,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset
  ) {
    int count = (event.employees() != null) ? event.employees().size() : 0;
    log.info(
        "📥 [Kafka受信: 月次勤怠サマリ] Topic: {}, Partition: {}, Offset: {}, 対象月: {}, 対象者数: {} 名",
        topic, partition, offset, event.procMonth(), count);

    notifyAttendanceIrregularityUseCase.execute(event);
  }
}
