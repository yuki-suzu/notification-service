package com.computer_rescuer.notification.infrastructure.config;

import com.computer_rescuer.notification.infrastructure.property.KafkaConsumerProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka メッセージ受信時の一括リトライ、DLT（Dead Letter Topic）退避、およびメッセージ変換ルールを定義する構成クラス。
 * <p>
 * 各 Consumer クラスに個別のアノテーション（{@code @RetryableTopic}）を分散定義せず、
 * 本クラスにてアプリケーション全体の非同期リトライポリシーを一元管理します。<br> また、Spring Kafka 4.0 正式推奨の
 * {@link RecordMessageConverter} を登録することで、 Kafka から受信したバイト配列を各リスナーメソッドの引数型（各種 DTO）へ型安全にマッピングします。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

  private final KafkaConsumerProperties properties;

  /**
   * Kafka から受信したバイト配列を JsonMapper を用いて各リスナーメソッドの引数型（DTO）へ自動変換するメッセージコンバーター。
   * <p>
   * Spring Kafka 4.0（Jackson 3 体系）で正式導入された {@link ByteArrayJacksonJsonMessageConverter} を採用し、
   * トピックごとに異なる DTO が要求される環境下でも、各リスナーの引数型に合わせて型安全にデシリアライズします。
   * </p>
   *
   * @param jsonMapper Spring コンテキストに登録されている共通の {@link JsonMapper}
   * @return 構築された {@link RecordMessageConverter}
   */
  @Bean
  public RecordMessageConverter recordMessageConverter(JsonMapper jsonMapper) {
    return new ByteArrayJacksonJsonMessageConverter(jsonMapper);
  }

  /**
   * 指定されたトピック群に対して、指数バックオフによる自動再試行および DLT 退避パイプラインを登録します。
   * <p>
   * Spring Boot によって自動構成された {@link KafkaOperations} をインジェクションし、 障害メッセージの再送および DLT 転送に利用します。<br>
   * バリデーションエラー（{@link MethodArgumentNotValidException}）はリトライ不要な恒久障害として即座に DLT へ転送されます。
   * </p>
   *
   * @param kafkaTemplate Spring Boot 自動構成から提供される Kafka 送信テンプレート
   * @return 構築された {@link RetryTopicConfiguration} Bean
   */
  @Bean
  public RetryTopicConfiguration notificationRetryTopicConfiguration(
      KafkaOperations<Object, Object> kafkaTemplate
  ) {
    return RetryTopicConfigurationBuilder
        .newInstance()
        .maxAttempts(properties.retry().maxAttempts())
        .exponentialBackoff(
            properties.retry().initialDelayMs(),
            properties.retry().multiplier(),
            properties.retry().maxDelayMs()
        )
        // バリデーションエラーはリトライせず即座にDLTへ転送
        .notRetryOn(MethodArgumentNotValidException.class)
        .autoCreateTopics(true, 1, (short) 1)
        .includeTopics(List.of(
            properties.topics().notification(),
            properties.topics().unstampedAlert(),
            properties.topics().unstampedDirect(),
            properties.topics().attendanceIrregularity()
        ))
        .dltHandlerMethod("dltErrorHandler", "handleDlt")
        .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
        .create(kafkaTemplate);
  }
}
