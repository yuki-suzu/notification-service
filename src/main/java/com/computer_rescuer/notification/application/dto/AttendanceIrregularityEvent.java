package com.computer_rescuer.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 勤怠管理サービスから受信する「月次勤怠サマリ（勤怠異常）イベント」のデータ転送オブジェクト（DTO）。
 * <p>
 * 勤怠管理サービスで当月集計・差分検知された各従業員の勤怠異常内訳（予定休・当欠・半休・遅延）を保持します。
 * </p>
 *
 * @param procMonth 処理対象年月 (yyyy-MM 形式)
 * @param employees 勤怠サマリ対象の従業員リスト (1件以上必須)
 */
public record AttendanceIrregularityEvent(
    @NotBlank(message = "処理対象月は必須です")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "処理対象月は yyyy-MM 形式である必要があります")
    @JsonProperty("proc_month")
    String procMonth,

    @NotEmpty(message = "従業員リストは1件以上指定してください")
    @JsonProperty("employees")
    List<@Valid EmployeeIrregularitySummary> employees
) {

  /**
   * 個別従業員の月次勤怠集約情報。
   *
   * @param employeeNumber          社員番号
   * @param departmentName          所属部門名
   * @param fullName                氏名
   * @param scheduledHolidayCount   予定休日数
   * @param unscheduledHolidayCount 当日欠勤日数
   * @param halfHolidayCount        半日休暇日数（午前・午後の合算）
   * @param delayCount              遅延回数
   */
  public record EmployeeIrregularitySummary(
      @NotBlank(message = "社員番号は必須です")
      @JsonProperty("employee_number")
      String employeeNumber,

      @NotBlank(message = "部門名は必須です")
      @JsonProperty("department_name")
      String departmentName,

      @NotBlank(message = "氏名は必須です")
      @JsonProperty("full_name")
      String fullName,

      @JsonProperty("scheduled_holiday_count")
      int scheduledHolidayCount,

      @JsonProperty("unscheduled_holiday_count")
      int unscheduledHolidayCount,

      @JsonProperty("half_holiday_count")
      int halfHolidayCount,

      @JsonProperty("delay_count")
      int delayCount
  ) {

  }
}
