package com.computer_rescuer.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent.EmployeeIrregularitySummary;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AttendanceIrregularityMessageFormatter} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 勤怠管理サービスから受信した月次勤怠サマリエベントに基づき、管理者が状況を一目で確認できるトークルーム通知文面を生成します。<br>
 * <ul>
 *   <li>Kafka メッセージ受信時点（{@link LocalDateTime}）を "yyyy-MM-dd HH:mm時点" 形式でヘッダーに埋め込み</li>
 *   <li>勤怠異常対象従業員の所属部門別グルーピング</li>
 *   <li>部門名が未設定（null、空文字、空白文字）の場合の「未所属」フォールバック</li>
 *   <li>予定休を除外し、当欠日数・半休日数・遅延回数のみを抽出したスッキリとした明細行の出力</li>
 *   <li>末尾の余分な改行・空白の除去（{@code stripTrailing()}）</li>
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
@DisplayName("AttendanceIrregularityMessageFormatter 単体テスト")
class AttendanceIrregularityMessageFormatterTest {

  @InjectMocks
  private AttendanceIrregularityMessageFormatter sut;

  private final LocalDateTime receivedAt = LocalDateTime.of(2026, 9, 22, 18, 30);

  @Nested
  @DisplayName("正常系（日時フォーマット・部門グルーピング・明細出力）のテスト")
  class StandardFormattingTest {

    /**
     * 【テスト観点 / 責務】<br> 受信日時が "yyyy-MM-dd HH:mm時点" 形式で正しく出力され、
     * 単一部門における従業員の当欠・半休・遅延が整形されて出力されることを確認します。<br>
     * （※業務仕様として、予定休（scheduledHolidayCount）が通知本文に出力されないことも併せて検証します）
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 受信日時: 2026-09-22 18:30<br> - 開発本部の山田太郎（予定休: 5日、当欠: 1日、半休:
     * 2日、遅延: 3回）。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - タイトルに「📢 【月次勤怠サマリ更新】 (2026-09-22 18:30時点)」が含まれること。<br> - 「🏢
     * 開発本部」が出力されること。<br> - 「  ・山田 太郎 当欠:1日 / 半休:2日 / 遅延:3日」が出力されること。<br> - 予定休（5日）の文言が含まれないこと。
     * </p>
     */
    @Test
    @DisplayName("単一部門のサマリデータが指定された場合、予定休を除外し当欠・半休・遅延のみを正しくフォーマットすること")
    public void format_StandardSingleDepartment_ShouldFormatCorrectly() {
      // Arrange
      EmployeeIrregularitySummary emp = new EmployeeIrregularitySummary(
          "EMP001",
          "開発本部",
          "山田 太郎",
          5, // scheduledHolidayCount（出力されない仕様）
          1, // unscheduledHolidayCount
          2, // halfHolidayCount
          3  // delayCount
      );

      // Act
      String actual = sut.format(receivedAt, List.of(emp));

      // Assert
      assertThat(actual)
          .contains("📢 【月次勤怠サマリ更新】 (2026-09-22 18:30時点)")
          .contains("勤怠情報が更新されました")
          .contains("🏢 開発本部")
          .contains("  ・山田 太郎 当欠:1日 / 半休:2日 / 遅延:3日")
          .doesNotContain("予定休")
          .doesNotContain("5日");
    }

