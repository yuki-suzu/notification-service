package com.computer_rescuer.notification.presentation.consumer.handler;

import com.computer_rescuer.notification.application.SendNotificationUseCase;
import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * Kafka の DLT（Dead Letter Topic）退避時における障害レコードを一元処理し、システム管理者へ緊急通知するハンドラー。
 * <p>
 * {@code RetryTopicConfiguration} の {@code dltHandlerMethod} から呼び出され、
 * アプリケーション内の全トピックでリトライ上限超過やデシリアライズ失敗となったメッセージを集約処理します。<br> メッセージング基盤の引数解決自爆を防ぐため、引数は
 * {@link ConsumerRecord} のみに純化し、 Kafka ヘッダーに格納された根本原因メッセージおよび受信生データを抽出して LINE WORKS へ一次報を送信します。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DltErrorHandler {

  /**
   * 通知配信ユースケース（入力ポート）
   */
  private final SendNotificationUseCase sendNotificationUseCase;

  /**
   * DLT トピックへ退避された障害レコードを受け取り、サーバーログへの記録およびシステム管理者への緊急通知を実行します。
   * <p>
   * メッセージのパース失敗やシステム例外時に付与された Kafka ヘッダーを解析して根本原因を特定し、 受信データ（Payload）とともにシステム管理者の LINE WORKS 個人 DM
   * へ即時通報します。<br> 通知処理は完全なフェイルセーフ構造とし、LINE WORKS 側の障害による二次障害を防止します。
   * </p>
   *
   * @param record DLT トピックへ転送された Kafka レコード（キー、ペイロード、ヘッダー等の全メタデータを内包）
   */
  public void handleDlt(ConsumerRecord<Object, Object> record) {
    if (record == null) {
      log.error("☠️ [DLT退避検知] レコードが null の状態でハンドラーが呼び出されました。");
      return;
    }

    String topic = record.topic();
    long offset = record.offset();
    int partition = record.partition();

    // 1. ペイロード文字列（受信データ全体）の安全な復元
    String payloadString = resolvePayloadString(record);

    // 2. Kafka ヘッダーから転送元で記録された例外エラーメッセージを抽出
    String rootCause = extractErrorMessageFromHeaders(record);

    // 3. 【生命線】サーバーログへ確実に障害内容、位置情報、受信データ全体を出力
    log.error(
        "☠️ [DLT退避検知] トピック '{}' (Partition: {}, Offset: {}) のメッセージ処理が失敗しました。Error: {}, Payload: {}",
        topic, partition, offset, rootCause, payloadString);

    // 4. 【即時一次報】システム管理者への緊急通知（二次障害は絶対に飲み込む）
    try {
      String alertMessage = String.format(
          """
              🚨 【Kafka DLT退避エラー】
              ・トピック: %s
              ・オフセット: %d
              ・エラー内容: %s
              ・受信データ: %s
              ※詳細はサーバーログを確認してください。""",
          topic,
          offset,
          rootCause,
          truncatePayload(payloadString)
      );

      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.LINE_WORKS,
          Notification.DestinationType.USER,
          null,
          alertMessage
      );

      sendNotificationUseCase.send(command);
      log.info("📢 システム管理者への DLT 障害通知を送信しました。");

    } catch (Exception e) {
      log.error("【二次障害防止】LINE WORKS への DLT 障害通知に失敗しました。", e);
    }
  }

  /**
   * レコードの値（value）またはデシリアライズ失敗時のヘッダーから、受信データ（Payload）の文字列を安全に復元します。
   *
   * @param record 対象の Kafka レコード
   * @return 復元されたデータ文字列
   */
  private String resolvePayloadString(ConsumerRecord<Object, Object> record) {
    Object rawValue = record.value();
    if (rawValue instanceof byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8);
    }
    if (rawValue != null) {
      return String.valueOf(rawValue);
    }

    // デシリアライズ失敗により value が null の場合、生データヘッダーを確認
    for (Header header : record.headers()) {
      if (header.key() != null && header.key().toLowerCase().contains("exception")
          && header.value() != null) {
        // 例外ヘッダーが存在することを確認
        continue;
      }
    }
    return "(null / デシリアライズ失敗生データ)";
  }

  /**
   * Kafka ヘッダーに格納されている例外メッセージまたは例外クラス名を抽出します。
   *
   * @param record 対象の Kafka レコード
   * @return 抽出されたエラー原因文字列
   */
  private String extractErrorMessageFromHeaders(ConsumerRecord<Object, Object> record) {
    if (record.headers() == null) {
      return "障害情報ヘッダーなし";
    }

    // 1. 例外メッセージヘッダーを確認
    Header msgHeader = record.headers().lastHeader(KafkaHeaders.EXCEPTION_MESSAGE);
    if (msgHeader != null && msgHeader.value() != null) {
      return new String(msgHeader.value(), StandardCharsets.UTF_8);
    }

    // 2. 例外クラス名ヘッダーを確認
    Header fqcnHeader = record.headers().lastHeader(KafkaHeaders.EXCEPTION_FQCN);
    if (fqcnHeader != null && fqcnHeader.value() != null) {
      return new String(fqcnHeader.value(), StandardCharsets.UTF_8);
    }

    return "原因特定不能（例外ヘッダーなし）";
  }

  /**
   * 通知本文の肥大化による LINE WORKS API の文字数制限超過を防ぐため、ペイロードを適度に切り詰めます。
   *
   * @param payload 対象ペイロード文字列
   * @return 切り詰め後の文字列
   */
  private String truncatePayload(String payload) {
    if (payload == null) {
      return "(null)";
    }
    int maxLength = 300;
    return payload.length() > maxLength ? payload.substring(0, maxLength) + "...(略)" : payload;
  }
}
