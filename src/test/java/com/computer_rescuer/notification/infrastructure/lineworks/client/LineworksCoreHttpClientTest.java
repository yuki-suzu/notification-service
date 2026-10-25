package com.computer_rescuer.notification.infrastructure.lineworks.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computer_rescuer.notification.infrastructure.property.LineworksProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * {@link LineworksCoreHttpClient} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * LINE WORKS API 2.0 呼び出しの共通 HTTP 通信基盤として機能します。<br>
 * <ul>
 *   <li>ベース URL の一括解決および各リクエストへの Authorization ヘッダー（Bearer トークン）の自動付与</li>
 *   <li><b>【最重要】401 Unauthorized（トークン失効）検知時におけるトークン再取得（{@code refreshToken}）と1回限定の自動自己修復リトライ</b></li>
 *   <li>POST 通信時における 401 以外の HTTP エラー／通信例外の業務向け {@link RuntimeException} ラップ</li>
 *   <li>GET 通信時における 404 Not Found 等の個別ステータス例外の透過的伝播（呼び出し元での不在判定用）</li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * {@link RestClient} の Fluent API チェーンを型安全にモック化し、
 * コンパイル警告の抑制アノテーション（{@code @SuppressWarnings}）を一切使用せずに構築します。
 * トークン正常時・401失効リカバリ時・リトライ失敗時・サーバー障害時における全条件分岐（C1カバレッジ 100%）を検証します。
 * コレクションアクセスには Java 21/25 仕様の {@code getFirst()} / {@code getLast()} を使用します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineworksCoreHttpClient 単体テスト")
class LineworksCoreHttpClientTest {

  @Mock
  private RestClient.Builder restClientBuilder;

  @Mock
  private RestClient restClient;

  @Mock
  private RestClient.RequestBodyUriSpec requestBodyUriSpec;

  @Mock
  private RestClient.RequestBodySpec requestBodySpec;

  @Mock
  private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

  @Mock
  private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

  @Mock
  private RestClient.ResponseSpec responseSpec;

  @Mock
  private LineworksTokenManager tokenManager;

  @Captor
  private ArgumentCaptor<String> headerNameCaptor;

  @Captor
  private ArgumentCaptor<String> headerValueCaptor;

  private LineworksCoreHttpClient sut;

  private final LineworksProperties properties = new LineworksProperties(
      "https://api.worksmobile.com/r/dummy",
      "dummy-client-id",
      "dummy-client-secret",
      "dummy-service-account",
      "dummy-private-key",
      "dummy-bot-id",
      "dummy-alert-channel-id",
      "dummy-system-manager-id"
  );

  /**
   * 各テスト実行前の共通初期化処理。
   * <p>
   * {@link RestClient.Builder} をモック化してテスト対象インスタンスを構築するとともに、 Fluent API の共通チェーンを型安全に事前設定します。
   * </p>
   */
  @BeforeEach
  void setUp() {
    lenient().when(restClientBuilder.baseUrl(properties.baseUrl())).thenReturn(restClientBuilder);
    lenient().when(restClientBuilder.build()).thenReturn(restClient);

    // POST 送信チェーンの型安全な共通スタブ（body には Object.class を明示してオーバーロードの曖昧さを排除）
    lenient().when(restClient.post()).thenReturn(requestBodyUriSpec);
    lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
    lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);

    // GET 送信チェーンの型安全な共通スタブ（ワイルドカード型に対応した doReturn で警告を完全排除）
    lenient().doReturn(requestHeadersUriSpec).when(restClient).get();
    lenient().doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
    lenient().doReturn(requestHeadersSpec).when(requestHeadersSpec)
        .header(anyString(), anyString());
    lenient().doReturn(requestHeadersSpec).when(requestHeadersSpec).accept(any(MediaType.class));
    lenient().doReturn(responseSpec).when(requestHeadersSpec).retrieve();

