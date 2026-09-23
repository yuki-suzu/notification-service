package com.computer_rescuer.notification.application.service;

import com.computer_rescuer.notification.application.NotifyAttendanceIrregularityUseCase;
import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.domain.service.AttendanceIrregularityMessageFormatter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 月次勤怠サマリエベントを処理し、LINE WORKS へメッセージ配信するアプリケーションサービス。
 */
@Slf4j
@Service
public class NotifyAttendanceIrregularityService implements NotifyAttendanceIrregularityUseCase {

  private final Map<NotificationChannelType, NotificationSender> senderMap;
  private final AttendanceIrregularityMessageFormatter messageFormatter;

  public NotifyAttendanceIrregularityService(
      List<NotificationSender> senders,
      AttendanceIrregularityMessageFormatter messageFormatter
  ) {
    this.senderMap = senders.stream()
        .collect(Collectors.toMap(NotificationSender::getChannelType, Function.identity()));
    this.messageFormatter = messageFormatter;
  }

  @Override
  public void execute(AttendanceIrregularityEvent event) {
    if (event.employees() == null || event.employees().isEmpty()) {
      log.info("ℹ️ 対象月: {} の勤怠サマリ対象者はいませんでした。通知をスキップします。",
          event.procMonth());
      return;
    }

    NotificationSender sender = senderMap.get(NotificationChannelType.LINE_WORKS);
    if (sender == null) {
      log.error("❌ LINE WORKS 送信ゲートウェイが見つかりません。");
      throw new IllegalStateException("LINE WORKS 送信ゲートウェイが未登録です");
    }

    LocalDateTime now = LocalDateTime.now();
    log.info(
        "▶ [月次勤怠サマリ通知] 受信日時: {}, 対象月: {}, 送信対象者: {} 名のメッセージを生成します。",
        now, event.procMonth(), event.employees().size());

    // 1. 受信時点の日時を渡して文面を整形
    String formattedMessage = messageFormatter.format(now, event.employees());

    // 2. 管理者チャンネル宛て通知モデルを生成
    Notification notification = Notification.ofLineWorksAlert(formattedMessage);

    // 3. 送信
    sender.send(notification);
    log.info("✅ [月次勤怠サマリ通知完了] 管理者トークルームへのサマリ送信が完了しました。");
  }
}
