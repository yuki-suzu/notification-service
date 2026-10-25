package com.computer_rescuer.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent;
import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent.DirectReminderEmployee;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.Notification.DestinationType;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.domain.service.DirectReminderReportFormatter;
import com.computer_rescuer.notification.domain.service.DirectReminderReportFormatter.FailureItem;
import com.computer_rescuer.notification.domain.service.UnstampedDirectReminderMessageFormatter;
import com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksUserApi;
import com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksUserApi.LineworksUserResponse;
import com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksUserApi.UserName;
import com.computer_rescuer.notification.infrastructure.lineworks.support.LineworksAccountResolver;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SendUnstampedDirectReminderService} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 勤怠未打刻者検知イベント（{@link UnstampedDirectReminderEvent}）を受け取り、 各未打刻従業員に対して LINE WORKS
 * の個別ダイレクトメッセージ（DM）の送信パイプラインをオーケストレーションします。 送信結果（成功件数・失敗件数・警告明細）を集約し、管理者トークルームへ配信結果レポートを通知します。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>入力値の境界値（空リスト・null）に対する早期リターン挙動の検証</li>
 *   <li>LINE WORKS 送信ゲートウェイの未登録異常系（{@link IllegalStateException}）の検証</li>
 *   <li>代理送信モード（特定の proxy メールが含まれる場合）の一括代理配送判定の検証</li>
 *   <li>通常モードにおけるアカウント導出・実在確認・表示名（ニックネーム）解決・本人DM配送の検証</li>
 *   <li>LINE WORKS アカウント不在時およびメール不正時における管理者代理通知フォールバックの検証</li>
 *   <li>外部通信例外発生時におけるフェイルセーフ（後続従業員処理の継続、二次障害防止）の検証</li>
 *   <li>Java 21/25 仕様に準拠した {@code getFirst()} / {@code getLast()} によるコレクションアクセス</li>
 *   <li>C1（分岐網羅）100% を達成するための全条件分岐ルートの網羅</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SendUnstampedDirectReminderService 単体テスト")
class SendUnstampedDirectReminderServiceTest {

  @Mock
  private NotificationSender lineWorksSender;

  @Mock
  private LineworksAccountResolver lineworksAccountResolver;

  @Mock
  private UnstampedDirectReminderMessageFormatter reminderMessageFormatter;

  @Mock
  private DirectReminderReportFormatter reportFormatter;

  @Mock
  private LineworksUserApi lineworksUserApi;

  @Captor
  private ArgumentCaptor<Notification> notificationCaptor;

  @Captor
  private ArgumentCaptor<DirectReminderEmployee> displayEmployeeCaptor;

  @Captor
  private ArgumentCaptor<List<DirectReminderEmployee>> successItemsCaptor;

  @Captor
  private ArgumentCaptor<List<FailureItem>> failureItemsCaptor;

  private SendUnstampedDirectReminderService sut;

  /**
   * 各テスト実行前の共通初期化処理。
   * <p>
   * 送信ゲートウェイ（LINE WORKS）のモックを含むテスト対象サービスインスタンスを構築します。
   * </p>
   */
  @BeforeEach
  void setUp() {
    lenient().when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);

