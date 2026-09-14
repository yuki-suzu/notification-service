package com.computer_rescuer.notification.adapter.in.web;

import com.computer_rescuer.notification.application.port.in.SendNotificationUseCase;
import com.computer_rescuer.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LINE WORKS 通知の疎通確認および手動テスト用RESTコントローラー。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/test")
@RequiredArgsConstructor
public class NotificationTestController {

    private final SendNotificationUseCase sendNotificationUseCase;

    /**
     * 管理者チャンネルへのテストアラート通知を送信します。
     *
     * @param message 送信する本文（省略時はテスト定型文）
     * @return 送信結果レスポンス
     */
    @PostMapping("/alert")
    public ResponseEntity<String> testAlert(
            @RequestParam(required = false, defaultValue = "🤖 [NotificationService] テスト通知ですじゃ！") String message
    ) {
        log.info("テストアラート通知要求を受信: {}", message);
        sendNotificationUseCase.send(Notification.ofLineWorksAlert(message));
        return ResponseEntity.ok("アラートチャンネルへ送信完了じゃ！");
    }

    /**
     * システム管理者へのテストエラー通知を送信します。
     *
     * @param message 送信する本文（省略時はテスト定型文）
     * @return 送信結果レスポンス
     */
    @PostMapping("/error")
    public ResponseEntity<String> testError(
            @RequestParam(required = false, defaultValue = "🚨 [NotificationService] テスト障害通知ですじゃ！") String message
    ) {
        log.info("テストエラー通知要求を受信: {}", message);
        sendNotificationUseCase.send(Notification.ofLineWorksError(message));
        return ResponseEntity.ok("システム管理者へ送信完了じゃ！");
    }
}