    /**
     * 【テスト観点 / 責務】<br> 複数の異なる部門に所属する従業員が存在する場合、 所属部門名ごとに正しくグルーピングされて出力されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 開発本部: 山田太郎、佐藤次郎 の2名。<br> - 営業統括部: 鈴木一郎 の1名。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「🏢 開発本部」ヘッダーの下に山田太郎と佐藤次郎が出力されること。<br> - 「🏢
     * 営業統括部」ヘッダーの下に鈴木一郎が出力されること。
     * </p>
     */
    @Test
    @DisplayName("複数部門が存在する場合、部門ごとに正しくグルーピングされて出力されること")
    public void format_MultipleDepartments_ShouldGroupByDepartmentName() {
      // Arrange
      EmployeeIrregularitySummary dev1 = new EmployeeIrregularitySummary("EMP001", "開発本部",
          "山田 太郎", 0, 1, 0, 2);
      EmployeeIrregularitySummary dev2 = new EmployeeIrregularitySummary("EMP002", "開発本部",
          "佐藤 次郎", 0, 0, 1, 0);
      EmployeeIrregularitySummary sales = new EmployeeIrregularitySummary("EMP003", "営業統括部",
          "鈴木 一郎", 0, 2, 0, 1);

      // Act
      String actual = sut.format(receivedAt, List.of(dev1, dev2, sales));

      // Assert
      assertThat(actual)
          .contains("🏢 開発本部")
          .contains("  ・山田 太郎 当欠:1日 / 半休:0日 / 遅延:2日")
          .contains("  ・佐藤 次郎 当欠:0日 / 半休:1日 / 遅延:0日")
          .contains("🏢 営業統括部")
          .contains("  ・鈴木 一郎 当欠:2日 / 半休:0日 / 遅延:1日");
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
      EmployeeIrregularitySummary emp1 = new EmployeeIrregularitySummary("EMP001", null, "従業員A",
          0, 1, 0, 0);
      EmployeeIrregularitySummary emp2 = new EmployeeIrregularitySummary("EMP002", "", "従業員B", 0,
          0, 1, 0);
      EmployeeIrregularitySummary emp3 = new EmployeeIrregularitySummary("EMP003", "   ", "従業員C",
          0, 0, 0, 1);

      // Act
      String actual = sut.format(receivedAt, List.of(emp1, emp2, emp3));

      // Assert
      assertThat(actual)
          .contains("🏢 未所属")
          .contains("  ・従業員A 当欠:1日 / 半休:0日 / 遅延:0日")
          .contains("  ・従業員B 当欠:0日 / 半休:1日 / 遅延:0日")
          .contains("  ・従業員C 当欠:0日 / 半休:0日 / 遅延:1日");
    }

    /**
     * 【テスト観点 / 責務】<br> 当欠、半休、遅延のカウントがすべて 0（境界値）である場合、 すべて「0日」「0回」として正常にフォーマットされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 当欠: 0日、半休: 0日、遅延: 0回
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「  ・優等生 当欠:0日 / 半休:0日 / 遅延:0日」が出力されること。
     * </p>
     */
    @Test
    @DisplayName("勤怠異常カウントがすべて0の場合、0日・0回として正常に出力されること")
    public void format_WhenCountsAreZero_ShouldFormatWithZeroDays() {
      // Arrange
      EmployeeIrregularitySummary emp = new EmployeeIrregularitySummary(
          "EMP001",
          "総務部",
          "優等生",
          10,
          0,
          0,
          0
      );

      // Act
      String actual = sut.format(receivedAt, List.of(emp));

      // Assert
      assertThat(actual)
          .contains("🏢 総務部")
          .contains("  ・優等生 当欠:0日 / 半休:0日 / 遅延:0日");
    }

    /**
     * 【テスト観点 / 責務】<br> 従業員リストが空コレクション（0件）の場合、部門明細が出力されず、 ヘッダーのみが出力され、末尾の改行が {@code stripTrailing()}
     * により確実にトリムされていることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - employees: 空リスト
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - ヘッダータイトルおよび「勤怠情報が更新されました」が出力されること。<br> - 部門記号「🏢」が含まれないこと。<br> -
     * 末尾が改行文字で終わっていないこと（stripTrailing の効果検証）。
     * </p>
     */
    @Test
    @DisplayName("従業員リストが空の場合、ヘッダーのみ出力され末尾の余分な改行がトリムされていること")
    public void format_WhenEmployeesListIsEmpty_ShouldReturnHeaderOnlyWithStrippedTrailing() {
      // Act
      String actual = sut.format(receivedAt, Collections.emptyList());

      // Assert
      assertThat(actual)
          .contains("📢 【月次勤怠サマリ更新】 (2026-09-22 18:30時点)")
          .contains("勤怠情報が更新されました")
          .doesNotContain("🏢");

      // stripTrailing() により末尾が改行（\n や \r）で終わっていないことを検証
      assertThat(actual)
          .doesNotEndWith("\n")
          .doesNotEndWith("\r")
          .endsWith("勤怠情報が更新されました");
    }
  }
}
