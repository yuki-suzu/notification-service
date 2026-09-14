package com.computer_rescuer.notification.application.port.out;

import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;

/**
 * 各種メッセージングサービス（LINE WORKS、Slackなど）へ通知を送信するための出力ポート。
 * <p>
 * このインターフェースを実装することで、アプリケーション層は各外部サービスの
 * API仕様や認証プロトコルに依存することなく、統一的な手順でメッセージを送信できます。
 * </p>
 */
public interface NotificationChannelPort {

    /**
     * 対象の通知チャネル種別を取得します。
     *
     * @return サポートする通知チャネル種別（{@link NotificationChannelType}）
     */
    NotificationChannelType getChannelType();

    /**
     * 指定された通知モデルに基づき、外部サービスへメッセージを送信します。
     *
     * @param notification 送信対象となる通知のドメインオブジェクト
     * @throws RuntimeException 外部API通信に失敗した場合
     */
    void send(Notification notification);
}
