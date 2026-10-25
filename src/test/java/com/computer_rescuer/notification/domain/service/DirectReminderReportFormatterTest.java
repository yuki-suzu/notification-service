package com.computer_rescuer.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent.DirectReminderEmployee;
import com.computer_rescuer.notification.domain.service.DirectReminderReportFormatter.FailureItem;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DirectReminderReportFormatter} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 未打刻者本人への個別ダイレクト通知（DM）の送信実行結果（成功者リスト・失敗/スキップ明細リスト）を集約し、
 * 管理者が状況を一目で把握できる視認性の高いトークルーム向けサマリーレポート本文を生成します。<br>
 * <ul>
 *   <li>送信成功人数および失敗・警告人数のサマリーヘッダーの出力</li>
 *   <li>送信成功者が存在する場合の氏名・社員番号一覧セクションの出力</li>
 *   <li>送信失敗者が存在する場合の氏名・社員番号・障害理由明細および登録確認注記の出力</li>
 *   <li>全対象者への送信が正常完了した場合の完了メッセージ出力</li>
 *   <li>代理送信モード（{@code isProxy = true}）時における管理者宛て代理配信注記メッセージの付加</li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * 成功・失敗の件数組み合わせ（成功のみ、失敗のみ、混在、両方0件）および {@code isProxy} フラグの
 * True / False パターンを網羅し、すべての条件分岐（C1カバレッジ 100%）を検証します。
 * 全テストメソッドに厳格な JavaDoc を記述します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DirectReminderReportFormatter 単体テスト")
class DirectReminderReportFormatterTest {

  @InjectMocks
  private DirectReminderReportFormatter sut;

  @Nested
  @DisplayName("通常モード（isProxy = false）のレポート生成テスト")
  class NormalModeReportTest {

    /**
     * 【テスト観点 / 責務】<br> 配信成功者のみが存在し失敗者が 0 件の場合、 成功者一覧セクションおよび「全対象者へのダイレクト通知が正常に完了しました」のメッセージが出力され、
     * 失敗セクションおよび代理送信注記が出力されないことを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - successItems: 2名の従業員（山田太郎、佐藤次郎）。<br> - failureItems: 空リスト。<br>
     * - isProxy: false
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「（送信成功: 2名 / 失敗・警告: 0名）」が出力されること。<br> -
     * 山田太郎、佐藤次郎の氏名と社員番号が出力されること。<br> - 「※全対象者へのダイレクト通知が正常に完了しました。」が含まれること。<br> - 失敗セクション（🚨
     * 送信失敗・スキップ）および代理注記が含まれないこと。
     * </p>
     */
    @Test
    @DisplayName("全件成功の場合、成功者一覧と正常完了メッセージが出力され、失敗セクションが含まれないこと")
    public void format_WhenAllSuccess_ShouldContainSuccessListAndCompletionMessage() {
      // Arrange
      DirectReminderEmployee emp1 = new DirectReminderEmployee("EMP001", "taro@example.com",
          "山田 太郎");
      DirectReminderEmployee emp2 = new DirectReminderEmployee("EMP002", "jiro@example.com",
          "佐藤 次郎");
      List<DirectReminderEmployee> successItems = List.of(emp1, emp2);
      List<FailureItem> failureItems = Collections.emptyList();

      // Act
      String actual = sut.format(successItems, failureItems, false);

      // Assert
      assertThat(actual)
          .contains("📢 【未打刻DM 配信結果レポート】")
          .contains("（送信成功: 2名 / 失敗・警告: 0名）")
          .contains("✅ 送信成功:")
          .contains("  ・山田 太郎（社員番号: EMP001）")
          .contains("  ・佐藤 次郎（社員番号: EMP002）")
          .contains("※全対象者へのダイレクト通知が正常に完了しました。")
          .doesNotContain("🚨 送信失敗・スキップ:")
          .doesNotContain("※DM機能は無効となっております。");
    }

