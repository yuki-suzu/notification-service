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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 未打刻者本人への個別ダイレクト通知ユースケースを処理するアプリケーションサービス。
 * <p>
 * {@link SendUnstampedDirectReminderUseCase} を実装し、未打刻対象従業員の一覧を走査して個別DMを送信します。<br>
 * 本人への送信成否（成功者・失敗者）を収集し、レポートフォーマッター（{@link DirectReminderReportFormatter}）
 * を通じて管理者トークルームへ全件結果を一括報告します。
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

        log.info("▶ [未打刻DM一括配信] 配信対象: {} 名への個別リマインド送信を開始します。", event.employees().size());

        List<DirectReminderEmployee> successItems = new ArrayList<>();
        List<FailureItem> failureItems = new ArrayList<>();

        for (DirectReminderEmployee emp : event.employees()) {
            sendIndividual(emp, sender, successItems, failureItems);
        }

        log.info("✅ [未打刻DM一括配信完了] 成功: {} 件 / 失敗・警告: {} 件",
                successItems.size(), failureItems.size());

        // 管理者チャンネルへサマリーレポートを配送
        notifyAdminReport(successItems, failureItems, sender);
    }

    /**
     * 個別従業員への送信を試行し、成功・失敗の各リストへ振り分けます。
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
            String lineWorksUserId = lineworksAccountResolver.resolveUserIdFromEmail(emp.email());
            if (lineWorksUserId.isBlank()) {
                log.warn("⚠️ LINE WORKS アカウントID導出不可（スキップ）: 社員番号: {}, 氏名: {}",
                        emp.employeeNumber(), emp.fullName());
                failureItems.add(new FailureItem(emp, "アカウント導出不可（メール不正）"));
                return;
            }

            String message = reminderMessageFormatter.format(emp);
            Notification notification = new Notification(
                    NotificationChannelType.LINE_WORKS,
                    Notification.DestinationType.USER,
                    lineWorksUserId,
                    message,
                    LocalDateTime.now()
            );

            sender.send(notification);
            log.info("  └ ✉️ 送信成功: {} さん", emp.fullName());
            successItems.add(emp);

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
