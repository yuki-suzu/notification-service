package com.computer_rescuer.notification.presentation.consumer.handler;

import com.computer_rescuer.notification.application.SendNotificationUseCase;
import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Kafka の DLT（Dead Letter Topic）退避時および致命的障害発生時にシステム管理者への通知を司るエラーハンドラー。
 * <p>
 * attendance-management のエラー通知機構を継承し、以下の二段構えで障害に対処します：<br>
 * 1. サーバーログへの詳細なエラー出力（監視・追跡用）<br>
 * 2. システム管理者個人への LINE WORKS ダイレクト通知（即時一次報）<br>
 * LINE WORKS 自体の全館障害やネットワーク断による「二次障害（巻き添え停止）」を防ぐため、
 * 通知送信処理はすべて try-catch で安全にカプセル化されます。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DltErrorHandler {

    private final SendNotificationUseCase sendNotificationUseCase;

    /**
     * DLT に退避された障害メッセージを受け取り、ログ記録およびシステム管理者への緊急通知を実行します。
     * <p>
     * エラーの根本原因メッセージを抽出して通知本文を生成し、システム管理者の LINE WORKS DM へディスパッチします。
     * 外部 API 通信に失敗した場合でも例外は再スローされず、二次障害ログの出力に留めます。
     * </p>
     *
     * @param topic     障害メッセージが退避された DLT トピック名
     * @param payload   処理失敗となったメッセージペイロード（コマンドまたはイベントオブジェクト）
     * @param throwable 発生した例外オブジェクト（null 許容）
     */
    public void handleError(String topic, Object payload, Throwable throwable) {
        // 1. 【生命線】サーバーログへ確実に障害内容を出力
        log.error("☠️ [DLT退避検知] トピック '{}' のメッセージ処理がリトライ上限に到達しました。Payload: {}",
                topic, payload, throwable);

        // 2. 【ベストエフォート】システム管理者への緊急通知（二次障害は絶対に飲み込む）
        try {
            String rootCause = extractRootCauseMessage(throwable);

            String alertMessage = String.format(
                    "🚨 【Kafka DLT退避エラー】\n" +
                            "・トピック: %s\n" +
                            "・エラー内容: %s\n" +
                            "※詳細はサーバーログまたは kafka-ui を確認してください。",
                    topic,
                    rootCause
            );

            // targetId を null にすることで、インフラ層の既定値（systemManagerId）へ個人DM送信される
            NotificationCommand command = new NotificationCommand(
                    NotificationChannelType.LINE_WORKS,
                    Notification.DestinationType.USER,
                    null,
                    alertMessage
            );

            sendNotificationUseCase.send(command);
            log.info("📢 システム管理者への DLT 障害通知を送信しました。");

        } catch (Exception e) {
            // LINE WORKS 側がダウンしていた場合でも、Kafka Listener コンテナをクラッシュさせない安全弁
            log.error("【二次障害】LINE WORKS への DLT 障害通知に失敗しました。", e);
        }
    }

    /**
     * 例外チェーンを辿り、最深層の根本原因（Root Cause）メッセージを安全に抽出します。
     *
     * @param throwable 抽出元の例外オブジェクト
     * @return 根本原因メッセージ（取得できない場合はデフォルト文言）
     */
    private String extractRootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "原因特定不能（例外オブジェクトなし）";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
