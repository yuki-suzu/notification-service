package com.computer_rescuer.notification.application.service;

import com.computer_rescuer.notification.application.NotifyUnstampedAlertUseCase;
import com.computer_rescuer.notification.application.dto.UnstampedAlertEvent;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.domain.service.UnstampedAlertMessageFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 勤怠未打刻者検知イベントに対するアラート通知ユースケースを処理するアプリケーションサービス。
 * <p>
 * {@link NotifyUnstampedAlertUseCase} を実装し、未打刻者一覧をドメインフォーマッターで整形後、
 * 管理者向けチャンネルへアラートメッセージを配信します。
 * </p>
 */
@Slf4j
@Service
public class NotifyUnstampedAlertService implements NotifyUnstampedAlertUseCase {

    private final Map<NotificationChannelType, NotificationSender> senderMap;
    private final UnstampedAlertMessageFormatter unstampedAlertMessageFormatter;

    /**
     * コンストラクタ。
     *
     * @param senders                        送信ゲートウェイのリスト
     * @param unstampedAlertMessageFormatter 未打刻アラート文面整形ドメインサービス
     */
    public NotifyUnstampedAlertService(
            List<NotificationSender> senders,
            UnstampedAlertMessageFormatter unstampedAlertMessageFormatter
    ) {
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(
                        NotificationSender::getChannelType,
                        Function.identity()
                ));
        this.unstampedAlertMessageFormatter = unstampedAlertMessageFormatter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(UnstampedAlertEvent event) {
        if (event.employees() == null || event.employees().isEmpty()) {
            log.info("ℹ️ 対象日: {} の未打刻者はいませんでした。通知をスキップします。", event.targetDate());
            return;
        }

        log.info("▶ [未打刻アラート処理] 対象日: {}, 未打刻者数: {} 名のアラート通知を生成します。",
                event.targetDate(), event.employees().size());

        String formattedMessage = unstampedAlertMessageFormatter.format(event.targetDate(), event.employees());

        Notification notification = new Notification(
                NotificationChannelType.LINE_WORKS,
                Notification.DestinationType.CHANNEL,
                null,
                formattedMessage,
                LocalDateTime.now()
        );

        NotificationSender sender = senderMap.get(NotificationChannelType.LINE_WORKS);
        if (sender == null) {
            log.error("❌ LINE WORKS 送信ゲートウェイが見つかりません。");
            throw new IllegalStateException("LINE WORKS 送信ゲートウェイが未登録です");
        }

        sender.send(notification);
        log.info("✅ [未打刻アラート完了] LINE WORKS 管理者チャンネルへの送信が完了しました。");
    }
}
