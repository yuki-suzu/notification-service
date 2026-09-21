package com.computer_rescuer.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 勤怠管理サービスから受信する「未打刻者検知イベント」のデータ転送オブジェクト（DTO）。
 * <p>
 * 始業時刻を過ぎても出勤打刻が確認できない対象従業員の一覧と、判定対象日などのコンテキストを保持します。<br>
 * 本クラスは入力値検証アノテーションを備え、不正な形式や必須欠落のあるメッセージの混入をインバウンド境界で防ぎます。
 * </p>
 *
 * @param targetDate 勤怠判定の対象日（現在または過去の日付であること）
 * @param detectedAt システムによって未打刻が検知された日時（必須）
 * @param employees  未打刻と判定された対象従業員のリスト（各要素のバリデーションも再帰実行）
 */
public record UnstampedAlertEvent(
        @NotNull(message = "対象日は必須です")
        @PastOrPresent(message = "対象日に未来の日付は指定できません")
        @JsonProperty("target_date")
        LocalDate targetDate,

        @NotNull(message = "検知日時は必須です")
        @JsonProperty("detected_at")
        LocalDateTime detectedAt,

        @NotNull(message = "従業員リストは必須です")
        @JsonProperty("employees")
        List<@Valid UnstampedEmployee> employees
) {

    /**
     * 未打刻対象となった個別従業員の業務情報を保持する不変レコード。
     *
     * @param employeeNumber        自社システムにおける従業員番号（必須）
     * @param email                 社用メールアドレス（必須・正しいメール形式）
     * @param departmentName        所属部門名（必須）
     * @param fullName              従業員の氏名（必須）
     * @param scheduledStartAt      当該従業員の出勤予定時刻（必須）
     * @param monthlyUnstampedCount 当月における未打刻検知の累積回数（必須・1以上）
     */
    public record UnstampedEmployee(
            @NotBlank(message = "社員番号は必須です")
            @JsonProperty("employee_number")
            String employeeNumber,

            @NotBlank(message = "メールアドレスは必須です")
            @Email(message = "正しいメールアドレス形式で入力してください")
            @JsonProperty("email")
            String email,

            @NotBlank(message = "部門名は必須です")
            @JsonProperty("department_name")
            String departmentName,

            @NotBlank(message = "氏名は必須です")
            @JsonProperty("full_name")
            String fullName,

            @NotNull(message = "出勤予定時刻は必須です")
            @JsonProperty("scheduled_start_at")
            LocalTime scheduledStartAt,

            @NotNull(message = "当月未打刻回数は必須です")
            @Min(value = 1, message = "当月未打刻回数は1以上である必要があります")
            @JsonProperty("monthly_unstamped_count")
            Integer monthlyUnstampedCount
    ) {
    }
}
