package com.computer_rescuer.notification.application.interactor;

import com.computer_rescuer.notification.application.port.in.SendNotificationUseCase;
import com.computer_rescuer.notification.application.port.out.NotificationChannelPort;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知送信ユースケースの実装クラス（Interactor）。
 * <p>
 * DIされた複数の {@link NotificationChannelPort} 実装（LINE WORKS、Slackなど）の中から、
 * 要求されたチャネル種別に合致するAdapterを動的に解決してメッセージ送信を委譲します。
 * </p>
 */
@Slf4j
@Service
public class SendNotificationInteractor implements SendNotificationUseCase {

    /**
     * チャネル種別ごとのポートマッピング
     */
    private final Map<NotificationChannelType, NotificationChannelPort> channelPortMap;

    /**
     * コンストラクタ。
     * Springによって登録されているすべての {@link NotificationChannelPort} 実装を受け取り、Mapにキャッシュします。
     *
     * @param channelPorts 登録されている全送信チャネルポートのリスト
     */
    public SendNotificationInteractor(List<NotificationChannelPort> channelPorts) {
        this.channelPortMap = channelPorts.stream()
                .collect(Collectors.toMap(
                        NotificationChannelPort::getChannelType,
                        Function.identity()
                ));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(Notification notification) {
        NotificationChannelPort port = channelPortMap.get(notification.channelType());

        if (port == null) {
            log.error("❌ サポートされていない通知チャネルです: {}", notification.channelType());
            throw new IllegalArgumentException("未対応の通知チャネル: " + notification.channelType());
        }

        log.info("▶ [通知サービス] {} チャネルへ通知をディスパッチします。", notification.channelType());
        port.send(notification);
    }
}
