package com.computer_rescuer.notification.domain.service;

import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent.DirectReminderEmployee;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 未打刻者本人への個別DM配信結果から、管理者トークルーム向けサマリー文面を生成するドメインサービス。
 * <p>
 * 配信成功者および送信失敗・スキップ者の情報を集約し、管理者が一目で状況を把握できる
 * 視認性の高いレポート本文（絵文字、明細、注意書き）を構築します。
 * </p>
 */
@Service
public class DirectReminderReportFormatter {

    /**
     * 配信結果を表現する障害明細レコード。
     *
     * @param employee 対象従業員情報
     * @param reason   失敗またはスキップの理由
     */
    public record FailureItem(
            DirectReminderEmployee employee,
            String reason
    ) {
    }

    /**
     * 配信成功者リストおよび失敗者リストから、管理者向けレポート本文を構築します。
     *
     * @param successItems 正常にダイレクト通知が送信された従業員のリスト
     * @param failureItems 送信に失敗またはスキップされた障害情報のリスト
     * @return フォーマット済みの管理者向けレポート本文文字列
     */
    public String format(List<DirectReminderEmployee> successItems, List<FailureItem> failureItems) {
        StringBuilder sb = new StringBuilder();
        sb.append("📢 【未打刻DM 配信結果レポート】\n");
        sb.append("未打刻者本人への個別リマインドDMの送信が完了しました。\n");
        sb.append(String.format("（送信成功: %d名 / 失敗・警告: %d名）\n\n",
                successItems.size(), failureItems.size()));

        if (!successItems.isEmpty()) {
            sb.append("✅ 送信成功:\n");
            for (DirectReminderEmployee emp : successItems) {
                sb.append(String.format("  ・%s（社員番号: %s）%n", emp.fullName(), emp.employeeNumber()));
            }
            sb.append("\n");
        }

        if (!failureItems.isEmpty()) {
            sb.append("🚨 送信失敗・スキップ:\n");
            for (FailureItem item : failureItems) {
                sb.append(String.format("  ・%s（社員番号: %s）: %s%n",
                        item.employee().fullName(),
                        item.employee().employeeNumber(),
                        item.reason()));
            }
            sb.append("\n※失敗対象がある場合は、LINE WORKS の登録状況やログをご確認ください。");
        } else {
            sb.append("※全対象者へのダイレクト通知が正常に完了しました。");
        }

        return sb.toString();
    }
}
