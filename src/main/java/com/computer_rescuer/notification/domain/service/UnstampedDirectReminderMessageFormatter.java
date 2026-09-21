package com.computer_rescuer.notification.domain.service;

import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent.DirectReminderEmployee;
import org.springframework.stereotype.Service;

/**
 * 未打刻者本人向けの個別ダイレクトメッセージを組み立てるドメインサービス。
 * <p>
 * 角を立てずに打刻や連絡を促すため、出勤予定時刻や累積回数といった機械的な追究文言を排し、
 * 宛名と端的な事実通知・アクション喚起のみで構成された視認性の高いリマインド文面を構築します。
 * </p>
 */
@Service
public class UnstampedDirectReminderMessageFormatter {

    /**
     * 未打刻対象の従業員情報から、本人宛ての個別リマインド本文を構築します。
     *
     * @param employee 未打刻対象の従業員情報
     * @return フォーマット済みの通知本文文字列
     */
    public String format(DirectReminderEmployee employee) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ 【勤怠打刻リマインド】\n");
        sb.append(employee.fullName()).append(" さん\n\n");
        sb.append("出勤予定時刻を過ぎていますが、出勤打刻が確認できておりません。\n打刻または勤怠連絡をお願いします。\n");
        sb.append("システム検知のため、入れ違いとなりましたら申し訳ございません。\n\n");
        sb.append("※もしも検知が明らかに違う場合はお手数ですが所属上長にご確認ください");

        return sb.toString();
    }
}
