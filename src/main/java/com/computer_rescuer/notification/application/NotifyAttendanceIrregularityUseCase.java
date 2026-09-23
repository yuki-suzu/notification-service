package com.computer_rescuer.notification.application;

import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent;

/**
 * 月次勤怠サマリ（勤怠異常）イベントを受け付け、管理者トークルームへ通知するユースケース。
 */
public interface NotifyAttendanceIrregularityUseCase {

  /**
   * 月次勤怠サマリエベントを処理し、管理者チャンネルへ通知を配信します。
   *
   * @param event 受信した月次勤怠サマリエベント
   */
  void execute(AttendanceIrregularityEvent event);
}
