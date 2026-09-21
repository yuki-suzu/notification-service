package com.computer_rescuer.notification.application.dto;

import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Kafka トピックから受信する通知要求イベントのデータ転送オブジェクト（DTO）。
 * <p>
 * 他マイクロサービスが発行した通知メッセージを、
 * JSONからデシリアライズして保持する不変レコードです。
 * </p>
 *
 * @param channelType     配信先チャネル（例: LINE_WORKS）
 * @param destinationType 配信先の種別（CHANNEL: トークルーム, USER: 個別DM）
 * @param targetId        送信先の一意識別子（トークルームIDまたはユーザーID。null時は既定値）
 * @param message         通知本文
 */
public record NotificationCommand(
        @JsonProperty("channel_type")
        NotificationChannelType channelType,

        @JsonProperty("destination_type")
        Notification.DestinationType destinationType,

        @JsonProperty("target_id")
        String targetId,

        @JsonProperty("message")
        String message
) {

    /**
     * 受信したイベントDTOを、アプリケーション層で扱う通知ドメインモデルへ変換します。
     *
     * @return 構築された {@link Notification} ドメインオブジェクト
     */
    public Notification toDomainModel() {
        return new Notification(
                this.channelType != null ? this.channelType : NotificationChannelType.LINE_WORKS,
                this.destinationType != null ? this.destinationType : Notification.DestinationType.CHANNEL,
                this.targetId,
                this.message,
                LocalDateTime.now()
        );
    }
}
