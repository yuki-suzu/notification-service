package com.computer_rescuer.notification.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP通信クライアントの構成クラス。
 * <p>
 * アプリケーション内で使用する {@link RestClient.Builder} をDIコンテナに登録します。
 * </p>
 */
@Configuration
public class RestClientConfig {

    /**
     * 共通の RestClient ビルダーを生成します。
     *
     * @return RestClient.Builder インスタンス
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
