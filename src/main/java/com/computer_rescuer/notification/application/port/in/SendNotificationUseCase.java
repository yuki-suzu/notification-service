package com.computer_rescuer.notification.application.port.in;

import com.computer_rescuer.notification.domain.model.Notification;

/**
 * 通知送信要求を受け付ける入力ポート（ユースケースインターフェース）。
 * <p>
 * WebコントローラーやKafkaメッセージリスナー等のInput Adapterから呼び出され、
 * アプリケーションの通知配信ビジネスロジックを実行します。
 * </p>
 */
public interface SendNotificationUseCase {

    /**
     * 指定された通知オブジェクトを適切な配信チャネルへ送信します。
     *
     * @param notification 送信対象となる通知ドメインモデル
     */
    void send(Notification notification);
}
