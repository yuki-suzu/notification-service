package com.computer_rescuer.notification.domain.model;

/**
 * サポートする通知配信チャネルの種別を定義する列挙型。
 * <p>
 * システムが対応する外部通知プラットフォーム（LINE WORKS、Slack、メールなど）を一元管理します。
 * </p>
 */
public enum NotificationChannelType {
  /**
   * LINE WORKS チャット/トークルーム通知
   */
  LINE_WORKS,
  /**
   * Slack チャンネル通知（将来拡張用）
   */
  SLACK,
  /**
   * Eメール通知（将来拡張用）
   */
  EMAIL
}