    sut = new LineworksCoreHttpClient(restClientBuilder, properties, tokenManager);
  }

  /**
   * テスト用の 401 Unauthorized 例外オブジェクトを生成するヘルパーメソッド。
   *
   * @return {@link HttpClientErrorException.Unauthorized} インスタンス
   */
  private HttpClientErrorException.Unauthorized createUnauthorizedException() {
    return (HttpClientErrorException.Unauthorized) HttpClientErrorException.create(
        HttpStatus.UNAUTHORIZED,
        "Unauthorized",
        HttpHeaders.EMPTY,
        new byte[0],
        StandardCharsets.UTF_8
    );
  }

  @Nested
  @DisplayName("POST リクエスト処理のテスト")
  class PostRequestTest {

    /**
     * 【テスト観点 / 責務】<br> 初回取得したアクセストークンを用いて POST リクエストが正常に送信され、 Authorization ヘッダーに Bearer
     * トークンが正しく設定されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - トークンマネージャーが有効なトークン "valid-token-123" を返却。<br> - RestClient が
     * 200 OK の {@link ResponseEntity} を返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外なく正常終了すること。<br> - {@code tokenManager.refreshToken()}
     * は一度も呼び出されないこと。<br> - Authorization ヘッダーに "Bearer valid-token-123" が設定されていること。
     * </p>
     */
    @Test
    @DisplayName("初回トークンでPOST送信が成功し、リフレッシュが呼ばれないこと")
    public void post_SuccessOnFirstAttempt_ShouldSendRequestWithoutRefresh() {
      // Arrange
      String path = "/bots/dummy-bot/messages";
      String requestBody = "{\"text\":\"Hello\"}";
      String apiName = "メッセージ送信";

      when(tokenManager.getToken()).thenReturn("valid-token-123");
      when(responseSpec.toEntity(String.class)).thenReturn(
          ResponseEntity.ok("{\"result\":\"ok\"}"));

      // Act
      sut.post(path, requestBody, apiName);

      // Assert
      verify(tokenManager, times(1)).getToken();
      verify(tokenManager, never()).refreshToken();

      verify(requestBodySpec).header(headerNameCaptor.capture(), headerValueCaptor.capture());
      assertThat(headerNameCaptor.getValue()).isEqualTo(HttpHeaders.AUTHORIZATION);
      assertThat(headerValueCaptor.getValue()).isEqualTo("Bearer valid-token-123");
    }

    /**
     * 【テスト観点 / 責務】<br> 初回 POST 送信時に 401 Unauthorized
     * が発生した場合、自己修復としてトークンを強制再取得（{@code refreshToken}）し、 新しいトークンを用いて 1
     * 度だけ自動リトライを実行して正常復帰することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 初回 {@code getToken()} は "expired-token" を返却。<br> -
     * 1回目のリクエスト実行時に {@link HttpClientErrorException.Unauthorized} が発生。<br> -
     * {@code tokenManager.refreshToken()} が新トークン "refreshed-token-456" を返却。<br> - 2回目のリクエスト実行時に 200
     * OK が返却される。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外なく正常終了すること。<br> - {@code tokenManager.refreshToken()} が正確に 1
     * 回呼び出されること。<br> - リトライ時の Authorization ヘッダーに "Bearer refreshed-token-456" が適用されていること。
     * </p>
     */
    @Test
    @DisplayName("401発生時にトークンを再取得してリトライし、処理を復旧すること")
    public void post_WhenUnauthorizedOnFirstAttempt_ShouldRefreshTokenAndRetrySuccessfully() {
      // Arrange
      String path = "/bots/dummy-bot/messages";
      String requestBody = "{\"text\":\"Retry Test\"}";
      String apiName = "リトライテスト";

      when(tokenManager.getToken()).thenReturn("expired-token");
      when(tokenManager.refreshToken()).thenReturn("refreshed-token-456");

      // 1回目は401スロー、2回目（リトライ）は200 OK成功
      when(responseSpec.toEntity(String.class))
          .thenThrow(createUnauthorizedException())
          .thenReturn(ResponseEntity.ok("{\"result\":\"recovered\"}"));

      // Act
      sut.post(path, requestBody, apiName);

      // Assert
      verify(tokenManager, times(1)).getToken();
      verify(tokenManager, times(1)).refreshToken();

      verify(requestBodySpec, times(2)).header(headerNameCaptor.capture(),
          headerValueCaptor.capture());
      List<String> capturedTokens = headerValueCaptor.getAllValues();
      assertThat(capturedTokens).hasSize(2);
      assertThat(capturedTokens.getFirst()).isEqualTo("Bearer expired-token");
      assertThat(capturedTokens.getLast()).isEqualTo("Bearer refreshed-token-456");
    }

    /**
     * 【テスト観点 / 責務】<br> 401 後のリトライ通信においても再度 401 Unauthorized が発生した場合、
     * 無限ループを防ぐため自己修復を中断し、呼び出し元へ例外を再スローすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 1回目、2回目の通信ともに 401 Unauthorized をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link HttpClientErrorException.Unauthorized} がそのままスローされること。<br>
     * - リトライは 1 回のみ行われ、合計 2 回の試行で停止すること。
     * </p>
     */
    @Test
    @DisplayName("リトライ後も再度401が発生した場合、Unauthorized例外が再スローされること")
    public void post_WhenUnauthorizedPersistsAfterRetry_ShouldRethrowUnauthorized() {
      // Arrange
      String path = "/bots/dummy-bot/messages";
      String requestBody = "{\"text\":\"Fail Twice\"}";

      when(tokenManager.getToken()).thenReturn("token-1");
      when(tokenManager.refreshToken()).thenReturn("token-2");

      // 2回連続で401が発生
      when(responseSpec.toEntity(String.class))
          .thenThrow(createUnauthorizedException());

      // Act & Assert
      assertThatThrownBy(() -> sut.post(path, requestBody, "連続401テスト"))
          .isInstanceOf(HttpClientErrorException.Unauthorized.class);

      verify(tokenManager, times(1)).getToken();
      verify(tokenManager, times(1)).refreshToken();
      verify(responseSpec, times(2)).toEntity(String.class);
    }

    /**
     * 【テスト観点 / 責務】<br> 初回通信で 401 以外の HTTP エラー（例: 500 Internal Server Error）が発生した場合、
     * 無駄なトークンリフレッシュを行わず、業務向け {@link RuntimeException} にラップしてスローすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 通信時に 500 Internal Server Error が発生。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link RuntimeException} がスローされること。<br> - エラーメッセージが「LINE WORKS
     * API (サーバー障害テスト) の通信エラー」であること。<br> - {@code tokenManager.refreshToken()} が呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("401以外のHTTPエラー発生時はリトライせず、RuntimeExceptionにラップしてスローすること")
    public void post_WhenServerErrorOccurs_ShouldWrapInRuntimeExceptionWithoutRetry() {
      // Arrange
      String path = "/bots/dummy-bot/messages";
      when(tokenManager.getToken()).thenReturn("valid-token");

      HttpServerErrorException serverError = new HttpServerErrorException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Server Error");
      when(responseSpec.toEntity(String.class)).thenThrow(serverError);

      // Act & Assert
      assertThatThrownBy(() -> sut.post(path, "{}", "サーバー障害テスト"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS API (サーバー障害テスト) の通信エラー")
          .hasCause(serverError);

      verify(tokenManager, never()).refreshToken();
    }

    /**
     * 【テスト観点 / 責務】<br> 401 によるリトライ試行時に 401 以外の通信例外が発生した場合、 リトライ明記の API 識別名を付与した
     * {@link RuntimeException} がスローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 1回目は 401 Unauthorized が発生。<br> - リフレッシュ後の 2
     * 回目でネットワーク切断（{@link IllegalStateException}）が発生。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - メッセージ「LINE WORKS API (リトライ障害テスト (リトライ)) の通信エラー」を持つ
     * {@link RuntimeException} がスローされること。
     * </p>
     */
    @Test
    @DisplayName("リトライ中に通信エラーが発生した場合、リトライ明記のRuntimeExceptionがスローされること")
    public void post_WhenGeneralExceptionOccursDuringRetry_ShouldThrowWrappedRuntimeException() {
      // Arrange
      String path = "/bots/dummy-bot/messages";
      when(tokenManager.getToken()).thenReturn("token-1");
      when(tokenManager.refreshToken()).thenReturn("token-2");

      when(responseSpec.toEntity(String.class))
          .thenThrow(createUnauthorizedException())
          .thenThrow(new IllegalStateException("Connection timed out"));

      // Act & Assert
      assertThatThrownBy(() -> sut.post(path, "{}", "リトライ障害テスト"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS API (リトライ障害テスト (リトライ)) の通信エラー");

      verify(tokenManager, times(1)).getToken();
      verify(tokenManager, times(1)).refreshToken();
    }
  }

  @Nested
  @DisplayName("GET リクエスト処理のテスト")
  class GetRequestTest {

    private record TestUserDto(String id, String name) {

    }

    /**
     * 【テスト観点 / 責務】<br> 初回取得トークンを用いて GET リクエストが正常に送信され、 レスポンスボディが要求された DTO
     * クラスに正しくデシリアライズされて返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - トークンマネージャーが "get-token-123" を返却。<br> - レスポンスとして
     * {@link TestUserDto} インスタンスを返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却された DTO オブジェクトのプロパティが期待値と完全一致すること。<br> - Authorization ヘッダーに
     * Bearer トークンが正しく設定されていること。<br> - {@code tokenManager.refreshToken()} は一度も呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("初回トークンでGET通信が成功し、型安全にオブジェクトを返却すること")
    public void get_SuccessOnFirstAttempt_ShouldReturnDeserializedBody() {
      // Arrange
      String path = "/users/user-001";
      TestUserDto expectedResponse = new TestUserDto("user-001", "山田 太郎");

      when(tokenManager.getToken()).thenReturn("get-token-123");
      when(responseSpec.body(TestUserDto.class)).thenReturn(expectedResponse);

      // Act
      TestUserDto actual = sut.get(path, TestUserDto.class, "ユーザー情報取得");

      // Assert
      assertThat(actual).isEqualTo(expectedResponse);

      verify(requestHeadersSpec).header(headerNameCaptor.capture(), headerValueCaptor.capture());
      assertThat(headerNameCaptor.getValue()).isEqualTo(HttpHeaders.AUTHORIZATION);
      assertThat(headerValueCaptor.getValue()).isEqualTo("Bearer get-token-123");

      verify(tokenManager, never()).refreshToken();
    }

    /**
     * 【テスト観点 / 責務】<br> GET 送信時に 401 Unauthorized が発生した場合、トークンを再取得してリトライし、
     * 正常にデシリアライズされた結果を返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 1回目の呼び出しで 401 Unauthorized が発生。<br> - トークン再取得により
     * "refreshed-get-token" が返却。<br> - 2回目の呼び出しで正常レスポンスが返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - リトライ後のレスポンスが正常に返却されること。<br> - トークンのリフレッシュが実行されること。<br> - 2回目の
     * Authorization ヘッダーに新トークンが適用されていること。
     * </p>
     */
    @Test
    @DisplayName("GET通信で401発生時にトークン再取得してリトライ成功すること")
    public void get_WhenUnauthorizedOnFirstAttempt_ShouldRefreshTokenAndRetrySuccessfully() {
      // Arrange
      String path = "/users/user-002";
      TestUserDto expectedResponse = new TestUserDto("user-002", "佐藤 次郎");

      when(tokenManager.getToken()).thenReturn("expired-get-token");
      when(tokenManager.refreshToken()).thenReturn("refreshed-get-token");

      // 1回目401スロー、2回目正常
      when(responseSpec.body(TestUserDto.class))
          .thenThrow(createUnauthorizedException())
          .thenReturn(expectedResponse);

      // Act
      TestUserDto actual = sut.get(path, TestUserDto.class, "ユーザー取得リトライ");

      // Assert
      assertThat(actual).isEqualTo(expectedResponse);
      verify(tokenManager, times(1)).getToken();
      verify(tokenManager, times(1)).refreshToken();

      verify(requestHeadersSpec, times(2)).header(headerNameCaptor.capture(),
          headerValueCaptor.capture());
      List<String> capturedTokens = headerValueCaptor.getAllValues();
      assertThat(capturedTokens).hasSize(2);
      assertThat(capturedTokens.getFirst()).isEqualTo("Bearer expired-get-token");
      assertThat(capturedTokens.getLast()).isEqualTo("Bearer refreshed-get-token");
    }

    /**
     * 【テスト観点 / 責務】<br> GET 送信時に 404 Not Found が発生した場合、トークン失効ではないためリフレッシュを行わず、 呼び出し元がハンドリング（例:
     * {@code LineworksUserApi} の不在判定）できるよう例外を透過的に伝播することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code responseSpec.body(...)} が
     * {@link HttpClientErrorException.NotFound} をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link HttpClientErrorException.NotFound}
     * がラップされずにそのままスローされること。<br> - {@code tokenManager.refreshToken()} は一度も呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("404 Not Found発生時はトークンリフレッシュせず、NotFound例外をそのままスローすること")
    public void get_WhenNotFoundOccurs_ShouldRethrowNotFoundWithoutRefresh() {
      // Arrange
      String path = "/users/non-existent-user";
      when(tokenManager.getToken()).thenReturn("valid-token");

      HttpClientErrorException.NotFound notFoundException = (HttpClientErrorException.NotFound) HttpClientErrorException.create(
          HttpStatus.NOT_FOUND,
          "User Not Found",
          HttpHeaders.EMPTY,
          new byte[0],
          StandardCharsets.UTF_8
      );
      when(responseSpec.body(TestUserDto.class)).thenThrow(notFoundException);

      // Act & Assert
      assertThatThrownBy(() -> sut.get(path, TestUserDto.class, "存在確認"))
          .isInstanceOf(HttpClientErrorException.NotFound.class);

      verify(tokenManager, never()).refreshToken();
    }
  }
}
