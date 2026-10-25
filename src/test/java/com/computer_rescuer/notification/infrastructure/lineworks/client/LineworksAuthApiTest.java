package com.computer_rescuer.notification.infrastructure.lineworks.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computer_rescuer.notification.infrastructure.property.LineworksProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link LineworksAuthApi} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * LINE WORKS API 2.0 の Service Account 認証（OAuth 2.0 JWT Bearer Token フロー）を実行します。<br>
 * <ul>
 *   <li>設定ファイルに定義されたパスから PKCS#8 形式の RSA 秘密鍵をパース・復元</li>
 *   <li>有効期限 30 分、Client ID、Service Account、ランダム JTI を含む JWT Assertion を生成し RS256 署名</li>
 *   <li>{@code application/x-www-form-urlencoded} 形式のリクエストボディを構築し、認証サーバーへ POST 送信</li>
 *   <li>取得したアクセストークン（Bearer Token）の返却、および障害発生時の業務向け {@link RuntimeException} ラップ</li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * テスト実行時に有効な 2048bit RSA 鍵ペアを動的生成してテンポラリファイルへ書き出すことで、
 * ファイル読み込みから Base64 デコード、JWT 署名、公開鍵による署名検証までを一気通貫でテストします。
 * {@code @SuppressWarnings} は一切使用せず、全テストメソッドに詳細な JavaDoc を完備します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineworksAuthApi 単体テスト")
class LineworksAuthApiTest {

  @Mock
  private RestClient.Builder restClientBuilder;

  @Mock
  private RestClient restClient;

  @Mock
  private RestClient.RequestBodyUriSpec requestBodyUriSpec;

  @Mock
  private RestClient.RequestBodySpec requestBodySpec;

  @Mock
  private RestClient.ResponseSpec responseSpec;

  @Captor
  private ArgumentCaptor<MultiValueMap<String, String>> bodyCaptor;

  @Captor
  private ArgumentCaptor<String> uriCaptor;

  @Captor
  private ArgumentCaptor<MediaType> mediaTypeCaptor;

  @TempDir
  private Path tempDir;

  private PublicKey testPublicKey;
  private Path validKeyFilePath;
  private LineworksProperties properties;
  private LineworksAuthApi sut;

  /**
   * 各テスト実行前の共通初期化処理。
   * <p>
   * 有効な RSA 秘密鍵ファイルを一時ディレクトリに作成し、{@link RestClient} のチェーンモックを構築します。
   * </p>
   *
   * @throws Exception 鍵ペアの生成またはファイル書き込みに失敗した場合
   */
  @BeforeEach
  void setUp() throws Exception {
    // 1. 本物の 2048bit RSA 鍵ペアを生成してテスト用公開鍵を保持
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();
    this.testPublicKey = keyPair.getPublic();

    // 2. PKCS#8 形式の PEM 秘密鍵文字列を構築して一時ファイルへ保存
    byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
    String base64PrivateKey = Base64.getEncoder().encodeToString(privateKeyBytes);
    String pemContent =
        "-----BEGIN PRIVATE KEY-----\n" + base64PrivateKey + "\n-----END PRIVATE KEY-----\n";

    this.validKeyFilePath = tempDir.resolve("test_private.key");
    Files.writeString(validKeyFilePath, pemContent);

    // 3. テスト用プロパティの初期化
    this.properties = new LineworksProperties(
        "https://api.worksmobile.com/r/dummy",
        "test-client-id-001",
        "test-client-secret-xyz",
        "test-service-account@service",
        validKeyFilePath.toAbsolutePath().toString(),
        "dummy-bot-id",
        "dummy-alert-channel-id",
        "dummy-system-manager-id"
    );

    // 4. RestClient Fluent API チェーンの型安全な事前スタブ
    lenient().when(restClientBuilder.build()).thenReturn(restClient);
    lenient().when(restClient.post()).thenReturn(requestBodyUriSpec);
    lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);

