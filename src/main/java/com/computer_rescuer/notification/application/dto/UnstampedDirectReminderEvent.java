package com.computer_rescuer.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 勤怠管理サービスから受信する「未打刻者本人向けダイレクト通知要求イベント」のデータ転送オブジェクト（DTO）。
 *
 * @param employees 個別リマインド送信の対象となる従業員リスト（1件以上必須）
 */
public record UnstampedDirectReminderEvent(
    @NotEmpty(message = "従業員リストは1件以上指定してください")
    @JsonProperty("employees")
    List<@Valid DirectReminderEmployee> employees
) {

  /**
   * ダイレクト通知の対象となる個別従業員の業務情報を保持する不変レコード。
   *
   * @param employeeNumber 自社システムにおける従業員番号（必須・追跡監査用キー）
   * @param email          社用メールアドレス（任意・未設定時は管理者宛てに代理送信）
   * @param fullName       従業員の氏名（必須・メッセージ本文の宛名呼びかけ用）
   */
  public record DirectReminderEmployee(
      @NotBlank(message = "社員番号は必須です")
      @JsonProperty("employee_number")
      String employeeNumber,

      // 💡 @NotBlank を削除！null や空文字でも DLT 送りにせず正常受信させる
      @Email(message = "正しいメールアドレス形式で入力してください")
      @JsonProperty("email")
      String email,

      @NotBlank(message = "氏名は必須です")
      @JsonProperty("fullName")
      String fullName
  ) {

  }
}
