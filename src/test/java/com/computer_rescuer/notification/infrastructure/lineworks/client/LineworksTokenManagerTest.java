package com.computer_rescuer.notification.infrastructure.lineworks.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LineworksTokenManager} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * LINE WORKS API 呼び出しに必要なアクセストークンをオンメモリでキャッシュ管理します。<br>
 * <ul>
 *   <li>初回呼び出し時における {@link LineworksAuthApi} への新規トークン取得要求とメモリキャッシュ保持</li>
 *   <li>キャッシュ存在時における外部 API 呼び出しの抑止（無駄な JWT 生成やレートリミット超過の防止）</li>
 *   <li>401 エラー検知時等におけるキャッシュの破棄および新トークンの強制再取得（{@code refreshToken}）</li>
 *   <li>{@code synchronized} 修飾によるスレッド安全性の担保（マルチスレッド環境での二重取得防止）</li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * {@link LineworksAuthApi} をモック化し、初回取得・キャッシュ返却・強制リフレッシュ・例外発生時のキャッシュ状態遷移、
 * および複数スレッドからの同時アクセス時における排他制御（スレッドセーフティ）を検証します。
 * 全テストメソッドに厳格な JavaDoc を記述します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineworksTokenManager 単体テスト")
class LineworksTokenManagerTest {

  private static final String REQUIRED_SCOPES = "bot,directory.read";

  @Mock
  private LineworksAuthApi authApi;

  @InjectMocks
  private LineworksTokenManager sut;

  @Nested
  @DisplayName("トークン取得（getToken）とキャッシュライフサイクルのテスト")
  class TokenCacheLifecycleTest {

    /**
     * 【テスト観点 / 責務】<br> トークンがまだキャッシュされていない初回呼び出し時、 必要な権限スコープ（"bot,directory.read"）を指定して認証 API を呼び出し、
     * 取得したトークンを返却するとともに内部メモリにキャッシュすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - キャッシュは未保持（null）。<br> - 認証 API がトークン "initial-token-123"
     * を返却するように設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却されたトークンが "initial-token-123" であること。<br> -
     * {@code authApi.fetchAccessToken("bot,directory.read")} が正確に 1 回呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("初回呼び出し時、認証APIから新規トークンを取得してキャッシュすること")
    public void getToken_WhenCacheIsNull_ShouldFetchNewTokenAndCacheIt() {
      // Arrange
      when(authApi.fetchAccessToken(REQUIRED_SCOPES)).thenReturn("initial-token-123");

      // Act
      String token = sut.getToken();

      // Assert
      assertThat(token).isEqualTo("initial-token-123");
      verify(authApi, times(1)).fetchAccessToken(REQUIRED_SCOPES);
    }

    /**
     * 【テスト観点 / 責務】<br> すでにトークンがキャッシュされている場合、認証 API を再度呼び出すことなく、 保持しているキャッシュトークンを即座に返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 初回 {@code getToken()} を実行して "cached-token-abc" がキャッシュされた状態。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 2回目の {@code getToken()} の返却値が 1 回目と同一の "cached-token-abc"
     * であること。<br> - 認証 API（{@code fetchAccessToken}）の呼び出し回数が通算 1 回のままであること。
     * </p>
     */
    @Test
    @DisplayName("キャッシュが存在する場合、APIを再呼び出しせずキャッシュされたトークンを返却すること")
    public void getToken_WhenCacheIsPresent_ShouldReturnCachedTokenWithoutCallingApi() {
      // Arrange
      when(authApi.fetchAccessToken(REQUIRED_SCOPES)).thenReturn("cached-token-abc");

      // 1回目の呼び出し（キャッシュ生成）
      String firstCallToken = sut.getToken();

      // Act: 2回目の呼び出し（キャッシュ利用）
      String secondCallToken = sut.getToken();

      // Assert
      assertThat(firstCallToken).isEqualTo("cached-token-abc");
      assertThat(secondCallToken).isEqualTo("cached-token-abc");
      assertThat(secondCallToken).isSameAs(firstCallToken);

      // 通算で 1 回しか外部 API が呼ばれていないことを検証
      verify(authApi, times(1)).fetchAccessToken(anyString());
    }

