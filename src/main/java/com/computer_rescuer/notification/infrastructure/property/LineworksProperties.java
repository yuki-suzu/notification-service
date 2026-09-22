package com.computer_rescuer.notification.infrastructure.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LINE WORKS APIの設定値を保持するレコード（不変オブジェクト）。 application.yml の app.line-works 配下の値を型安全にバインドします。
 */
@ConfigurationProperties(prefix = "app.line-works")
public record LineworksProperties(
    String baseUrl,
    String clientId,
    String clientSecret,
    String serviceAccount,
    String privateKey,
    String botId,
    String alertChannelId,
    String systemManagerId
) {

}