    /**
     * 【テスト観点 / 責務】<br> 配信失敗者のみが存在し成功者が 0 件の場合、
     * 成功者一覧セクションが出力されず、失敗理由を含んだ失敗明細セクションおよび確認要請注記が出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - successItems: 空リスト。<br> - failureItems: 2名の障害明細（鈴木一郎:
     * アカウント解決不可、高橋花子: 通信エラー）。<br> - isProxy: false
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「（送信成功: 0名 / 失敗・警告: 2名）」が出力されること。<br> - 成功セクション（✅
     * 送信成功:）が含まれないこと。<br> - 鈴木一郎、高橋花子の氏名、社員番号、それぞれの失敗理由が出力されること。<br> - 「※失敗対象がある場合は、LINE WORKS
     * の登録状況やログをご確認ください。」が含まれること。
     * </p>
     */
    @Test
    @DisplayName("全件失敗の場合、成功セクションが出力されず、失敗明細と理由が出力されること")
    public void format_WhenAllFailed_ShouldContainFailureListAndReasonsWithoutSuccessSection() {
      // Arrange
      List<DirectReminderEmployee> successItems = Collections.emptyList();

      DirectReminderEmployee emp1 = new DirectReminderEmployee("EMP003", "ichiro@example.com",
          "鈴木 一郎");
      DirectReminderEmployee emp2 = new DirectReminderEmployee("EMP004", "hanako@example.com",
          "高橋 花子");
      List<FailureItem> failureItems = List.of(
          new FailureItem(emp1, "アカウント解決不可のため管理者へ代理送信"),
          new FailureItem(emp2, "送信エラー: Connection timeout")
      );

      // Act
      String actual = sut.format(successItems, failureItems, false);

      // Assert
      assertThat(actual)
          .contains("📢 【未打刻DM 配信結果レポート】")
          .contains("（送信成功: 0名 / 失敗・警告: 2名）")
          .doesNotContain("✅ 送信成功:")
          .contains("🚨 送信失敗・スキップ:")
          .contains("  ・鈴木 一郎（社員番号: EMP003）: アカウント解決不可のため管理者へ代理送信")
          .contains("  ・高橋 花子（社員番号: EMP004）: 送信エラー: Connection timeout")
          .contains("※失敗対象がある場合は、LINE WORKS の登録状況やログをご確認ください。")
          .doesNotContain("※全対象者へのダイレクト通知が正常に完了しました。");
    }

    /**
     * 【テスト観点 / 責務】<br> 成功者と失敗者が混在する場合、 成功者一覧セクションと失敗者一覧セクションの双方が完全に出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - successItems: 1名（山田太郎）。<br> - failureItems: 1名（鈴木一郎）。<br> -
     * isProxy: false
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「（送信成功: 1名 / 失敗・警告: 1名）」が出力されること。<br> -
     * 成功セクションと失敗セクションの両方が含まれること。
     * </p>
     */
    @Test
    @DisplayName("成功と失敗が混在する場合、双方のセクションが完全に出力されること")
    public void format_WhenMixed_ShouldContainBothSuccessAndFailureSections() {
      // Arrange
      DirectReminderEmployee successEmp = new DirectReminderEmployee("EMP001", "taro@example.com",
          "山田 太郎");
      DirectReminderEmployee failEmp = new DirectReminderEmployee("EMP002", "ichiro@example.com",
          "鈴木 一郎");

      List<DirectReminderEmployee> successItems = List.of(successEmp);
      List<FailureItem> failureItems = List.of(new FailureItem(failEmp, "アカウント不在"));

      // Act
      String actual = sut.format(successItems, failureItems, false);

      // Assert
      assertThat(actual)
          .contains("（送信成功: 1名 / 失敗・警告: 1名）")
          .contains("✅ 送信成功:")
          .contains("  ・山田 太郎（社員番号: EMP001）")
          .contains("🚨 送信失敗・スキップ:")
          .contains("  ・鈴木 一郎（社員番号: EMP002）: アカウント不在");
    }

