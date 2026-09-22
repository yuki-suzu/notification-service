package com.computer_rescuer.notification.infrastructure.lineworks.client;

import com.computer_rescuer.notification.infrastructure.property.LineworksProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * LINE WORKS の Service Account 認証（JWT Bearer Grant）を担当する API クライアント。
 * <p>
 * 人間のブラウザ操作（同意画面）を伴わないサーバー間通信（M2M）を実現するため、 設定ファイルに定義された秘密鍵（Private Key）を用いて動的に JWT を生成・署名し、 LINE
 * WORKS の認証サーバーからアクセストークンを取得します。<br> 通信基盤には Spring Boot 3.2 以降で推奨されるモダンな {@link RestClient}
 * を使用しています。
 * </p>
 */
@Slf4j
@Component
public class LineworksAuthApi {

  /**
   * LINE WORKS API 2.0 のトークン取得エンドポイント
   */
  private static final String AUTH_URL = "https://auth.worksmobile.com/oauth2/v2.0/token";
  /**
   * HTTPリクエストを送信するためのモダンなクライアント
   */
  private final RestClient restClient;
  /**
   * LINE WORKS の設定値（Client ID 等）を保持するプロパティ
   */
  private final LineworksProperties properties;

  /**
   * コンストラクタ。 Spring が管理する RestClient.Builder を注入し、クライアントインスタンスを構築します。
   *
   * @param restClientBuilder Spring Boot の自動構成によるビルダー
   * @param properties        LINE WORKS 設定プロパティ
   */
  public LineworksAuthApi(RestClient.Builder restClientBuilder, LineworksProperties properties) {
    this.restClient = restClientBuilder.build();
    this.properties = properties;
  }

  /**
   * 秘密鍵で署名した JWT を用いて、新しいアクセストークンを取得します。
   *
   * @param scopes 取得するトークンに付与する権限（例: "bot,directory.read"）。複数ある場合はカンマ区切り。
   * @return LINE WORKS API 呼び出しに使用するアクセストークン（Bearer トークン）
   * @throws RuntimeException 通信エラー、または秘密鍵のパースに失敗した場合
   */
  public String fetchAccessToken(String scopes) {
    try {
      // 1. JWT (Assertion) の生成と署名
      String jwt = createJwtAssertion();

      // 2. Token エンドポイントへのリクエストボディ作成 (x-www-form-urlencoded 形式)
      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("assertion", jwt);
      body.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
      body.add("client_id", properties.clientId());
      body.add("client_secret", properties.clientSecret());
      body.add("scope", scopes);

      // 3. RestClient を使用した API リクエストの実行と JSON マッピング
      TokenResponse response = restClient.post()
          .uri(AUTH_URL)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(body)
          .retrieve()
          .body(TokenResponse.class);

      log.debug("LINE WORKS アクセストークンの新規取得に成功しました。");
      return response.accessToken();

    } catch (Exception e) {
      log.error("❌ LINE WORKS トークン取得に失敗しました: {}", e.getMessage(), e);
      throw new RuntimeException("LINE WORKS トークン取得失敗", e);
    }
  }

  /**
   * LINE WORKS の仕様に基づく JWT ペイロードを構築し、秘密鍵で RS256 署名を行います。
   *
   * @return 署名済みの JWT 文字列
   * @throws Exception 秘密鍵ファイルの読み込みや署名処理に失敗した場合
   */
  private String createJwtAssertion() throws Exception {
    long nowMillis = System.currentTimeMillis();
    Date now = new Date(nowMillis);
    // JWT 自身の有効期限（LINE WORKS の仕様では最大1時間。ここでは安全マージンを取り30分とする）
    Date exp = new Date(nowMillis + 1800000);

    PrivateKey privateKey = getPrivateKeyFromPath(properties.privateKey());

    return Jwts.builder()
        .header().add("alg", "RS256").add("typ", "JWT").and()
        .issuer(properties.clientId())           // iss: Client ID
        .subject(properties.serviceAccount())    // sub: Service Account
        .issuedAt(now)                           // iat: 発行時刻
        .expiration(exp)                         // exp: 有効期限
        .id(UUID.randomUUID().toString())        // jti: リプレイ攻撃防止用のランダム識別子
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  /**
   * 指定されたファイルパスから PEM 形式の秘密鍵を読み込み、Java の PrivateKey オブジェクトを復元します。
   *
   * @param pathStr 秘密鍵ファイル (.key / .pem) の絶対または相対パス
   * @return 復元された PrivateKey オブジェクト
   * @throws Exception ファイルの読み込み、Base64 デコード、または鍵の生成に失敗した場合
   */
  private PrivateKey getPrivateKeyFromPath(String pathStr) throws Exception {
    // 1. ファイルから文字列として読み込む
    String keyStr = Files.readString(Path.of(pathStr));

    // 2. PEM ヘッダー/フッターおよび不要な改行や空白を除去
    String privateKeyPEM = keyStr
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s", "");

    // 3. バイト配列にデコードしてキーファクトリに渡す
    byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
  }

  /**
   * トークンエンドポイントからの JSON レスポンスをマッピングするための内部レコード。
   */
  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Integer expiresIn,
      @JsonProperty("token_type") String tokenType
  ) {

  }
}