    /**
     * 【テスト観点 / 責務】<br> {@code refreshToken()} が呼び出された場合、既存のキャッシュが即座に破棄され、 認証 API
     * を再呼び出しして新しいトークンを取得・キャッシュし直すことを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 初回トークン "old-token-001" がキャッシュ済み。<br> - 認証 API が 2 回目の呼び出しに対して
     * "new-token-002" を返却するように設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@code refreshToken()} の戻り値が "new-token-002" であること。<br> - 外部
     * API（{@code fetchAccessToken}）が通算 2 回呼び出されること。<br> - リフレッシュ後に再度 {@code getToken()}
     * を呼んだ場合、新トークンが返却されること。
     * </p>
     */
    @Test
    @DisplayName("refreshToken呼び出し時、キャッシュを破棄して新しいトークンを再取得・キャッシュすること")
    public void refreshToken_ShouldEvictCacheAndFetchNewToken() {
      // Arrange
      when(authApi.fetchAccessToken(REQUIRED_SCOPES))
          .thenReturn("old-token-001")
          .thenReturn("new-token-002");

      // 初回取得してキャッシュ
      String oldToken = sut.getToken();
      assertThat(oldToken).isEqualTo("old-token-001");

      // Act: 強制リフレッシュ実行
      String refreshedToken = sut.refreshToken();

      // Assert
      assertThat(refreshedToken).isEqualTo("new-token-002");
      verify(authApi, times(2)).fetchAccessToken(REQUIRED_SCOPES);

      // リフレッシュ後の getToken() で新トークンがキャッシュから返ることの検証
      String subsequentToken = sut.getToken();
      assertThat(subsequentToken).isEqualTo("new-token-002");
      verify(authApi, times(2)).fetchAccessToken(anyString()); // API 呼び出し回数は 2 回のまま
    }
  }

  @Nested
  @DisplayName("異常系（認証APIエラー時）のテスト")
  class ExceptionHandlingTest {

    /**
     * 【テスト観点 / 責務】<br> 外部認証 API の呼び出し時に例外（通信エラー等）が発生した場合、 例外が上位へそのまま伝播し、不正な値がキャッシュに残らないことを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code authApi.fetchAccessToken} が 1 回目に
     * {@link RuntimeException} をスロー。<br> - 2 回目には正常にトークン "recovered-token" を返却するように設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 1 回目の呼び出しで {@link RuntimeException} がスローされること。<br> -
     * 不正なトークンがキャッシュに残らず、2 回目の呼び出し時に正常に API が再試行されて復旧すること。
     * </p>
     */
    @Test
    @DisplayName("API通信で例外が発生した場合、例外をスローしキャッシュが未設定のまま維持されること")
    public void getToken_WhenAuthApiThrowsException_ShouldPropagateExceptionAndNotCache() {
      // Arrange
      when(authApi.fetchAccessToken(REQUIRED_SCOPES))
          .thenThrow(new RuntimeException("LINE WORKS 認証サーバー接続エラー"))
          .thenReturn("recovered-token");

      // Act & Assert: 1回目は例外がスローされること
      assertThatThrownBy(() -> sut.getToken())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS 認証サーバー接続エラー");

      // Act: 2回目の呼び出し（再試行）
      String recoveredToken = sut.getToken();

      // Assert: 不正な値がキャッシュされず、再度取得が行われて復旧すること
      assertThat(recoveredToken).isEqualTo("recovered-token");
      verify(authApi, times(2)).fetchAccessToken(REQUIRED_SCOPES);
    }
  }

  @Nested
  @DisplayName("並行性・スレッドセーフティ（排他制御）のテスト")
  class ConcurrencyTest {

    /**
     * 【テスト観点 / 責務】<br> 複数スレッドから同時に {@code getToken()} が呼び出された場合でも、 {@code synchronized}
     * による排他制御が働き、外部認証 API が二重に呼び出されることなく 正確に 1 回だけ実行され、全スレッドが同一のトークンを受け取れることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 10 本のスレッドが同時に {@code getToken()} を実行。<br> -
     * {@link CountDownLatch} を用いて全スレッドの実行タイミングを完全同期。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 全スレッドが取得したトークンがすべて "thread-safe-token" と一致すること。<br> - 認証
     * API（{@code fetchAccessToken}）の呼び出しが正確に 1 回のみであること。
     * </p>
     *
     * @throws InterruptedException スレッド待機中に割り込みが発生した場合
     */
    @Test
    @DisplayName("複数スレッドから同時にgetTokenが呼ばれても、APIは1回のみ呼ばれ同一トークンが返ること")
    public void getToken_ConcurrentAccess_ShouldOnlyCallApiOnce() throws InterruptedException {
      // Arrange
      int threadCount = 10;
      when(authApi.fetchAccessToken(REQUIRED_SCOPES)).thenReturn("thread-safe-token");

      ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
      CountDownLatch readyLatch = new CountDownLatch(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);

      List<String> results = Collections.synchronizedList(new ArrayList<>());

      // Act: 10 本のスレッドで一斉に getToken を実行
      for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
          readyLatch.countDown();
          try {
            startLatch.await(); // 全スレッドが揃うまで待機
            results.add(sut.getToken());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            doneLatch.countDown();
          }
        });
      }

      readyLatch.await(); // 全スレッドの起動待ち
      startLatch.countDown(); // 一斉スタート！
      boolean finished = doneLatch.await(5, TimeUnit.SECONDS);

      executorService.shutdown();

      // Assert
      assertThat(finished).isTrue();
      assertThat(results).hasSize(threadCount);

      // すべてのスレッドが同一のトークンを取得できたこと
      assertThat(results).allMatch(token -> token.equals("thread-safe-token"));

      // 同時アクセスがあっても外部 API は 1 回しか呼ばれていないこと（二重取得防止）
      verify(authApi, times(1)).fetchAccessToken(REQUIRED_SCOPES);
    }
  }
}
