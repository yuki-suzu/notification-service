package com.computer_rescuer.notification.infrastructure.lineworks.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LineworksAccountResolver} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 社内運用ルール（LINE WORKS のアカウント ID が社用メールアドレスの末尾 {@code .com} を除いた文字列となっている仕様）に基づき、 標準的な社用メールアドレスから
 * LINE WORKS API 用の宛先識別子への変換を担当します。<br>
 * <ul>
 *   <li>入力値が null、空文字、空白文字である場合の安全な空文字列返却（ガード節）</li>
 *   <li>末尾の {@code .com} の大文字小文字を問わない安全な除去</li>
 *   <li>{@code .com} 以外の拡張子（{@code .jp}, {@code .co.jp} 等）に対する非置換保証</li>
 *   <li>アドレス途中（ローカル部やサブドメイン部）に含まれる {@code .com} に対する誤置換防止</li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * 境界値および各種ドメインパターンの入力に対して決定論的に出力文字列をアサートし、
 * 正規表現（{@code (?i)\\.com$}）の安全性を 100% 網羅検証します。全テストメソッドに厳格な JavaDoc を記述します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineworksAccountResolver 単体テスト")
class LineworksAccountResolverTest {

  @InjectMocks
  private LineworksAccountResolver sut;

  @Nested
  @DisplayName("境界値（null・空文字・空白文字）のテスト")
  class BoundaryTest {

    /**
     * 【テスト観点 / 責務】<br> メールアドレスとして null が渡された場合、NullPointerException を起こさず安全に空文字列を返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: null
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却値が空文字列（""）であること。
     * </p>
     */
    @Test
    @DisplayName("メールアドレスがnullの場合、空文字列を返却すること")
    public void resolveUserIdFromEmail_WhenEmailIsNull_ShouldReturnEmptyString() {
      // Act
      String actual = sut.resolveUserIdFromEmail(null);

      // Assert
      assertThat(actual).isEmpty();
    }

    /**
     * 【テスト観点 / 責務】<br> メールアドレスとして空文字（""）が渡された場合、安全に空文字列を返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: ""
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却値が空文字列（""）であること。
     * </p>
     */
    @Test
    @DisplayName("メールアドレスが空文字の場合、空文字列を返却すること")
    public void resolveUserIdFromEmail_WhenEmailIsEmpty_ShouldReturnEmptyString() {
      // Act
      String actual = sut.resolveUserIdFromEmail("");

      // Assert
      assertThat(actual).isEmpty();
    }

    /**
     * 【テスト観点 / 責務】<br> メールアドレスとして半角スペース・タブ等の空白文字のみが渡された場合、安全に空文字列を返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: "   \t\n"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却値が空文字列（""）であること。
     * </p>
     */
    @Test
    @DisplayName("メールアドレスが空白文字のみの場合、空文字列を返却すること")
    public void resolveUserIdFromEmail_WhenEmailIsWhitespace_ShouldReturnEmptyString() {
      // Act
      String actual = sut.resolveUserIdFromEmail("   \t\n");

      // Assert
      assertThat(actual).isEmpty();
    }
  }

  @Nested
  @DisplayName("末尾 .com 除去（大文字小文字不問）のテスト")
  class ExtensionRemovalTest {

    /**
     * 【テスト観点 / 責務】<br> 標準的な小文字の ".com" で終わるメールアドレスから、末尾の ".com" のみが正しく除去されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: "taro.yamada@example.com"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 導出された ID が "taro.yamada@example" であること。
     * </p>
     */
    @Test
    @DisplayName("小文字の.comで終わる場合、末尾の.comを除去した文字列を返却すること")
    public void resolveUserIdFromEmail_WhenEmailEndsWithLowerCaseCom_ShouldRemoveExtension() {
      // Act
      String actual = sut.resolveUserIdFromEmail("taro.yamada@example.com");

      // Assert
      assertThat(actual).isEqualTo("taro.yamada@example");
    }

