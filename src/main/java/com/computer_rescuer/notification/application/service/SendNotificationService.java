package com.computer_rescuer.notification.application.service;

import com.computer_rescuer.notification.application.SendNotificationUseCase;
import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 汎用通知配信ユースケースを取りまとめるアプリケーションサービス。
 * <p>
 * {@link SendNotificationUseCase} を実装し、要求された配信チャネル（LINE WORKS等）に応じた
 * 送信ゲートウェイ（{@link NotificationSender}）へメッセージ送信をディスパッチします。
 * </p>
 */
@Slf4j
@Service
public class SendNotificationService implements SendNotificationUseCase {

  private final Map<NotificationChannelType, NotificationSender> senderMap;

  /**
   * コンストラクタ。利用可能な送信ゲートウェイをインデックス化します。
   *
   * @param senders 登録されている全送信ゲートウェイのリスト
   */
  public SendNotificationService(List<NotificationSender> senders) {
    this.senderMap = senders.stream()
        .collect(Collectors.toMap(
            NotificationSender::getChannelType,
            Function.identity()
        ));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void send(NotificationCommand command) {
    Notification notification = command.toDomainModel();

    NotificationSender sender = senderMap.get(notification.channelType());
    if (sender == null) {
      log.error("❌ サポートされていない通知チャネルです: {}", notification.channelType());
      throw new IllegalArgumentException("未対応の通知チャネル: " + notification.channelType());
    }

    log.info("▶ [汎用通知] {} チャネルへ通知をディスパッチします。", notification.channelType());
    sender.send(notification);
  }
}
