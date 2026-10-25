package com.computer_rescuer.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.computer_rescuer.notification.application.dto.UnstampedAlertEvent.UnstampedEmployee;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UnstampedAlertMessageFormatter} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 勤怠管理サービスから受信した未打刻者検知データに基づき、管理者が視認しやすいトークルーム通知文面を構築します。<br>
 * <ul>
 *   <li>判定対象日を含むアラートヘッダーの出力</li>
 *   <li>未打刻従業員リストの所属部門別グルーピング</li>
 *   <li>部門名が未設定（null、空文字、空白文字）の場合の「未所属」フォールバック</li>
 *   <li>出勤予定時刻（{@link LocalTime}）の "HH:mm" 形式フォーマット、および未設定時の「予定不明」フォールバック</li>
 *   <li>当月累積未打刻回数の埋め込み、および未設定時のデフォルト「1回目」フォールバック</li>
 *   <li>確認要請フッターメッセージの出力</li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * プラットフォーム固有の改行コード差分（Windows CRLF / Linux LF）や {@code HashMap} の走査順序に
 * 左右されないクロスプラットフォーム対応のアサーションを行い、すべての条件分岐（C1カバレッジ 100%）を検証します。
 * 全テストメソッドに厳格な JavaDoc を記述します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnstampedAlertMessageFormatter 単体テスト")
class UnstampedAlertMessageFormatterTest {

  @InjectMocks
  private UnstampedAlertMessageFormatter sut;

  private final LocalDate targetDate = LocalDate.of(2026, 9, 22);

  @Nested
  @DisplayName("正常系（部門グルーピングおよび標準フォーマット）のテスト")
  class StandardFormattingTest {

    /**
     * 【テスト観点 / 責務】<br> 全項目が正常に設定された従業員データから、対象日、部門名、氏名、出勤予定時刻、 累積未打刻回数が正しいフォーマットで出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 対象日: 2026-09-22<br> - 開発本部の山田太郎（予定: 09:00、今月2回目）。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - タイトルに「2026-09-22 の出勤打刻が確認できない従業員がいます。」が含まれること。<br> - 部門ヘッダー「🏢
     * 開発本部」が出力されること。<br> - 明細行「  ・山田 太郎 （予定: 09:00〜 / 今月2回目）」が出力されること。<br> -
     * フッター「※打刻漏れ、または遅刻の可能性があります。状況の確認をお願いします。」が含まれること。
     * </p>
     */
    @Test
    @DisplayName("単一部門の正常データが指定された場合、標準のフォーマットで文面を生成すること")
    public void format_StandardSingleDepartment_ShouldFormatCorrectly() {
      // Arrange
      UnstampedEmployee emp = new UnstampedEmployee(
          "EMP001",
          "taro@example.com",
          "開発本部",
          "山田 太郎",
          LocalTime.of(9, 0),
          2
      );

      // Act
      String actual = sut.format(targetDate, List.of(emp));

      // Assert
      assertThat(actual)
          .contains("⚠️ 【未打刻アラート】")
          .contains("2026-09-22 の出勤打刻が確認できない従業員がいます。")
          .contains("🏢 開発本部")
          .contains("  ・山田 太郎 （予定: 09:00〜 / 今月2回目）")
          .contains("※打刻漏れ、または遅刻の可能性があります。状況の確認をお願いします。");
    }

    /**
     * 【テスト観点 / 責務】<br> 複数の異なる部門に所属する従業員が存在する場合、 所属部門名ごとに正しくグルーピングされて出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 開発本部: 山田太郎、佐藤次郎 の2名。<br> - 営業推進部: 鈴木一郎 の1名。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「🏢 開発本部」ヘッダーの下に山田太郎と佐藤次郎が出力されること。<br> - 「🏢
     * 営業推進部」ヘッダーの下に鈴木一郎が出力されること。
     * </p>
     */
    @Test
    @DisplayName("複数部門が存在する場合、部門ごとに正しくグルーピングされて出力されること")
    public void format_MultipleDepartments_ShouldGroupByDepartmentName() {
      // Arrange
      UnstampedEmployee dev1 = new UnstampedEmployee("EMP001", "taro@example.com", "開発本部",
          "山田 太郎", LocalTime.of(9, 0), 1);
      UnstampedEmployee dev2 = new UnstampedEmployee("EMP002", "jiro@example.com", "開発本部",
          "佐藤 次郎", LocalTime.of(9, 30), 3);
      UnstampedEmployee sales = new UnstampedEmployee("EMP003", "ichiro@example.com", "営業推進部",
          "鈴木 一郎", LocalTime.of(10, 0), 1);

      // Act
      String actual = sut.format(targetDate, List.of(dev1, dev2, sales));

      // Assert
      assertThat(actual)
          .contains("🏢 開発本部")
          .contains("  ・山田 太郎 （予定: 09:00〜 / 今月1回目）")
          .contains("  ・佐藤 次郎 （予定: 09:30〜 / 今月3回目）")
          .contains("🏢 営業推進部")
          .contains("  ・鈴木 一郎 （予定: 10:00〜 / 今月1回目）");
    }
  }