    /**
     * 【テスト観点 / 責務】<br> すべて大文字の ".COM" で終わるメールアドレスから、大文字小文字を区別せず ".COM" が除去されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: "JIRO.SATO@EXAMPLE.COM"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 導出された ID が "JIRO.SATO@EXAMPLE" であること。
     * </p>
     */
    @Test
    @DisplayName("大文字の.COMで終わる場合、大文字小文字を問わず.COMを除去すること")
    public void resolveUserIdFromEmail_WhenEmailEndsWithUpperCaseCom_ShouldRemoveExtension() {
      // Act
      String actual = sut.resolveUserIdFromEmail("JIRO.SATO@EXAMPLE.COM");

      // Assert
      assertThat(actual).isEqualTo("JIRO.SATO@EXAMPLE");
    }

    /**
     * 【テスト観点 / 責務】<br> 大文字小文字混在の ".Com" で終わるメールアドレスから、安全に拡張子が除去されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: "hanako@test.Com"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 導出された ID が "hanako@test" であること。
     * </p>
     */
    @Test
    @DisplayName("大文字小文字混在の.Comで終わる場合、安全に除去すること")
    public void resolveUserIdFromEmail_WhenEmailEndsWithMixedCaseCom_ShouldRemoveExtension() {
      // Act
      String actual = sut.resolveUserIdFromEmail("hanako@test.Com");

      // Assert
      assertThat(actual).isEqualTo("hanako@test");
    }
  }

  @Nested
  @DisplayName("非 .com ドメインおよび部分一致の誤置換防止テスト")
  class NonComDomainAndPatternSafetyTest {

    /**
     * 【テスト観点 / 責務】<br> ".jp", ".co.jp", ".net" 等の ".com" 以外のトップレベルドメインを持つメールアドレスの場合、
     * 拡張子が除去されず、元のメールアドレスがそのまま返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: "suzuki@example.co.jp"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 元の文字列 "suzuki@example.co.jp" が変更されずに返却されること。
     * </p>
     */
    @Test
    @DisplayName(".co.jpなど.com以外のドメインの場合、文字列を変更せず返却すること")
    public void resolveUserIdFromEmail_WhenEmailEndsWithNonComDomain_ShouldReturnOriginalEmail() {
      // Act & Assert
      assertThat(sut.resolveUserIdFromEmail("suzuki@example.co.jp")).isEqualTo(
          "suzuki@example.co.jp");
      assertThat(sut.resolveUserIdFromEmail("tanaka@example.jp")).isEqualTo("tanaka@example.jp");
      assertThat(sut.resolveUserIdFromEmail("admin@example.net")).isEqualTo("admin@example.net");
    }

    /**
     * 【テスト観点 / 責務】<br> ローカル部（ユーザー名部分）や会社名部分に "com" の文字列が含まれる場合、 途中の文字列は誤って削除されず、末尾の ".com"
     * のみが厳密に除去されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: "com.taro@company-community.com"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 途中の "com", "company", "community" は保持され、末尾の ".com" のみが除去された
     * "com.taro@company-community" が返却されること。
     * </p>
     */
    @Test
    @DisplayName("メールアドレスの途中にcomが含まれていても、末尾の.comのみが除去されること")
    public void resolveUserIdFromEmail_WhenEmailContainsComInMiddle_ShouldOnlyRemoveAtEnd() {
      // Act
      String actual = sut.resolveUserIdFromEmail("com.taro@company-community.com");

      // Assert
      assertThat(actual).isEqualTo("com.taro@company-community");
    }

    /**
     * 【テスト観点 / 責務】<br> ドメイン部分に ".com.jp" のように途中に ".com" を含む場合、 末尾が ".com"
     * ではないため置換が行われず、そのまま返却されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 入力メールアドレス: "user@example.com.jp"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 途中の ".com" が誤って除去されず、"user@example.com.jp" のまま返却されること。
     * </p>
     */
    @Test
    @DisplayName("末尾が.com.jpのように途中に.comがある場合、誤置換されずにそのまま返却されること")
    public void resolveUserIdFromEmail_WhenEmailEndsWithComDotJp_ShouldNotRemoveExtension() {
      // Act
      String actual = sut.resolveUserIdFromEmail("user@example.com.jp");

      // Assert
      assertThat(actual).isEqualTo("user@example.com.jp");
    }
  }
}
