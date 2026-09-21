package com.computer_rescuer.notification.domain.service;

import com.computer_rescuer.notification.application.dto.UnstampedAlertEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 未打刻者検知イベントから通知用の整形テキストを組み立てるドメインサービス。
 * <p>
 * 未打刻者の一覧を所属部門ごとにグルーピングし、管理者向けトークルームで視認しやすい
 * フォーマット済みのアラートメッセージ文字列を構築します。
 * </p>
 */
@Service
public class UnstampedAlertMessageFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 未打刻者リストを部門別にグループ化し、管理者チャンネル向けのアラート本文を構築します。
     *
     * @param targetDate 勤怠判定の対象日
     * @param employees  未打刻従業員のリスト
     * @return フォーマット済みの通知メッセージ文字列
     */
    public String format(LocalDate targetDate, List<UnstampedAlertEvent.UnstampedEmployee> employees) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ 【未打刻アラート】\n");
        sb.append(targetDate.toString()).append(" の出勤打刻が確認できない従業員がいます。\n\n");

        // 部門名ごとにグルーピング（未設定時は「未所属」）
        Map<String, List<UnstampedAlertEvent.UnstampedEmployee>> grouped = employees.stream()
                .collect(Collectors.groupingBy(
                        e -> (e.departmentName() != null && !e.departmentName().isBlank())
                                ? e.departmentName()
                                : "未所属"
                ));

        grouped.forEach((deptName, list) -> {
            sb.append("🏢 ").append(deptName).append("\n");
            for (UnstampedAlertEvent.UnstampedEmployee emp : list) {
                String timeStr = emp.scheduledStartAt() != null
                        ? emp.scheduledStartAt().format(TIME_FORMATTER)
                        : "予定不明";
                sb.append(String.format("  ・%s （予定: %s〜）%n", emp.fullName(), timeStr));
            }
            sb.append("\n");
        });

        sb.append("※打刻漏れ、または遅刻の可能性があります。状況の確認をお願いします。");
        return sb.toString();
    }
}