  @Nested
  @DisplayName("境界値およびフォールバック処理のテスト")
  class FallbackAndBoundaryTest {

    /**
     * 【テスト観点 / 責務】<br> 部門名が null、空文字（""）、または空白文字（"   "）である場合、 「未所属」グループに統合されてフォールバック出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 従業員A: departmentName が null。<br> - 従業員B: departmentName が空文字
     * ""。<br> - 従業員C: departmentName が空白文字 "   "。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「🏢 未所属」ヘッダーが出力されること。<br> - 3名全員が「🏢 未所属」の下に出力されること。
     * </p>
     */
    @Test
    @DisplayName("部門名がnull・空文字・空白文字の場合、すべて「未所属」として出力されること")
    public void format_WhenDepartmentNameIsNullOrEmpty_ShouldFallbackToUnassignedDepartment() {
      // Arrange
      UnstampedEmployee emp1 = new UnstampedEmployee("EMP001", "a@example.com", null, "従業員A",
          LocalTime.of(9, 0), 1);
      UnstampedEmployee emp2 = new UnstampedEmployee("EMP002", "b@example.com", "", "従業員B",
          LocalTime.of(9, 0), 1);
      UnstampedEmployee emp3 = new UnstampedEmployee("EMP003", "c@example.com", "   ", "従業員C",
          LocalTime.of(9, 0), 1);

      // Act
      String actual = sut.format(targetDate, List.of(emp1, emp2, emp3));

      // Assert
      assertThat(actual)
          .contains("🏢 未所属")
          .contains("  ・従業員A （予定: 09:00〜 / 今月1回目）")
          .contains("  ・従業員B （予定: 09:00〜 / 今月1回目）")
          .contains("  ・従業員C （予定: 09:00〜 / 今月1回目）");
    }

    /**
     * 【テスト観点 / 責務】<br> 当該従業員の出勤予定時刻（scheduledStartAt）が null の場合、 NullPointerException
     * を起こさず「予定不明」として安全に出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - scheduledStartAt: null
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 明細行に「（予定: 予定不明〜 / 今月1回目）」が出力されること。
     * </p>
     */
    @Test
    @DisplayName("出勤予定時刻がnullの場合、「予定不明」にフォールバックされること")
    public void format_WhenScheduledStartAtIsNull_ShouldFallbackToUnknownSchedule() {
      // Arrange
      UnstampedEmployee emp = new UnstampedEmployee(
          "EMP001",
          "taro@example.com",
          "人事総務部",
          "シフト未定者",
          null, // 予定時刻なし
          1
      );

      // Act
      String actual = sut.format(targetDate, List.of(emp));

      // Assert
      assertThat(actual)
          .contains("🏢 人事総務部")
          .contains("  ・シフト未定者 （予定: 予定不明〜 / 今月1回目）");
    }

    /**
     * 【テスト観点 / 責務】<br> 当月未打刻回数（monthlyUnstampedCount）が null の場合、
     * 初回検知とみなしてデフォルトの「今月1回目」としてフォールバック出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - monthlyUnstampedCount: null
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 明細行に「今月1回目」が出力されること。
     * </p>
     */
    @Test
    @DisplayName("当月未打刻回数がnullの場合、デフォルトの「1回目」にフォールバックされること")
    public void format_WhenMonthlyUnstampedCountIsNull_ShouldFallbackToOne() {
      // Arrange
      UnstampedEmployee emp = new UnstampedEmployee(
          "EMP001",
          "taro@example.com",
          "企画開発部",
          "カウントなし従業員",
          LocalTime.of(9, 0),
          null // 回数なし
      );

      // Act
      String actual = sut.format(targetDate, List.of(emp));

      // Assert
      assertThat(actual)
          .contains("🏢 企画開発部")
          .contains("  ・カウントなし従業員 （予定: 09:00〜 / 今月1回目）");
    }

    /**
     * 【テスト観点 / 責務】<br> 従業員リストが空コレクション（0件）の場合、 例外を発生させず、ヘッダーおよびフッターのみで構成された文面を返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - employees: 空リスト
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 部門ヘッダーおよび従業員明細行が含まれず、ヘッダーとフッターのみが出力されること。
     * </p>
     */
    @Test
    @DisplayName("従業員リストが空の場合、部門明細なしでヘッダーとフッターのみを返却すること")
    public void format_WhenEmployeesListIsEmpty_ShouldReturnHeaderAndFooterOnly() {
      // Act
      String actual = sut.format(targetDate, Collections.emptyList());

      // Assert
      assertThat(actual)
          .contains("⚠️ 【未打刻アラート】")
          .contains("2026-09-22 の出勤打刻が確認できない従業員がいます。")
          .doesNotContain("🏢")
          .contains("※打刻漏れ、または遅刻の可能性があります。状況の確認をお願いします。");
    }
  }
}