    sut = new SendUnstampedDirectReminderService(
        List.of(lineWorksSender),
        lineworksAccountResolver,
        reminderMessageFormatter,
        reportFormatter,
        lineworksUserApi
    );
  }

  @Nested
  @DisplayName("入力境界値および前提条件チェックのテスト")
  class PreconditionAndBoundaryTest {

    /**
     * 【テスト観点 / 責務】<br> 従業員リストが null のイベントが渡された場合、送信処理を行わずに早期リターンすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - employees が null である {@link UnstampedDirectReminderEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外が発生せず正常終了すること。<br> -
     * 外部送信ゲートウェイ（{@link NotificationSender#send(Notification)}）が一度も呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("employeesがnullの場合、送信処理を行わず正常に早期リターンすること")
    public void execute_WhenEmployeesIsNull_ShouldReturnImmediatelyWithoutSending() {
      // Arrange
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(null);

      // Act
      sut.execute(event);

      // Assert
      verify(lineWorksSender, never()).send(any());
      verify(lineworksUserApi, never()).fetchUser(any());
      verify(reportFormatter, never()).format(any(), any(), anyBoolean());
    }

    /**
     * 【テスト観点 / 責務】<br> 従業員リストが空コレクション（0件）のイベントが渡された場合、送信処理を行わずに早期リターンすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - employees が空リストである {@link UnstampedDirectReminderEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外が発生せず正常終了すること。<br> -
     * 外部送信ゲートウェイ（{@link NotificationSender#send(Notification)}）が一度も呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("employeesが空リストの場合、送信処理を行わず正常に早期リターンすること")
    public void execute_WhenEmployeesIsEmpty_ShouldReturnImmediatelyWithoutSending() {
      // Arrange
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(
          Collections.emptyList());

      // Act
      sut.execute(event);

      // Assert
      verify(lineWorksSender, never()).send(any());
      verify(lineworksUserApi, never()).fetchUser(any());
      verify(reportFormatter, never()).format(any(), any(), anyBoolean());
    }

    /**
     * 【テスト観点 / 責務】<br> LINE WORKS 送信ゲートウェイが DI コンテキスト内に未登録の場合に、 適切なエラーメッセージを伴う
     * {@link IllegalStateException} がスローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 空の送信ゲートウェイリストを用いて {@link SendUnstampedDirectReminderService}
     * を初期化。<br> - 有効な従業員1名を含むイベントを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link IllegalStateException} がスローされること。<br> - 例外メッセージが「LINE
     * WORKS 送信ゲートウェイが未登録です」と一致すること。
     * </p>
     */
    @Test
    @DisplayName("LINE WORKS送信ゲートウェイが未登録の場合、IllegalStateExceptionをスローすること")
    public void execute_WhenLineWorksSenderNotRegistered_ShouldThrowIllegalStateException() {
      // Arrange: LINE WORKS 送信ゲートウェイが存在しない状態でサービスを構築
      SendUnstampedDirectReminderService serviceWithoutSender = new SendUnstampedDirectReminderService(
          Collections.emptyList(),
          lineworksAccountResolver,
          reminderMessageFormatter,
          reportFormatter,
          lineworksUserApi
      );

      DirectReminderEmployee employee = new DirectReminderEmployee("EMP001", "taro@example.com",
          "山田 太郎");
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(employee));

      // Act & Assert
      assertThatThrownBy(() -> serviceWithoutSender.execute(event))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("LINE WORKS 送信ゲートウェイが未登録です");
    }
  }

  @Nested
  @DisplayName("通常送信モード（isProxy = false）のテスト")
  class NormalModeTest {

    /**
     * 【テスト観点 / 責務】<br> 通常モード時、LINE WORKS Directory API でユーザーが実在しニックネームが存在する場合、
     * ニックネームを表示名に差し替えて本人宛てに DM を送信し、管理者レポートへ成功計上されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 社員番号「EMP001」、氏名「山田 太郎」、メール「taro.yamada@example.com」の従業員。<br> -
     * アカウントリゾルバーが "taro.yamada@example" を返却。<br> - Directory API が userId "lw-user-001"、nickname
     * "タロー" を持つレスポンスを返却。<br> - フォーマッターは 1 回目（基本文面）と 2 回目（タロー宛て文面）で異なるテキストを返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - リマインドフォーマッターが 2 回（元従業員 DTO とニックネーム反映後 DTO）呼び出されること。<br> - 従業員宛て
     * DM 通知の宛先が "lw-user-001"、宛先種別が USER であること。<br> - 管理者レポート送信が呼び出され、成功リストに1件、失敗リストに0件で渡されること。
     * </p>
     */
    @Test
    @DisplayName("ユーザーが実在しニックネームがある場合、ニックネームを反映して本人宛てDMを送信し成功計上すること")
    public void execute_NormalMode_WhenUserExistsWithNickname_ShouldSendToUserAndRecordSuccess() {
      // Arrange
      DirectReminderEmployee emp = new DirectReminderEmployee("EMP001", "taro.yamada@example.com",
          "山田 太郎");
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(emp));

      when(lineworksAccountResolver.resolveUserIdFromEmail("taro.yamada@example.com")).thenReturn(
          "taro.yamada@example");

      LineworksUserResponse userResponse = new LineworksUserResponse(
          "lw-user-001",
          new UserName("山田", "太郎"),
          "タロー"
      );
      when(lineworksUserApi.fetchUser("taro.yamada@example")).thenReturn(Optional.of(userResponse));

      // 1回目（resolveDispatchPlan）と 2回目（createDirectPlan の displayEmp）の呼び出しに対応
      when(reminderMessageFormatter.format(any(DirectReminderEmployee.class)))
          .thenReturn("未打刻です。打刻をお願いします。")
          .thenReturn("⚠️ タロー さん、打刻をお願いします。");

      when(reportFormatter.format(any(), any(), eq(false))).thenReturn("📢 配信レポート本文");

      // Act
      sut.execute(event);

      // Assert
      // 本人向け DM 送信 + 管理者向けレポート送信 の計2回 send が呼ばれる
      verify(lineWorksSender, times(2)).send(notificationCaptor.capture());
      List<Notification> sentNotifications = notificationCaptor.getAllValues();

      // 本人向け DM の検証
      Notification directNotification = sentNotifications.getFirst();
      assertThat(directNotification.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(directNotification.destinationType()).isEqualTo(DestinationType.USER);
      assertThat(directNotification.targetId()).isEqualTo("lw-user-001");
      assertThat(directNotification.message()).isEqualTo("⚠️ タロー さん、打刻をお願いします。");

      // フォーマッターの呼び出し引数検証（1回目は元の氏名、2回目はニックネーム「タロー」）
      verify(reminderMessageFormatter, times(2)).format(displayEmployeeCaptor.capture());
      List<DirectReminderEmployee> formattedEmployees = displayEmployeeCaptor.getAllValues();
      assertThat(formattedEmployees.getFirst().fullName()).isEqualTo("山田 太郎");
      assertThat(formattedEmployees.getLast().fullName()).isEqualTo("タロー");

      // 管理者向けサマリーレポート通知の検証
      Notification adminNotification = sentNotifications.getLast();
      assertThat(adminNotification.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(adminNotification.destinationType()).isEqualTo(DestinationType.CHANNEL);
      assertThat(adminNotification.targetId()).isNull();
      assertThat(adminNotification.message()).isEqualTo("📢 配信レポート本文");

      // レポートフォーマッターへ渡された成否リストの検証
      verify(reportFormatter).format(successItemsCaptor.capture(), failureItemsCaptor.capture(),
          eq(false));
      assertThat(successItemsCaptor.getValue()).containsExactly(emp);
      assertThat(failureItemsCaptor.getValue()).isEmpty();
    }

    /**
     * 【テスト観点 / 責務】<br> 通常モード時、LINE WORKS ユーザーは実在するが表示名（ニックネーム・姓名）が空白の場合、
     * 元の勤怠マスタ氏名（emp.fullName()）に安全にフォールバックして本人宛て DM を送信することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 氏名「佐藤 次郎」の従業員。<br> - Directory API が nickname なし、userName
     * が空文字のプロファイルを返却（resolveDisplayName() が空文字列）。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - リマインド文面整形用 DTO の氏名が、元の「佐藤 次郎」のままフォーマッターに渡されること。<br> -
     * 送信成功として計上されること。
     * </p>
     */
    @Test
    @DisplayName("LINE WORKS側の表示名が空白の場合、勤怠マスタの元氏名にフォールバックして本人宛てDMを送信すること")
    public void execute_NormalMode_WhenUserExistsWithoutDisplayName_ShouldFallbackToOriginalFullName() {
      // Arrange
      DirectReminderEmployee emp = new DirectReminderEmployee("EMP002", "jiro.sato@example.com",
          "佐藤 次郎");
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(emp));

      when(lineworksAccountResolver.resolveUserIdFromEmail("jiro.sato@example.com")).thenReturn(
          "jiro.sato@example");

      // resolveDisplayName() が空文字となるレスポンス
      LineworksUserResponse userResponse = new LineworksUserResponse("lw-user-002",
          new UserName("", ""), "");
      when(lineworksUserApi.fetchUser("jiro.sato@example")).thenReturn(Optional.of(userResponse));

      when(reminderMessageFormatter.format(any(DirectReminderEmployee.class)))
          .thenReturn("未打刻です。")
          .thenReturn("⚠️ 佐藤 次郎 さん、打刻をお願いします。");

      // Act
      sut.execute(event);

      // Assert
      verify(reminderMessageFormatter, times(2)).format(displayEmployeeCaptor.capture());
      List<DirectReminderEmployee> formattedEmployees = displayEmployeeCaptor.getAllValues();
      assertThat(formattedEmployees.getFirst().fullName()).isEqualTo("佐藤 次郎");
      assertThat(formattedEmployees.getLast().fullName()).isEqualTo("佐藤 次郎");

      verify(lineWorksSender, times(2)).send(notificationCaptor.capture());
      Notification directNotification = notificationCaptor.getAllValues().getFirst();
      assertThat(directNotification.targetId()).isEqualTo("lw-user-002");
    }

    /**
     * 【テスト観点 / 責務】<br> LINE WORKS Directory API でユーザーが検出できなかった場合（404 等で Optional.empty()）、 targetId
     * を null（システム管理者宛て）として代理通知文面を送信し、レポート上は「失敗・警告」に計上されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - メールアドレス「unknown@example.com」の従業員。<br> - アカウントリゾルバーが
     * "unknown@example" を返却。<br> - Directory API が {@link Optional#empty()} を返却。<br> - 基本文面が
     * "基本リマインド文面"。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 代理通知の targetId が null（システム管理者宛て）であること。<br> - メッセージ本文に「📢 【管理者代理通知
     * / 未打刻リマインド】」のフォールバックヘッダーが付与されていること。<br> - レポートフォーマッターの failureItems
     * に、理由「アカウント解決不可のため管理者へ代理送信」として計上されること。<br> - successItems は空であること。
     * </p>
     */
    @Test
    @DisplayName("Directory APIでユーザー不在の場合、管理者宛て代理通知を送信し失敗リストに計上すること")
    public void execute_NormalMode_WhenUserNotFound_ShouldSendProxyNotificationToAdminAndRecordFailure() {
      // Arrange
      DirectReminderEmployee emp = new DirectReminderEmployee("EMP999", "unknown@example.com",
          "名無 権兵衛");
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(emp));

      when(reminderMessageFormatter.format(emp)).thenReturn("基本リマインド文面");
      when(lineworksAccountResolver.resolveUserIdFromEmail("unknown@example.com")).thenReturn(
          "unknown@example");
      when(lineworksUserApi.fetchUser("unknown@example")).thenReturn(Optional.empty());

      // Act
      sut.execute(event);

      // Assert
      verify(reminderMessageFormatter, times(1)).format(emp);
      verify(lineWorksSender, times(2)).send(notificationCaptor.capture());
      List<Notification> sentNotifications = notificationCaptor.getAllValues();

      // 代理通知の検証
      Notification fallbackNotification = sentNotifications.getFirst();
      assertThat(fallbackNotification.targetId()).isNull(); // targetId=null でシステム管理者宛て
      assertThat(fallbackNotification.destinationType()).isEqualTo(DestinationType.USER);
      assertThat(fallbackNotification.message())
          .contains("📢 【管理者代理通知 / 未打刻リマインド】")
          .contains("※対象者のLINE WORKSアカウントが解決できないため、管理者に代理配信されました。")
          .contains("基本リマインド文面");

      // レポートフォーマッターへの引数検証
      verify(reportFormatter).format(successItemsCaptor.capture(), failureItemsCaptor.capture(),
          eq(false));
      assertThat(successItemsCaptor.getValue()).isEmpty();

      List<FailureItem> failures = failureItemsCaptor.getValue();
      assertThat(failures).hasSize(1);
      assertThat(failures.getFirst().employee()).isEqualTo(emp);
      assertThat(failures.getFirst().reason()).isEqualTo(
          "アカウント解決不可のため管理者へ代理送信");
    }

    /**
     * 【テスト観点 / 責務】<br> メールアドレス不正等でアカウントリゾルバーが空文字列を返却した場合、 Directory API
     * を一切呼び出すことなく即座に管理者代理通知フォールバックを行い、失敗計上されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - メールアドレスが未設定（空文字）の従業員。<br> - アカウントリゾルバーが空文字 "" を返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link LineworksUserApi#fetchUser(String)} が一度も呼び出されないこと。<br> -
     * 管理者代理通知が送信され、レポート上は失敗として計上されること。
     * </p>
     */
    @Test
    @DisplayName("候補IDが空文字の場合、Directory APIを呼ばずに管理者宛て代理通知を行い失敗計上すること")
    public void execute_NormalMode_WhenCandidateIdIsBlank_ShouldSkipDirectoryApiAndSendProxyNotification() {
      // Arrange
      DirectReminderEmployee emp = new DirectReminderEmployee("EMP003", "", "鈴木 一朗");
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(emp));

      when(reminderMessageFormatter.format(emp)).thenReturn("基本リマインド");
      when(lineworksAccountResolver.resolveUserIdFromEmail("")).thenReturn("");

      // Act
      sut.execute(event);

      // Assert
      verify(reminderMessageFormatter, times(1)).format(emp);
      verify(lineworksUserApi, never()).fetchUser(any());

      verify(reportFormatter).format(successItemsCaptor.capture(), failureItemsCaptor.capture(),
          eq(false));
      assertThat(successItemsCaptor.getValue()).isEmpty();
      assertThat(failureItemsCaptor.getValue()).hasSize(1);
      assertThat(failureItemsCaptor.getValue().getFirst().reason()).isEqualTo(
          "アカウント解決不可のため管理者へ代理送信");
    }
  }

  @Nested
  @DisplayName("代理送信モード（isProxy = true）のテスト")
  class ProxyModeTest {

    /**
     * 【テスト観点 / 責務】<br> イベント内のいずれかの従業員のメールアドレスが "proxy"（大文字小文字不問）で始まる場合、 イベント全体が代理送信モード（isProxy =
     * true）と判定され、全対象者の通知が targetId = null（システム管理者）宛てに 送信され、すべて「成功」としてレポート計上されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 従業員A: "proxy-user@example.com"<br> - 従業員B:
     * "regular.user@example.com"<br> - いずれかに proxy が含まれているため、全体が isProxy = true となる。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - アカウントリゾルバーおよび Directory API が一度も呼び出されないこと。<br> - 従業員A、Bのいずれの個別通知も
     * targetId が null（システム管理者宛て）であること。<br> -
     * 本文は元の基本リマインド文面（reminderMessageFormatter.format）のままであること。<br> - 管理者レポートフォーマッターが isProxy = true
     * で呼び出されること。<br> - 成功リストに2名とも追加され、失敗リストは空であること。
     * </p>
     */
    @Test
    @DisplayName("従業員にproxyメールが含まれる場合、全件システム管理者へ代理送信し成功計上＆注記フラグをtrueにすること")
    public void execute_ProxyMode_WhenAnyEmployeeHasProxyEmail_ShouldSendAllToAdminAndRecordSuccess() {
      // Arrange
      DirectReminderEmployee emp1 = new DirectReminderEmployee("EMP001", "PROXY-TEST@example.com",
          "テスト 太郎");
      DirectReminderEmployee emp2 = new DirectReminderEmployee("EMP002", "regular@example.com",
          "通常 次郎");
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(emp1, emp2));

      when(reminderMessageFormatter.format(emp1)).thenReturn("リマインド本文1");
      when(reminderMessageFormatter.format(emp2)).thenReturn("リマインド本文2");

      // Act
      sut.execute(event);

      // Assert
      // 代理送信モードのため外部アカウント解決は一切行われないこと
      verify(lineworksAccountResolver, never()).resolveUserIdFromEmail(any());
      verify(lineworksUserApi, never()).fetchUser(any());

      // 個別通知 2件 + レポート 1件 = 計3件の送信
      verify(lineWorksSender, times(3)).send(notificationCaptor.capture());
      List<Notification> sentNotifications = notificationCaptor.getAllValues();

      // 個別代理送信 1件目の検証
      Notification notif1 = sentNotifications.getFirst();
      assertThat(notif1.targetId()).isNull();
      assertThat(notif1.destinationType()).isEqualTo(DestinationType.USER);
      assertThat(notif1.message()).isEqualTo("リマインド本文1");

      // 個別代理送信 2件目の検証
      Notification notif2 = sentNotifications.get(1);
      assertThat(notif2.targetId()).isNull();
      assertThat(notif2.destinationType()).isEqualTo(DestinationType.USER);
      assertThat(notif2.message()).isEqualTo("リマインド本文2");

      // レポートフォーマッター呼び出し検証（第3引数 isProxy が true）
      verify(reportFormatter).format(successItemsCaptor.capture(), failureItemsCaptor.capture(),
          eq(true));
      assertThat(successItemsCaptor.getValue()).containsExactly(emp1, emp2);
      assertThat(failureItemsCaptor.getValue()).isEmpty();
    }
  }

  @Nested
  @DisplayName("耐障害性・フェイルセーフ・例外系のテスト")
  class FaultToleranceAndExceptionTest {

    /**
     * 【テスト観点 / 責務】<br> 1人目の個別従業員への送信処理で外部通信例外（RuntimeException）が発生した場合でも、
     * 例外が安全に捕捉されて失敗リストに計上され、2人目の従業員の送信処理が中断されずに継続することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 2名の従業員（EMP001, EMP002）が存在。<br> - 1人目の送信試行時に
     * lineWorksSender.send が RuntimeException("LINE WORKS API 通信タイムアウト") をスロー。<br> -
     * 2人目の送信試行は正常に成功。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 全体として例外で中断せず、最後まで処理が完了すること。<br> - 失敗リストに EMP001 が「送信エラー: LINE
     * WORKS API 通信タイムアウト」の理由で記録されること。<br> - 成功リストに EMP002 が記録されること。<br> - 最終的に管理者レポートが正常に送信されること。
     * </p>
     */
    @Test
    @DisplayName("個別送信で外部通信例外が発生しても後続処理を継続し、失敗理由を正しくレポートに記録すること")
    public void execute_IndividualSendThrowsException_ShouldCatchAndRecordFailureAndContinueNextEmployee() {
      // Arrange
      DirectReminderEmployee emp1 = new DirectReminderEmployee("EMP001", "error@example.com",
          "障害 太郎");
      DirectReminderEmployee emp2 = new DirectReminderEmployee("EMP002", "success@example.com",
          "順調 次郎");
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(emp1, emp2));

      when(reminderMessageFormatter.format(any(DirectReminderEmployee.class))).thenReturn("文面");
      when(lineworksAccountResolver.resolveUserIdFromEmail("error@example.com")).thenReturn(
          "error@example");
      when(lineworksAccountResolver.resolveUserIdFromEmail("success@example.com")).thenReturn(
          "success@example");

      LineworksUserResponse user1 = new LineworksUserResponse("user-001",
          new UserName("障害", "太郎"), "");
      LineworksUserResponse user2 = new LineworksUserResponse("user-002",
          new UserName("順調", "次郎"), "");
      when(lineworksUserApi.fetchUser("error@example")).thenReturn(Optional.of(user1));
      when(lineworksUserApi.fetchUser("success@example")).thenReturn(Optional.of(user2));

      // 1人目の送信時のみ例外をスロー、2人目およびレポート送信は正常動作
      doThrow(new RuntimeException("LINE WORKS API 通信タイムアウト"))
          .doNothing()
          .when(lineWorksSender).send(any());

      // Act
      sut.execute(event);

      // Assert
      // 1人目の送信(失敗) + 2人目の送信(成功) + レポート送信(成功) = 計3回 send が呼ばれる
      verify(lineWorksSender, times(3)).send(any());

      // レポート集計内容の検証
      verify(reportFormatter).format(successItemsCaptor.capture(), failureItemsCaptor.capture(),
          eq(false));
      assertThat(successItemsCaptor.getValue()).containsExactly(emp2);

      List<FailureItem> failures = failureItemsCaptor.getValue();
      assertThat(failures).hasSize(1);
      assertThat(failures.getFirst().employee()).isEqualTo(emp1);
      assertThat(failures.getFirst().reason()).isEqualTo(
          "送信エラー: LINE WORKS API 通信タイムアウト");
    }

    /**
     * 【テスト観点 / 責務】<br> 全従業員の個別送信完了後、管理者トークルームへのレポート送信時に外部例外が発生した場合でも、
     * 【二次障害防止ガード】により例外が安全に握りつぶされ、上位（Kafka Consumer 等）へ例外を伝播させないことを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 1名の正常な従業員データが存在。<br> -
     * 個別送信は成功するが、最後の管理者レポート送信（Notification.ofLineWorksAlert）時に lineWorksSender.send が
     * RuntimeException("トークルーム送信権限エラー") をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@code assertThatCode(...).doesNotThrowAnyException()}
     * により、例外が一切スローされないこと。
     * </p>
     */
    @Test
    @DisplayName("管理者レポート送信で例外が発生しても二次障害防止ガードにより安全に握りつぶされること")
    public void execute_AdminReportSendThrowsException_ShouldCatchSafelyWithoutThrowingException() {
      // Arrange
      DirectReminderEmployee emp = new DirectReminderEmployee("EMP001", "taro@example.com",
          "山田 太郎");
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(emp));

      when(reminderMessageFormatter.format(any(DirectReminderEmployee.class))).thenReturn("文面");
      when(lineworksAccountResolver.resolveUserIdFromEmail("taro@example.com")).thenReturn(
          "taro@example");
      LineworksUserResponse user = new LineworksUserResponse("user-001",
          new UserName("山田", "太郎"), "");
      when(lineworksUserApi.fetchUser("taro@example")).thenReturn(Optional.of(user));

      when(reportFormatter.format(any(), any(), eq(false))).thenReturn("レポート");

      // 1回目の本人DM送信は成功、2回目の管理者レポート送信で例外スロー
      doNothing()
          .doThrow(new RuntimeException("トークルーム送信権限エラー"))
          .when(lineWorksSender).send(any());

      // Act & Assert
      assertThatCode(() -> sut.execute(event))
          .doesNotThrowAnyException();

      verify(lineWorksSender, times(2)).send(any());
      verify(reportFormatter).format(any(), any(), eq(false));
    }
  }
}
