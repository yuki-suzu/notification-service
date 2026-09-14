package com.computer_rescuer.notification.adapter.out.lineworks;

import com.computer_rescuer.notification.adapter.out.lineworks.client.LineworksMessageApi;
import com.computer_rescuer.notification.application.port.out.NotificationChannelPort;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.infrastructure.property.LineworksProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LINE WORKS を用いた通知配信ポート（{@link NotificationChannelPort}）の実装アダプター。
 * <p>
 * 汎用的な {@link Notification} ドメインモデルを解釈し、宛先種別（チャンネル/ユーザー）に応じて
 * 最適な LINE WORKS API エンドポイントを呼び出します。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineworksNotificationAdapter implements NotificationChannelPort {

    private final LineworksMessageApi messageApi;
    private final LineworksProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.LINE_WORKS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(Notification notification) {
        switch (notification.destinationType()) {
            case CHANNEL -> sendToChannel(notification);
            case USER -> sendToUser(notification);
        }
    }

    /**
     * トークルーム（チャンネル）へメッセージを送信します。
     *
     * @param notification 通知ドメインオブジェクト
     */
    private void sendToChannel(Notification notification) {
        // 明示的なターゲットIDがなければ、デフォルトのアラートチャンネルを使用
        String channelId = (notification.targetId() != null && !notification.targetId().isBlank())
                ? notification.targetId()
                : properties.alertChannelId();

        log.debug("LINE WORKS チャンネル ({}) へ通知を送信します。", channelId);
        messageApi.sendChannelMessage(channelId, notification.message());
    }

    /**
     * 個別ユーザーへダイレクトメッセージを送信します。
     *
     * @param notification 通知ドメインオブジェクト
     */
    private void sendToUser(Notification notification) {
        // 明示的なターゲットIDがなければ、デフォルトのシステム管理者IDを使用
        String userId = (notification.targetId() != null && !notification.targetId().isBlank())
                ? notification.targetId()
                : properties.systemManagerId();

        log.debug("LINE WORKS ユーザー ({}) へダイレクト通知を送信します。", userId);
        messageApi.sendTextMessage(userId, notification.message());
    }
}
