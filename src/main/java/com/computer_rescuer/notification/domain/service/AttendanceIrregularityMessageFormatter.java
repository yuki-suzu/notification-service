package com.computer_rescuer.notification.domain.service;

import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent.EmployeeIrregularitySummary;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 月次勤怠サマリエベントから管理者トークルーム向けの通知文面を組み立てるドメインサービス。
 */
@Service
public class AttendanceIrregularityMessageFormatter {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(
      "yyyy-MM-dd HH:mm");

  /**
   * 勤怠サマリ情報から LINE WORKS 送信用のメッセージ本文を構築します。
   *
   * @param receivedAt メッセージ受信・処理日時
   * @param employees  勤怠サマリ従業員リスト
   * @return 整形された通知メッセージ文字列
   */
  public String format(LocalDateTime receivedAt, List<EmployeeIrregularitySummary> employees) {
    StringBuilder sb = new StringBuilder();

    // 💡 1. Kafka 受信時点の日時をタイトルにセット
    sb.append("📢 【月次勤怠サマリ更新】 (")
        .append(receivedAt.format(TIME_FORMATTER))
        .append("時点)\n");
    sb.append("勤怠情報が更新されました\n\n");

    // 部門名ごとにグルーピング（未設定時は「未所属」）
    Map<String, List<EmployeeIrregularitySummary>> grouped = employees.stream()
        .collect(Collectors.groupingBy(
            e -> (e.departmentName() != null && !e.departmentName().isBlank())
                ? e.departmentName()
                : "未所属"
        ));

    grouped.forEach((dept, list) -> {
      sb.append("🏢 ").append(dept).append("\n");
      for (EmployeeIrregularitySummary emp : list) {
        // 💡 2. 予定休を除外し、当欠・半休・遅延のみをスッキリ出力
        sb.append(String.format("  ・%s 当欠:%d日 / 半休:%d日 / 遅延:%d日%n",
            emp.fullName(),
            emp.unscheduledHolidayCount(),
            emp.halfHolidayCount(),
            emp.delayCount()
        ));
      }
      sb.append("\n");
    });

    // 💡 3. 末尾の自動配信文言は削除し、余分な末尾改行を除去
    return sb.toString().stripTrailing();
  }
}