    /**
     * 【テスト観点 / 責務】<br> 成功者・失敗者ともに 0 件（境界値）の場合、 件数が 0 名と表示され、双方向の明細行なしで正常完了メッセージが出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - successItems: 空リスト。<br> - failureItems: 空リスト。<br> - isProxy:
     * false
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「（送信成功: 0名 / 失敗・警告: 0名）」が出力されること。<br> -
     * 「※全対象者へのダイレクト通知が正常に完了しました。」が含まれること。
     * </p>
     */
    @Test
    @DisplayName("成功・失敗ともに0件の場合、明細行なしで正常完了メッセージが出力されること")
    public void format_WhenBothEmpty_ShouldOutputZeroCountAndCompletionMessage() {
      // Act
      String actual = sut.format(Collections.emptyList(), Collections.emptyList(), false);

      // Assert
      assertThat(actual)
          .contains("（送信成功: 0名 / 失敗・警告: 0名）")
          .doesNotContain("✅ 送信成功:")
          .doesNotContain("🚨 送信失敗・スキップ:")
          .contains("※全対象者へのダイレクト通知が正常に完了しました。");
    }
  }

  @Nested
  @DisplayName("代理送信モード（isProxy = true）の注記付加テスト")
  class ProxyModeReportTest {

    /**
     * 【テスト観点 / 責務】<br> 代理送信モード（{@code isProxy = true}）の場合、
     * レポートの末尾に「※DM機能は無効となっております。送信成功はすべて管理者へ送信しております。」 のシステム注記が必ず付加されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - successItems: 1名。<br> - failureItems: 空リスト。<br> - isProxy:
     * true
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - レポート末尾に代理送信に関する注意書きが含まれていること。
     * </p>
     */
    @Test
    @DisplayName("isProxyがtrueの場合、末尾に管理者代理送信注記メッセージが付加されること")
    public void format_WhenIsProxyIsTrue_ShouldAppendProxyNotice() {
      // Arrange
      DirectReminderEmployee emp = new DirectReminderEmployee("EMP001", "proxy@example.com",
          "代理 太郎");
      List<DirectReminderEmployee> successItems = List.of(emp);

      // Act
      String actual = sut.format(successItems, Collections.emptyList(), true);

      // Assert
      assertThat(actual)
          .contains("※全対象者へのダイレクト通知が正常に完了しました。")
          .contains("\n※DM機能は無効となっております。送信成功はすべて管理者へ送信しております。");
    }

    /**
     * 【テスト観点 / 責務】<br> 代理送信モードかつ失敗が存在する場合でも、 失敗に対する確認要請注記の後に代理送信注記が付加されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - successItems: 1名。<br> - failureItems: 1名。<br> - isProxy: true
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 失敗セクションの注意書きと、代理送信注記の両方が共存して出力されること。
     * </p>
     */
    @Test
    @DisplayName("isProxyがtrueで失敗もある場合、失敗注記と代理送信注記の双方が付加されること")
    public void format_WhenIsProxyIsTrueWithFailures_ShouldAppendBothNotices() {
      // Arrange
      DirectReminderEmployee emp1 = new DirectReminderEmployee("EMP001", "proxy@example.com",
          "代理 太郎");
      DirectReminderEmployee emp2 = new DirectReminderEmployee("EMP002", "proxy@example.com",
          "代理 次郎");

      List<DirectReminderEmployee> successItems = List.of(emp1);
      List<FailureItem> failureItems = List.of(new FailureItem(emp2, "送信エラー"));

      // Act
      String actual = sut.format(successItems, failureItems, true);

      // Assert
      assertThat(actual)
          .contains("※失敗対象がある場合は、LINE WORKS の登録状況やログをご確認ください。")
          .contains("\n※DM機能は無効となっております。送信成功はすべて管理者へ送信しております。");
    }
  }
}
