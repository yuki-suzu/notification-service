package com.computer_rescuer.notification.infrastructure.lineworks.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksUserApi.LineworksUserResponse;
import com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksUserApi.UserName;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * {@link LineworksUserApi} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * LINE WORKS API 2.0 の Directory API を呼び出し、ユーザーのプロファイル取得および実在確認を行います。<br>
 * <ul>
 *   <li>{@link LineworksCoreHttpClient} を経由したエンドポイント（{@code /users/{userId}}）への GET 要求</li>
 *   <li>ユーザー不在時（404 Not Found）における安全な空の {@link Optional} 返却</li>
 *   <li>予期せぬ通信例外発生時におけるログ出力および空の {@link Optional} 返却（フェイルセーフ）</li>
 *   <li><b>【宛名解決仕様】{@link LineworksUserResponse#resolveDisplayName()} によるニックネーム優先・姓名フォールバックの全分岐評価</b></li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * {@link LineworksCoreHttpClient} をモック化して通信ステータスごとの戻り値を検証するとともに、
 * 内部レコード {@link LineworksUserResponse} のビジネスロジックを境界値（null、空文字、空白文字）を含めて完全網羅します。
 * 全テストメソッドに厳格な JavaDoc を記述します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineworksUserApi 単体テスト")
class LineworksUserApiTest {

  @Mock
  private LineworksCoreHttpClient coreClient;

  @InjectMocks
  private LineworksUserApi sut;

  /**
   * テスト用の 404 Not Found 例外オブジェクトを生成するヘルパーメソッド。
   *
   * @return {@link HttpClientErrorException.NotFound} インスタンス
   */
  private HttpClientErrorException.NotFound createNotFoundException() {
    return (HttpClientErrorException.NotFound) HttpClientErrorException.create(
        HttpStatus.NOT_FOUND,
        "User Not Found",
        HttpHeaders.EMPTY,
        new byte[0],
        StandardCharsets.UTF_8
    );
  }

  @Nested
  @DisplayName("fetchUser（ユーザー取得・存在確認）のテスト")
  class FetchUserTest {

    /**
     * 【テスト観点 / 責務】<br> 有効なユーザー ID を指定した場合、適切な URL パスでクライアントが呼び出され、 取得されたユーザー情報が {@link Optional}
     * にラップされて返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - userId "user-123" を指定。<br> - coreClient が正常な
     * {@link LineworksUserResponse} を返却するように設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 戻り値の {@link Optional} が存在（isPresent）すること。<br> - 含まれるユーザー情報の
     * userId が "user-123" と一致すること。<br> - {@code coreClient.get("/users/user-123", ...)}
     * が正確に呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("ユーザーが実在する場合、Optionalにラップされたユーザー情報を返却すること")
    public void fetchUser_WhenUserExists_ShouldReturnOptionalWithUser() {
      // Arrange
      String userId = "user-123";
      LineworksUserResponse expectedResponse = new LineworksUserResponse(
          userId,
          new UserName("山田", "太郎"),
          "タロー"
      );

      when(coreClient.get(eq("/users/user-123"), eq(LineworksUserResponse.class), anyString()))
          .thenReturn(expectedResponse);

      // Act
      Optional<LineworksUserResponse> actual = sut.fetchUser(userId);

      // Assert
      assertThat(actual).isPresent().contains(expectedResponse);
      verify(coreClient).get("/users/user-123", LineworksUserResponse.class, "ユーザー情報取得");
    }

    /**
     * 【テスト観点 / 責務】<br> クライアントのレスポンスが null であった場合、 NullPointerException を起こさず安全に空の {@link Optional}
     * を返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - coreClient が null を返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 戻り値が {@link Optional#empty()} であること。
     * </p>
     */
    @Test
    @DisplayName("クライアントの戻り値がnullの場合、空のOptionalを返却すること")
    public void fetchUser_WhenCoreClientReturnsNull_ShouldReturnEmptyOptional() {
      // Arrange
      when(coreClient.get(anyString(), eq(LineworksUserResponse.class), anyString()))
          .thenReturn(null);

      // Act
      Optional<LineworksUserResponse> actual = sut.fetchUser("user-null");

      // Assert
      assertThat(actual).isEmpty();
    }

    /**
     * 【テスト観点 / 責務】<br> LINE WORKS 上にユーザーが存在せず 404 Not Found 例外が発生した場合、 例外がキャッチされ、ユーザー不在を表す空の
     * {@link Optional} が安全に返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - coreClient が {@link HttpClientErrorException.NotFound} をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外が外部に漏洩せず、{@link Optional#empty()} が返却されること。
     * </p>
     */
    @Test
    @DisplayName("ユーザーが存在しない（404 Not Found）場合、例外を捕捉して空のOptionalを返却すること")
    public void fetchUser_WhenUserNotFound_ShouldReturnEmptyOptional() {
      // Arrange
      when(coreClient.get(eq("/users/not-found-id"), eq(LineworksUserResponse.class), anyString()))
          .thenThrow(createNotFoundException());

      // Act
      Optional<LineworksUserResponse> actual = sut.fetchUser("not-found-id");

      // Assert
      assertThat(actual).isEmpty();
    }

    /**
     * 【テスト観点 / 責務】<br> サーバー障害（500 Internal Server Error）やタイムアウト等の予期せぬ例外が発生した場合、
     * アプリケーションを巻き込んでクラッシュさせず、ログを出力して空の {@link Optional} を返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - coreClient が {@link HttpServerErrorException}（500）をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外が外部に漏洩せず、{@link Optional#empty()} が返却されること。
     * </p>
     */
    @Test
    @DisplayName("500サーバー障害や通信エラーが発生した場合、例外を捕捉して安全に空のOptionalを返却すること")
    public void fetchUser_WhenOtherExceptionOccurs_ShouldCatchAndReturnEmptyOptional() {
      // Arrange
      HttpServerErrorException serverError = new HttpServerErrorException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"
      );
      when(coreClient.get(anyString(), eq(LineworksUserResponse.class), anyString()))
          .thenThrow(serverError);

      // Act
      Optional<LineworksUserResponse> actual = sut.fetchUser("error-id");

      // Assert
      assertThat(actual).isEmpty();
    }
  }

  @Nested
  @DisplayName("LineworksUserResponse#resolveDisplayName（表示名解決ロジック）のテスト")
  class ResolveDisplayNameTest {

    /**
     * 【テスト観点 / 責務】<br> ニックネームが設定されている場合、姓名（UserName）の値に関わらずニックネームが最優先で表示名として採用されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - nickname: "タローくん"<br> - userName: 姓 "山田"、名 "太郎"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却される表示名が "タローくん" であること。
     * </p>
     */
    @Test
    @DisplayName("ニックネームが存在する場合、ニックネームを最優先で返却すること")
    public void resolveDisplayName_WhenNicknameIsPresent_ShouldReturnNickname() {
      // Arrange
      LineworksUserResponse response = new LineworksUserResponse(
          "user-1",
          new UserName("山田", "太郎"),
          "タローくん"
      );

      // Act
      String displayName = response.resolveDisplayName();

      // Assert
      assertThat(displayName).isEqualTo("タローくん");
    }

    /**
     * 【テスト観点 / 責務】<br> ニックネームが未設定（null または空白文字）であり、姓名の両方が存在する場合、 「姓
     * 名」の形式でスペース結合された文字列が返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - nickname: 空白文字 "   "<br> - userName: 姓 "佐藤"、名 "次郎"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却される表示名が "佐藤 次郎" であること。
     * </p>
     */
    @Test
    @DisplayName("ニックネームが空白で姓名が存在する場合、半角スペース結合したフルネームを返却すること")
    public void resolveDisplayName_WhenNicknameIsBlankAndFullNameIsPresent_ShouldReturnCombinedFullName() {
      // Arrange
      LineworksUserResponse response = new LineworksUserResponse(
          "user-2",
          new UserName("佐藤", "次郎"),
          "   "
      );

      // Act
      String displayName = response.resolveDisplayName();

      // Assert
      assertThat(displayName).isEqualTo("佐藤 次郎");
    }

    /**
     * 【テスト観点 / 責務】<br> ニックネームがなく、名（firstName）が null で姓（lastName）のみ存在する場合、
     * 余分なスペースを含まずに姓のみが返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - nickname: null<br> - userName: 姓 "鈴木"、名 null
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却される表示名が "鈴木" であること。
     * </p>
     */
    @Test
    @DisplayName("姓のみが設定されている場合、余分なスペースなしで姓のみを返却すること")
    public void resolveDisplayName_WhenOnlyLastNameIsPresent_ShouldReturnLastName() {
      // Arrange
      LineworksUserResponse response = new LineworksUserResponse(
          "user-3",
          new UserName("鈴木", null),
          null
      );

      // Act
      String displayName = response.resolveDisplayName();

      // Assert
      assertThat(displayName).isEqualTo("鈴木");
    }

    /**
     * 【テスト観点 / 責務】<br> ニックネームがなく、姓（lastName）が null で名（firstName）のみ存在する場合、
     * 余分なスペースを含まずに名のみが返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - nickname: null<br> - userName: 姓 null、名 "一郎"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却される表示名が "一郎" であること。
     * </p>
     */
    @Test
    @DisplayName("名のみが設定されている場合、余分なスペースなしで名のみを返却すること")
    public void resolveDisplayName_WhenOnlyFirstNameIsPresent_ShouldReturnFirstName() {
      // Arrange
      LineworksUserResponse response = new LineworksUserResponse(
          "user-4",
          new UserName(null, "一郎"),
          null
      );

      // Act
      String displayName = response.resolveDisplayName();

      // Assert
      assertThat(displayName).isEqualTo("一郎");
    }

    /**
     * 【テスト観点 / 責務】<br> ニックネームがなく、UserName オブジェクト内の姓・名の双方が null の場合、 安全に空文字（""）が返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - nickname: null<br> - userName: 姓 null、名 null
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却される表示名が空文字 "" であること。
     * </p>
     */
    @Test
    @DisplayName("姓・名ともにnullの場合、空文字を返却すること")
    public void resolveDisplayName_WhenBothNamesAreNull_ShouldReturnEmptyString() {
      // Arrange
      LineworksUserResponse response = new LineworksUserResponse(
          "user-5",
          new UserName(null, null),
          null
      );

      // Act
      String displayName = response.resolveDisplayName();

      // Assert
      assertThat(displayName).isEmpty();
    }

    /**
     * 【テスト観点 / 責務】<br> ニックネームおよび UserName オブジェクトの双方が null である極限状態において、 NullPointerException
     * を起こさず安全に空文字（""）が返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - nickname: null<br> - userName: null
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却される表示名が空文字 "" であること。
     * </p>
     */
    @Test
    @DisplayName("nicknameとuserNameの両方がnullの場合、安全に空文字を返却すること")
    public void resolveDisplayName_WhenBothNicknameAndUserNameAreNull_ShouldReturnEmptyString() {
      // Arrange
      LineworksUserResponse response = new LineworksUserResponse(
          "user-6",
          null,
          null
      );

      // Act
      String displayName = response.resolveDisplayName();

      // Assert
      assertThat(displayName).isEmpty();
    }
  }
}