    this.sut = new LineworksAuthApi(restClientBuilder, properties);
  }

  /**
   * {@link LineworksAuthApi} 内の private record である {@code TokenResponse} のインスタンスを
   * リフレクションを用いて安全に生成するヘルパーメソッド。
   *
   * @param accessToken 返却するアクセストークン
   * @param expiresIn   有効秒数
   * @param tokenType   トークン種別（Bearer）
   * @return 生成された {@code TokenResponse} オブジェクト
   * @throws Exception リフレクションによるインスタンス化に失敗した場合
   */
  private Object createTokenResponse(String accessToken, Integer expiresIn, String tokenType)
      throws Exception {
    Class<?> tokenResponseClass = Class.forName(
        "com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksAuthApi$TokenResponse"
    );
    Constructor<?> constructor = tokenResponseClass.getDeclaredConstructor(
        String.class, Integer.class, String.class
    );
    constructor.setAccessible(true);
    return constructor.newInstance(accessToken, expiresIn, tokenType);
  }

  @Nested
  @DisplayName("正常系（アクセストークン取得成功）のテスト")
  class SuccessTest {

    /**
     * 【テスト観点 / 責務】<br> 秘密鍵のパース、JWT Assertion の生成・署名、認証エンドポイントへの POST 送信、
     * レスポンスからのトークン抽出の一連のフローが正常に完了することを確認します。<br> さらに、生成された JWT Assertion が有効な RS256
     * 署名を持ち、Claims（iss, sub, exp, jti）が 仕様通り設定されていることを公開鍵を用いて数学的に検証します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 有効な RSA 秘密鍵ファイルが存在。<br> - RestClient がアクセストークン
     * "issued-access-token-999" を含む TokenResponse を返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 戻り値として "issued-access-token-999" が取得できること。<br> - リクエスト URL が
     * "https://auth.worksmobile.com/oauth2/v2.0/token" であること。<br> - Content-Type が
     * {@link MediaType#APPLICATION_FORM_URLENCODED} であること。<br> - フォームデータに grant_type, client_id,
     * client_secret, scope が正しく含まれること。<br> - JWT Assertion の Claims（iss, sub, exp, jti）が完全一致すること。
     * </p>
     *
     * @throws Exception JWT の検証またはレスポンスモック生成に失敗した場合
     */
    @Test
    @DisplayName("有効な秘密鍵からJWTを生成・署名し、アクセストークンを正常に取得できること")
    public void fetchAccessToken_Success_ShouldReturnTokenAndVerifyJwtClaims() throws Exception {
      // Arrange
      String scopes = "bot,directory.read";
      Object mockTokenResponse = createTokenResponse("issued-access-token-999", 3600, "Bearer");
      when(responseSpec.body(any(Class.class))).thenReturn(mockTokenResponse);

      // Act
      String accessToken = sut.fetchAccessToken(scopes);

      // Assert
      assertThat(accessToken).isEqualTo("issued-access-token-999");

      // HTTP リクエストのメタデータ検証
      verify(requestBodyUriSpec).uri(uriCaptor.capture());
      assertThat(uriCaptor.getValue()).isEqualTo("https://auth.worksmobile.com/oauth2/v2.0/token");

      verify(requestBodySpec).contentType(mediaTypeCaptor.capture());
      assertThat(mediaTypeCaptor.getValue()).isEqualTo(MediaType.APPLICATION_FORM_URLENCODED);

      // 送信フォームパラメータの検証（Java 21/25 仕様の getFirst を使用）
      verify(requestBodySpec).body(bodyCaptor.capture());
      MultiValueMap<String, String> capturedBody = bodyCaptor.getValue();

      assertThat(capturedBody.getFirst("grant_type")).isEqualTo(
          "urn:ietf:params:oauth:grant-type:jwt-bearer");
      assertThat(capturedBody.getFirst("client_id")).isEqualTo("test-client-id-001");
      assertThat(capturedBody.getFirst("client_secret")).isEqualTo("test-client-secret-xyz");
      assertThat(capturedBody.getFirst("scope")).isEqualTo("bot,directory.read");

      // JWT Assertion の厳密な暗号学的検証
      String assertionJwt = capturedBody.getFirst("assertion");
      assertThat(assertionJwt).isNotBlank();

      Claims claims = Jwts.parser()
          .verifyWith(testPublicKey) // テスト用公開鍵で RS256 署名を検証
          .build()
          .parseSignedClaims(assertionJwt)
          .getPayload();

      assertThat(claims.getIssuer()).isEqualTo("test-client-id-001");
      assertThat(claims.getSubject()).isEqualTo("test-service-account@service");
      assertThat(claims.getId()).isNotBlank(); // ランダムな UUID (jti)
      assertThat(claims.getIssuedAt()).isBeforeOrEqualTo(new Date());
      assertThat(claims.getExpiration()).isAfter(new Date()); // 有効期限が未来であること
    }
  }

  @Nested
  @DisplayName("異常系（鍵ファイル・暗号処理エラー）のテスト")
  class KeyParsingExceptionTest {

    /**
     * 【テスト観点 / 責務】<br> 指定されたパスに秘密鍵ファイルが存在しない場合、 内部の例外が捕捉され、適切なメッセージを持つ {@link RuntimeException}
     * がスローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - プロパティの privateKey パスに、実在しないファイルパスを設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link RuntimeException} がスローされること。<br> - 例外メッセージが「LINE WORKS
     * トークン取得失敗」であること。<br> - 原因例外（Cause）が存在すること。
     * </p>
     */
    @Test
    @DisplayName("秘密鍵ファイルが存在しない場合、RuntimeExceptionにラップしてスローすること")
    public void fetchAccessToken_WhenPrivateKeyFileDoesNotExist_ShouldThrowRuntimeException() {
      // Arrange
      LineworksProperties invalidPathProps = new LineworksProperties(
          properties.baseUrl(),
          properties.clientId(),
          properties.clientSecret(),
          properties.serviceAccount(),
          tempDir.resolve("non_existent_key.pem").toString(),
          properties.botId(),
          properties.alertChannelId(),
          properties.systemManagerId()
      );
      LineworksAuthApi serviceWithInvalidPath = new LineworksAuthApi(restClientBuilder,
          invalidPathProps);

      // Act & Assert
      assertThatThrownBy(() -> serviceWithInvalidPath.fetchAccessToken("bot"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS トークン取得失敗")
          .hasCauseInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    /**
     * 【テスト観点 / 責務】<br> 秘密鍵ファイルの内容が破損（不正な Base64 文字列や無効な鍵フォーマット）している場合、 パース処理で発生した例外が捕捉され、業務向け
     * {@link RuntimeException} がスローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 秘密鍵ファイルに無効な文字列（"CORRUPTED_KEY_DATA"）を書き込む。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link RuntimeException} がスローされること。<br> - 例外メッセージが「LINE WORKS
     * トークン取得失敗」であること。
     * </p>
     *
     * @throws Exception ファイル書き込みに失敗した場合
     */
    @Test
    @DisplayName("秘密鍵ファイルが破損している場合、RuntimeExceptionにラップしてスローすること")
    public void fetchAccessToken_WhenPrivateKeyIsCorrupted_ShouldThrowRuntimeException()
        throws Exception {
      // Arrange: 不正な内容の秘密鍵ファイルを作成
      Path corruptedKeyPath = tempDir.resolve("corrupted.key");
      Files.writeString(corruptedKeyPath,
          "-----BEGIN PRIVATE KEY-----\nINVALID_BASE64_KEY\n-----END PRIVATE KEY-----");

      LineworksProperties corruptedProps = new LineworksProperties(
          properties.baseUrl(),
          properties.clientId(),
          properties.clientSecret(),
          properties.serviceAccount(),
          corruptedKeyPath.toString(),
          properties.botId(),
          properties.alertChannelId(),
          properties.systemManagerId()
      );
      LineworksAuthApi serviceWithCorruptedKey = new LineworksAuthApi(restClientBuilder,
          corruptedProps);

      // Act & Assert
      assertThatThrownBy(() -> serviceWithCorruptedKey.fetchAccessToken("bot"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS トークン取得失敗");
    }
  }

  @Nested
  @DisplayName("異常系（外部 HTTP 通信・レスポンスエラー）のテスト")
  class HttpCommunicationExceptionTest {

    /**
     * 【テスト観点 / 責務】<br> 認証エンドポイントへの HTTP POST リクエスト送信時に外部例外（通信エラー等）が発生した場合、 安全に捕捉されて業務向け
     * {@link RuntimeException} にラップされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 秘密鍵は正常。<br> - {@code responseSpec.body(any(Class.class))} が
     * {@link RestClientException} をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link RuntimeException} がスローされること。<br> - メッセージが「LINE WORKS
     * トークン取得失敗」であること。<br> - Cause に {@link RestClientException} がセットされていること。
     * </p>
     */
    @Test
    @DisplayName("RestClient通信時に例外が発生した場合、RuntimeExceptionにラップしてスローすること")
    public void fetchAccessToken_WhenRestClientThrowsException_ShouldThrowRuntimeException() {
      // Arrange
      RestClientException restException = new RestClientException(
          "Connection timed out to auth server");
      when(responseSpec.body(any(Class.class))).thenThrow(restException);

      // Act & Assert
      assertThatThrownBy(() -> sut.fetchAccessToken("bot"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS トークン取得失敗")
          .hasCause(restException);
    }

    /**
     * 【テスト観点 / 責務】<br> 認証エンドポイントからのレスポンスボディが null であった場合、 NullPointerException
     * が内部で発生しても安全に捕捉され、{@link RuntimeException} として上位へ通知されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code responseSpec.body(any(Class.class))} が null を返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link RuntimeException} がスローされること。<br> - メッセージが「LINE WORKS
     * トークン取得失敗」であること。<br> - Cause に {@link NullPointerException} がセットされていること。
     * </p>
     */
    @Test
    @DisplayName("レスポンスボディがnullの場合、NullPointerExceptionをラップしてスローすること")
    public void fetchAccessToken_WhenResponseBodyIsNull_ShouldThrowWrappedRuntimeException() {
      // Arrange: body が null を返却
      when(responseSpec.body(any(Class.class))).thenReturn(null);

      // Act & Assert
      assertThatThrownBy(() -> sut.fetchAccessToken("bot"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS トークン取得失敗")
          .hasCauseInstanceOf(NullPointerException.class);
    }
  }
}
