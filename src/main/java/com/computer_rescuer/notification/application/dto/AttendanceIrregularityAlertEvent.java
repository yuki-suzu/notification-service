package com.computer_rescuer.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 勤怠管理サービスから受信する「勤怠不良者検知イベント」のデータ転送オブジェクト（DTO）。
 * <p>
 * 日次集計バッチによって検知された、当月内の当日欠勤・遅刻・早退の累積回数が
 * 規定値を超過した従業員の一覧および判定コンテキストをまとめて保持します。
 * </p>
 *
 * @param targetDate 勤怠判定の対象日（現在または過去の日付）
 * @param detectedAt システムによって検知・集計された日時（必須）
 * @param employees  勤怠警告の対象となる従業員リスト（1件以上必須）
 */
public record AttendanceIrregularityAlertEvent(
        @NotNull(message = "対象日は必須です")
        @PastOrPresent(message = "対象日に未来の日付は指定できません")
        @JsonProperty("target_date")
        LocalDate targetDate,

        @NotNull(message = "検知日時は必須です")
        @JsonProperty("detected_at")
        LocalDateTime detectedAt,

        @NotEmpty(message = "従業員リストは1件以上指定してください")
        @JsonProperty("employees")
        List<@Valid IrregularEmployee> employees
) {

    /**
     * 勤怠不良警告の対象となった個別従業員の勤怠内訳を保持する不変レコード。
     *
     * @param employeeNumber      自社システムにおける従業員番号（必須）
     * @param fullName            従業員の氏名（必須）
     * @param departmentName      所属部門名（必須）
     * @param email               社用メールアドレス（必須）
     * @param sameDayAbsenceCount 当月における当日欠勤の累積回数（0以上必須）
     * @param lateCount           当月における遅刻の累積回数（0以上必須）
     * @param earlyLeavingCount   当月における早退の累積回数（0以上必須）
     */
    public record IrregularEmployee(
            @NotBlank(message = "社員番号は必須です")
            @JsonProperty("employee_number")
            String employeeNumber,

            @NotBlank(message = "氏名は必須です")
            @JsonProperty("full_name")
            String fullName,

            @NotBlank(message = "部門名は必須です")
            @JsonProperty("department_name")
            String departmentName,

            @NotBlank(message = "メールアドレスは必須です")
            @Email(message = "正しいメールアドレス形式で入力してください")
            @JsonProperty("email")
            String email,

            @NotNull(message = "当日欠勤回数は必須です")
            @Min(value = 0, message = "当日欠勤回数は0以上である必要があります")
            @JsonProperty("same_day_absence_count")
            Integer sameDayAbsenceCount,

            @NotNull(message = "遅刻回数は必須です")
            @Min(value = 0, message = "遅刻回数は0以上である必要があります")
            @JsonProperty("late_count")
            Integer lateCount,

            @NotNull(message = "早退回数は必須です")
            @Min(value = 0, message = "早退回数は0以上である必要があります")
            @JsonProperty("early_leaving_count")
            Integer earlyLeavingCount
    ) {
    }
}
