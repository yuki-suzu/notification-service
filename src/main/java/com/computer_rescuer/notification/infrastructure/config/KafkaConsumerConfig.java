package com.computer_rescuer.notification.infrastructure.config;

import com.computer_rescuer.notification.infrastructure.property.KafkaConsumerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;

import java.util.List;

/**
 * Kafka メッセージ受信および障害リトライパイプラインを定義する構成クラス。
 * <p>
 * アプリケーション設定（{@link KafkaConsumerProperties}）から注入された環境プロパティに基づき、
 * 指数バックオフによる自動再試行および致命的障害時の DLT（Dead Letter Topic）退避ルールを構成します。<br>
 * Spring Boot の自動構成（{@code KafkaAutoConfiguration}）との初期化タイミング競合を回避するため、
 * リトライメッセージ転送に使用する {@link KafkaOperations} は {@link Lazy} アノテーションを通じて遅延注入されます。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaConsumerProperties properties;

    /**
     * 通知トピック向けのリトライおよび DLT パイプラインを登録します。
     * <p>
     * メッセージ受信時に例外が発生した場合、本 Bean で定義されたバックオフ間隔で
     * 再試行トピックへ転送されます。最大試行回数を超過した場合は自動的に DLT へ退避されます。<br>
     * {@link Lazy} によりプロキシ経由で {@link KafkaOperations} を解決するため、
     * 自動構成 Bean の生成順序に依存せず安全に初期化が完了します。
     * </p>
     *
     * @param kafkaTemplate リトライメッセージおよび DLT 転送に使用する {@link KafkaOperations}（遅延注入プロキシ）
     * @return 構築された {@link RetryTopicConfiguration}
     */
    @Bean
    public RetryTopicConfiguration notificationRetryTopicConfiguration(
            @Lazy KafkaOperations<?, ?> kafkaTemplate // 💡 @Lazy を添えて順序問題を一撃で解決！
    ) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                .maxAttempts(properties.retry().maxAttempts())
                .exponentialBackoff(
                        properties.retry().initialDelayMs(),
                        properties.retry().multiplier(),
                        properties.retry().maxDelayMs()
                )
                // バリデーションエラーはリトライせず即座にDLTへ転送！
                .notRetryOn(org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class)
                .autoCreateTopics(true, 1, (short) 1)
                .includeTopics(List.of(
                        properties.topics().notification(),
                        properties.topics().unstampedAlert()
                ))
                .dltHandlerMethod("notificationKafkaConsumer", "handleDlt")
                .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
                .create(kafkaTemplate);
    }
}
