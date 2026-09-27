package com.computer_rescuer.notification.application.service;

import com.computer_rescuer.notification.application.SendUnstampedDirectReminderUseCase;
import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent;
import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent.DirectReminderEmployee;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.domain.service.DirectReminderReportFormatter;
import com.computer_rescuer.notification.domain.service.DirectReminderReportFormatter.FailureItem;
import com.computer_rescuer.notification.domain.service.UnstampedDirectReminderMessageFormatter;
import com.computer_rescuer.notification.infrastructure.lineworks.support.LineworksAccountResolver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 未打刻者本人への個別ダイレクト通知ユースケースを処理するアプリケーションサービス。
 * <p>
 * {@link SendUnstampedDirectReminderUseCase} を実装し、未打刻対象従業員の一覧を走査して個別DMを送信します。<br>
 * メール未設定やアカウント逆引き失敗時は管理者宛てに代理送信を行い、 最終結果をレポートフォーマッターを通じて管理者トークルームへ報告します。
 * </p>
 */
@Slf4j
@Service
public class SendUnstampedDirectReminderService implements SendUnstampedDirectReminderUseCase {

  private final Map<NotificationChannelType, NotificationSender> senderMap;
  private final LineworksAccountResolver lineworksAccountResolver;
  private final UnstampedDirectReminderMessageFormatter reminderMessageFormatter;
  private final DirectReminderReportFormatter reportFormatter;

  /**
   * コンストラクタ。
   *
   * @param senders                  全送信ゲートウェイのリスト
   * @param lineworksAccountResolver LINE WORKS アカウント解決サポート
   * @param reminderMessageFormatter 本人向けリマインド文面整形ドメインサービス
   * @param reportFormatter          管理者向けレポート文面整形ドメインサービス
   */
  public SendUnstampedDirectReminderService(
      List<NotificationSender> senders,
      LineworksAccountResolver lineworksAccountResolver,
      UnstampedDirectReminderMessageFormatter reminderMessageFormatter,
      DirectReminderReportFormatter reportFormatter
  ) {
    this.senderMap = senders.stream()
        .collect(Collectors.toMap(NotificationSender::getChannelType, Function.identity()));
    this.lineworksAccountResolver = lineworksAccountResolver;
    this.reminderMessageFormatter = reminderMessageFormatter;
    this.reportFormatter = reportFormatter;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void execute(UnstampedDirectReminderEvent event) {
    if (event.employees() == null || event.employees().isEmpty()) {
      log.info("ℹ️ 個別DM通知の対象者はいませんでした。処理を終了します。");
      return;
    }

    NotificationSender sender = senderMap.get(NotificationChannelType.LINE_WORKS);
    if (sender == null) {
      log.error("❌ LINE WORKS 送信ゲートウェイが見つかりません。");
      throw new IllegalStateException("LINE WORKS 送信ゲートウェイが未登録です");
    }

    log.info("▶ [未打刻DM一括配信] 配信対象: {} 名へのリマインド送信を開始します。",
        event.employees().size());

    List<DirectReminderEmployee> successItems = new ArrayList<>();
    List<FailureItem> failureItems = new ArrayList<>();

    for (DirectReminderEmployee emp : event.employees()) {
      sendIndividual(emp, sender, successItems, failureItems);
    }

    log.info("✅ [未打刻DM一括配信完了] 正常送信: {} 件 / 失敗・スキップ: {} 件",
        successItems.size(), failureItems.size());

    // 管理者チャンネルへサマリーレポートを配送
    notifyAdminReport(successItems, failureItems, sender);
  }

  /**
   * 個別従業員への送信を試行し、逆引き不能な場合は管理者宛てにフォールバック送信を行います。
   *
   * @param emp          対象従業員
   * @param sender       送信ゲートウェイ
   * @param successItems 成功リスト
   * @param failureItems 失敗リスト
   */
  private void sendIndividual(
      DirectReminderEmployee emp,
      NotificationSender sender,
      List<DirectReminderEmployee> successItems,
      List<FailureItem> failureItems
  ) {
    try {
      String lineWorksUserId = StringUtils.hasText(emp.email())
          ? lineworksAccountResolver.resolveUserIdFromEmail(emp.email())
          : "";

      String baseMessage = reminderMessageFormatter.format(emp);

      if (StringUtils.hasText(lineWorksUserId)) {
        // 1. 本人宛てに送信
        Notification notification = new Notification(
            NotificationChannelType.LINE_WORKS,
            Notification.DestinationType.USER,
            lineWorksUserId,
            baseMessage,
            LocalDateTime.now()
        );
        sender.send(notification);
        log.info("  └ ✉️ 本人送信成功: {} さん ({})", emp.fullName(), lineWorksUserId);
        successItems.add(emp);

      } else {
        // 2. 💡 メール未設定または逆引き不可 ➜ 管理者宛てに代理送信！
        log.warn(
            "⚠️ LINE WORKS アカウント解決不可のため、管理者へ代理送信します: 社員番号: {}, 氏名: {}",
            emp.employeeNumber(), emp.fullName());

        String proxyMessage = "📢 【管理者代理通知 / 未打刻リマインド】\n"
            + "※対象者のLINE WORKSアカウントが解決できない（またはDM無効設定）ため、管理者に代理配信されました。\n\n"
            + baseMessage;

        // targetId に null を指定すると LineworksNotificationSenderImpl により systemManagerId へ送信される
        Notification proxyNotification = new Notification(
            NotificationChannelType.LINE_WORKS,
            Notification.DestinationType.USER,
            null,
            proxyMessage,
            LocalDateTime.now()
        );
        sender.send(proxyNotification);
        log.info("  └ 🛡️ 管理者へ代理送信完了: {} さん分", emp.fullName());

        // レポート上にも代理送信した旨を記録
        failureItems.add(new FailureItem(emp, "アカウント解決不可のため管理者へ代理送信"));
      }

    } catch (Exception e) {
      log.error("❌ 送信失敗: 社員番号: {}, 氏名: {}, 原因: {}",
          emp.employeeNumber(), emp.fullName(), e.getMessage(), e);
      failureItems.add(new FailureItem(emp, "送信エラー: " + e.getMessage()));
    }
  }

  /**
   * 配信結果をドメインフォーマッターで整形し、管理者トークルームへ送信します。
   *
   * @param successItems 成功リスト
   * @param failureItems 失敗リスト
   * @param sender       送信ゲートウェイ
   */
  private void notifyAdminReport(
      List<DirectReminderEmployee> successItems,
      List<FailureItem> failureItems,
      NotificationSender sender
  ) {
    try {
      String reportMessage = reportFormatter.format(successItems, failureItems);
      sender.send(Notification.ofLineWorksAlert(reportMessage));
      log.info("📢 管理者トークルームへ配信結果レポートを送信しました。");
    } catch (Exception e) {
      log.error("⚠️ [二次障害防止] 管理者への配信結果レポート送信に失敗しました。", e);
    }
  }
}
