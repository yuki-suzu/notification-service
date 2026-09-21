package com.computer_rescuer.notification.presentation.controller;

import com.computer_rescuer.notification.application.SendNotificationUseCase;
import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LINE WORKS 通知の疎通確認および手動動作テスト用 REST コントローラー。
 * <p>
 * 管理者チャンネル向けアラート通知およびシステム担当者向け障害通知を手動キックするためのエンドポイントを提供します。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/test")
@RequiredArgsConstructor
public class NotificationTestController {

    /**
     * 通知配信ユースケースの入力インターフェース
     */
    private final SendNotificationUseCase sendNotificationUseCase;

    /**
     * 管理者チャンネルへのテストアラート通知を送信します。
     *
     * @param message 送信するメッセージ本文（未指定時は既定のテスト定型文）
     * @return 実行結果を示すレスポンスエンティティ
     */
    @PostMapping("/alert")
    public ResponseEntity<String> testAlert(
            @RequestParam(required = false, defaultValue = "🤖 [NotificationService] テスト通知ですじゃ！") String message
    ) {
        log.info("テストアラート通知要求を受信: {}", message);

        NotificationCommand command = new NotificationCommand(
                NotificationChannelType.LINE_WORKS,
                Notification.DestinationType.CHANNEL,
                null,
                message
        );
        sendNotificationUseCase.send(command);

        return ResponseEntity.ok("アラートチャンネルへ送信完了じゃ！");
    }

    /**
     * システム管理者個人へのテストエラー通知を送信します。
     *
     * @param message 送信するメッセージ本文（未指定時は既定のテスト定型文）
     * @return 実行結果を示すレスポンスエンティティ
     */
    @PostMapping("/error")
    public ResponseEntity<String> testError(
            @RequestParam(required = false, defaultValue = "🚨 [NotificationService] テスト障害通知ですじゃ！") String message
    ) {
        log.info("テストエラー通知要求を受信: {}", message);

        NotificationCommand command = new NotificationCommand(
                NotificationChannelType.LINE_WORKS,
                Notification.DestinationType.USER,
                null,
                message
        );
        sendNotificationUseCase.send(command);

        return ResponseEntity.ok("システム管理者へ送信完了じゃ！");
    }
}
