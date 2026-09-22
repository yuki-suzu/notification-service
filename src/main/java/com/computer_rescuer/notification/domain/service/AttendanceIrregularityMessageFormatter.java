package com.computer_rescuer.notification.domain.service;

import com.computer_rescuer.notification.application.dto.AttendanceIrregularityAlertEvent.IrregularEmployee;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 勤怠不良者検知イベントから管理者トークルーム向けのアラート文面を組み立てるドメインサービス。
 * <p>
 * 検知対象者を部門ごとにグルーピングし、当月内の「当日欠勤」「遅刻」「早退」の各累積回数を 管理者が一目で把握できるよう、視認性を重視したフォーマット文字列を構築します。
 * </p>
 */
@Service
public class AttendanceIrregularityMessageFormatter {

  /**
   * 勤怠不良者リストを部門別にグループ化し、管理者チャンネル向けのアラート本文を構築します。
   *
   * @param targetDate 判定対象日
   * @param employees  勤怠警告対象の従業員リスト
   * @return フォーマット済みの通知本文文字列
   */
  public String format(LocalDate targetDate, List<IrregularEmployee> employees) {
    StringBuilder sb = new StringBuilder();
    sb.append("⚠️ 【勤怠不良・警告レポート】\n");
    sb.append(targetDate.toString())
        .append(" 時点で、勤怠不良基準に達した従業員が検知されました。\n\n");

    // 部門名ごとにグルーピング（未設定時は「未所属」）
    Map<String, List<IrregularEmployee>> grouped = employees.stream()
        .collect(Collectors.groupingBy(
            e -> (e.departmentName() != null && !e.departmentName().isBlank())
                ? e.departmentName()
                : "未所属"
        ));

    grouped.forEach((deptName, list) -> {
      sb.append("🏢 ").append(deptName).append("\n");
      for (IrregularEmployee emp : list) {
        int absence = emp.sameDayAbsenceCount() != null ? emp.sameDayAbsenceCount() : 0;
        int late = emp.lateCount() != null ? emp.lateCount() : 0;
        int early = emp.earlyLeavingCount() != null ? emp.earlyLeavingCount() : 0;

        sb.append(String.format("  ・%s（社員番号: %s）%n", emp.fullName(), emp.employeeNumber()));
        sb.append(
            String.format("    当欠: %d回 / 遅刻: %d回 / 早退: %d回%n", absence, late, early));
      }
      sb.append("\n");
    });

    sb.append("※各所属長および管理者は、対象従業員へのヒアリングおよびフォローをお願いします。");
    return sb.toString();
  }
}
