package com.computer_rescuer.notification.domain.gateway;

import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;

/**
 * 各種外部メッセージングサービス（LINE WORKS等）へ通知を送信するためのドメインゲートウェイ。
 * <p>
 * ドメイン層が外部インフラの通信プロトコルやAPI仕様に直接依存することを防ぐため、
 * 通知送信に必要な操作を抽象化して定義します。
 * </p>
 */
public interface NotificationSender {

    /**
     * サポートする通知チャネル種別を取得します。
     *
     * @return 対応する {@link NotificationChannelType}
     */
    NotificationChannelType getChannelType();

    /**
     * 指定された通知ドメインモデルを外部サービスへ送信します。
     *
     * @param notification 送信対象となる通知のドメインエンティティ
     * @throws RuntimeException 外部サービスへの送信に失敗した場合
     */
    void send(Notification notification);
}
