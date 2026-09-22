package com.computer_rescuer.notification.application.service;

import com.computer_rescuer.notification.application.NotifyAttendanceIrregularityUseCase;
import com.computer_rescuer.notification.application.dto.AttendanceIrregularityAlertEvent;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.domain.service.AttendanceIrregularityMessageFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 勤怠不良者検知イベントに対するアラート通知ユースケースを処理するアプリケーションサービス。
 * <p>
 * {@link NotifyAttendanceIrregularityUseCase} を実装し、日次集計された勤怠不良者一覧を
 * ドメインフォーマッター（{@link AttendanceIrregularityMessageFormatter}）で整形後、 LINE WORKS
 * の管理者トークルーム宛てに一括送信します。
 * </p>
 */
@Slf4j
@Service
public class NotifyAttendanceIrregularityService implements NotifyAttendanceIrregularityUseCase {

  private final Map<NotificationChannelType, NotificationSender> senderMap;
  private final AttendanceIrregularityMessageFormatter messageFormatter;

  /**
   * コンストラクタ。
   *
   * @param senders          DIコンテナに登録されている全送信ゲートウェイのリスト
   * @param messageFormatter 勤怠不良アラート文面整形ドメインサービス
   */
  public NotifyAttendanceIrregularityService(
      List<NotificationSender> senders,
      AttendanceIrregularityMessageFormatter messageFormatter
  ) {
    this.senderMap = senders.stream()
        .collect(Collectors.toMap(NotificationSender::getChannelType, Function.identity()));
    this.messageFormatter = messageFormatter;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void execute(AttendanceIrregularityAlertEvent event) {
    if (event.employees() == null || event.employees().isEmpty()) {
      log.info("ℹ️ 対象日: {} の勤怠不良検知対象者はいませんでした。通知をスキップします。",
          event.targetDate());
      return;
    }

    NotificationSender sender = senderMap.get(NotificationChannelType.LINE_WORKS);
    if (sender == null) {
      log.error("❌ LINE WORKS 送信ゲートウェイが見つかりません。");
      throw new IllegalStateException("LINE WORKS 送信ゲートウェイが未登録です");
    }

    log.info("▶ [勤怠不良アラート処理] 対象日: {}, 対象者数: {} 名のアラート通知を生成します。",
        event.targetDate(), event.employees().size());

    // 1. ドメインフォーマッターで文面を整形
    String formattedMessage = messageFormatter.format(event.targetDate(), event.employees());

    // 2. 管理者チャンネル（トークルーム）宛ての通知モデルを生成
    Notification notification = Notification.ofLineWorksAlert(formattedMessage);

    // 3. 送信ゲートウェイへ配送
    sender.send(notification);
    log.info("✅ [勤怠不良アラート完了] 管理者トークルームへの警告レポート送信が完了しました。");
  }
}