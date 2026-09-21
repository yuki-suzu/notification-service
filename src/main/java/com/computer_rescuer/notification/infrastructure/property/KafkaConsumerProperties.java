package com.computer_rescuer.notification.infrastructure.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka Consumer およびリトライ処理に関する設定値を保持する不変レコード。
 * <p>
 * {@code application.yaml} の {@code app.kafka} プレフィックス配下の構造化されたプロパティ
 * （購読トピック定義、指数バックオフパラメータ）を型安全にバインドします。
 * </p>
 *
 * @param topics 購読対象となる各種 Kafka トピックの名称定義
 * @param retry  障害発生時における自動再試行およびバックオフのポリシー設定
 */
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaConsumerProperties(
        Topics topics,
        Retry retry
) {

    /**
     * 各種業務イベントを購読するための Kafka トピック名定義レコード。
     *
     * @param notification           汎用通知コマンドを受信するトピック名
     * @param unstampedAlert         勤怠管理サービスから未打刻検知イベントを受信するトピック名
     * @param unstampedDirect        勤怠管理サービスから未打刻警告イベントを受信するトピック名
     * @param attendanceIrregularity 勤怠管理サービスから勤怠不良イベントを受信するトピック名
     */
    public record Topics(
            String notification,
            String unstampedAlert,
            String unstampedDirect,
            String attendanceIrregularity
    ) {
    }

    /**
     * メッセージ配送失敗時におけるリトライおよびバックオフの動作パラメータ。
     *
     * @param maxAttempts    初回到着を含めた最大試行回数
     * @param initialDelayMs 初回リトライ実行までの待機時間（ミリ秒）
     * @param multiplier     バックオフ間隔の倍率係数
     * @param maxDelayMs     リトライ間隔の最大上限時間（ミリ秒）
     */
    public record Retry(
            int maxAttempts,
            long initialDelayMs,
            double multiplier,
            long maxDelayMs
    ) {
    }
}