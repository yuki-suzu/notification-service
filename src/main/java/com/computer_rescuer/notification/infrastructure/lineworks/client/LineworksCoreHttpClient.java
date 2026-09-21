package com.computer_rescuer.notification.infrastructure.lineworks.client;

import com.computer_rescuer.notification.infrastructure.property.LineworksProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * LINE WORKS API 呼び出しの共通基盤となる HTTP クライアント。
 * <p>
 * このクラスは、以下の共通処理をカプセル化します：<br> 1. ベース URL の解決と認証ヘッダー（Bearer Token）の付与<br> 2. ログ出力（リクエスト /
 * レスポンス）<br> 3. <b>【重要】トークン失効（401 Unauthorized）時の自動リトライ処理</b><br> 各個別 API
 * クライアント（メッセージ送信やユーザー取得など）は、このクラスを経由して通信を行います。
 * </p>
 */
@Slf4j
@Component
public class LineworksCoreHttpClient {

    /**
     * 共通設定（Base URL 等）が適用済みの RestClient インスタンス
     */
    private final RestClient restClient;
    /**
     * LINE WORKS の設定値
     */
    private final LineworksProperties properties;
    /**
     * アクセストークンのキャッシュと再取得を管理するマネージャー
     */
    private final LineworksTokenManager tokenManager;

    /**
     * コンストラクタ。 Spring が管理する RestClient.Builder を用いて、LINE WORKS API 共通の BaseURL を
     * あらかじめ設定したクライアントインスタンスを構築します。
     *
     * @param restClientBuilder Spring Boot の自動構成によるビルダー
     * @param properties        LINE WORKS 設定プロパティ
     * @param tokenManager      トークン管理コンポーネント
     */
    public LineworksCoreHttpClient(
            RestClient.Builder restClientBuilder,
            LineworksProperties properties,
            LineworksTokenManager tokenManager
    ) {
        // 毎回 URL をフルで書かなくて済むよう、BaseURL をセットした状態のクライアントを作っておく
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.tokenManager = tokenManager;
    }

    /**
     * 指定されたエンドポイントに対して POST リクエストを送信します。
     * <p>
     * キャッシュされたトークンで通信を試み、もし 401 エラーが返却された場合は、 トークンマネージャーを通じてトークンを再取得し、1度だけ自動リトライを行います。
     * </p>
     *
     * @param path    API のエンドポイントパス（BaseURL 以降のパス。例: "/bots/.../messages"）
     * @param request 送信するリクエストボディのオブジェクト（JSON にシリアライズされます）
     * @param apiName ログ出力用の API 識別名（例: "テキストメッセージ送信"）
     * @throws RuntimeException リトライ後も失敗した場合、または 401 以外の致命的なエラーの場合
     */
    public void post(String path, Object request, String apiName) {
        // まずはキャッシュされている現在のトークンを使用する
        String token = tokenManager.getToken();

        try {
            executePost(token, path, request, apiName);
        } catch (HttpClientErrorException.Unauthorized e) {
            // 401 Unauthorized の場合はトークンの有効期限切れとみなし、自己修復（リトライ）を試みる
            log.warn(
                    "🚨 [LINE WORKS {}] 401 Unauthorized エラーを検知。トークンを再取得してリトライします。",
                    apiName);

            String newToken = tokenManager.refreshToken();
            executePost(newToken, path, request, apiName + " (リトライ)");

            log.info("✅ [LINE WORKS {}] リトライに成功し、処理を復旧しました。", apiName);
        }
    }

    /**
     * RestClient の Fluent API（メソッドチェーン）を使用して、実際の HTTP POST 通信を実行する内部メソッド。
     *
     * @param token   使用する Bearer トークン
     * @param path    API パス
     * @param request リクエストボディ
     * @param apiName ログ出力用の API 識別名
     */
    private void executePost(String token, String path, Object request, String apiName) {
        log.debug("▶︎ [LINE WORKS {} Request]: URI={}, Body={}", apiName, path, request);

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(path) // BaseURL はコンストラクタで設定済みのため、パスのみでOK
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);

            log.debug("◀︎ [LINE WORKS {} Response]: Status={}", apiName, response.getStatusCode());

        } catch (HttpClientErrorException.Unauthorized e) {
            // 401 の場合は呼び出し元の post メソッドでリトライハンドリングさせるため、そのままスローする
            throw e;
        } catch (Exception e) {
            log.error("❌ [LINE WORKS {} Error]: 通信に失敗しました。URI={}, エラー: {}", apiName, path,
                    e.getMessage());
            throw new RuntimeException("LINE WORKS API (" + apiName + ") の通信エラー", e);
        }
    }
}
