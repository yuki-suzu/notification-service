package com.computer_rescuer.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 通知マイクロサービス（notification-service）のエントリーポイント。
 */
@SpringBootApplication
public class NotificationServiceApplication {

  static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
