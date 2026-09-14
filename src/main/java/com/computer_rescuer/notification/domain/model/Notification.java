package com.computer_rescuer.notification.domain.model;

import java.time.LocalDateTime;

/**
 * プラットフォーム共通の通知ドメインモデル。
 * <p>
 * 外部システムや送信元マイクロサービスから要求された通知の意図、宛先、本文を不変オブジェクトとしてカプセル化します。
 * 送信先が特定の管理者チャンネルか、システム担当者宛てか、あるいは個別従業員宛てかを柔軟に表現できます。
 * </p>
 *
 * @param channelType     通知先チャネル種別（LINE_WORKS等）
 * @param destinationType 宛先種別（CHANNEL: トークルーム, USER: 個人宛）
 * @param targetId        宛先ID（チャンネルIDまたは個別ユーザーID。nullの場合はデフォルト設定を適用）
 * @param message         送信する本文メッセージ
 * @param requestedAt     通知要求日時
 */
public record Notification(
        NotificationChannelType channelType,
        DestinationType destinationType,
        String targetId,
        String message,
        LocalDateTime requestedAt
) {

    /**
     * 通知の送信宛先スコープを定義する列挙型。
     */
    public enum DestinationType {
        /**
         * 共有トークルーム/チャンネル宛て
         */
        CHANNEL,
        /**
         * 特定ユーザーのダイレクトメッセージ宛て
         */
        USER
    }

    /**
     * LINE WORKSの管理者アラートチャンネル向け通知オブジェクトを生成します。
     *
     * @param message 送信するアラート本文
     * @return 構築された通知オブジェクト
     */
    public static Notification ofLineWorksAlert(String message) {
        return new Notification(
                NotificationChannelType.LINE_WORKS,
                DestinationType.CHANNEL,
                null,
                message,
                LocalDateTime.now()
        );
    }

    /**
     * LINE WORKSのシステム管理者向けエラー通知オブジェクトを生成します。
     *
     * @param message 送信するエラー本文
     * @return 構築された通知オブジェクト
     */
    public static Notification ofLineWorksError(String message) {
        return new Notification(
                NotificationChannelType.LINE_WORKS,
                DestinationType.USER,
                null,
                message,
                LocalDateTime.now()
        );
    }
}
