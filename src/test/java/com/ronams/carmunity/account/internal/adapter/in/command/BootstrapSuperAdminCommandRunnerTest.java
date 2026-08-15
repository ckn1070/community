package com.ronams.carmunity.account.internal.adapter.in.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.account.internal.application.command.BootstrapSuperAdminAccountCommand;
import com.ronams.carmunity.account.internal.application.port.in.BootstrapSuperAdminAccountUseCase;
import com.ronams.carmunity.account.internal.application.result.BootstrapSuperAdminAccountResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/** 최초 최고 관리자 운영 command adapter의 argument, prompt, exit code 매핑을 검증합니다. */
class BootstrapSuperAdminCommandRunnerTest {

  private FakeBootstrapSuperAdminAccountUseCase useCase;
  private FakeBootstrapCommandInputReader inputReader;
  private FakeCommandOutput output;
  private FakeCommandExitHandler exitHandler;
  private BootstrapSuperAdminCommandRunner runner;

  @BeforeEach
  void setUp() {
    useCase = new FakeBootstrapSuperAdminAccountUseCase();
    inputReader = new FakeBootstrapCommandInputReader();
    output = new FakeCommandOutput();
    exitHandler = new FakeCommandExitHandler();
    runner = new BootstrapSuperAdminCommandRunner(useCase, inputReader, output, exitHandler);
  }

  /** command option이 없으면 일반 web application 실행을 방해하지 않고 아무 작업도 하지 않습니다. */
  @Test
  void ignoresRunWhenBootstrapCommandIsNotRequested() {
    runner.run(new DefaultApplicationArguments("--server.port=0"));

    assertThat(useCase.lastCommand).isNull();
    assertThat(exitHandler.exitCodes).isEmpty();
  }

  /** option과 password 입력 대역으로 usecase를 실행하고 email/password/hash 없이 CREATED와 exit 0을 출력합니다. */
  @Test
  void executesBootstrapCommandWithOptionsAndMaskedPasswordConfirmation() {
    UUID accountPublicId = UUID.fromString("166584b8-0ffe-4ee6-b794-80de36275d19");
    useCase.nextResult =
        BootstrapSuperAdminAccountResult.created(
            accountPublicId, "admin@example.com", "Initial Admin");
    inputReader.passwords.add("correct horse battery staple".toCharArray());
    inputReader.passwords.add("correct horse battery staple".toCharArray());

    runner.run(
        new DefaultApplicationArguments(
            "--command=bootstrap-super-admin",
            "--admin-email=admin@example.com",
            "--admin-display-name=Initial Admin"));

    assertThat(useCase.lastCommand)
        .isEqualTo(
            new BootstrapSuperAdminAccountCommand(
                "admin@example.com", "Initial Admin", "correct horse battery staple"));
    assertThat(exitHandler.exitCodes).containsExactly(0);
    assertThat(String.join("\n", output.lines))
        .contains("CREATED")
        .contains(accountPublicId.toString());
    assertThat(String.join("\n", output.lines)).doesNotContain("admin@example.com");
    assertThat(String.join("\n", output.lines)).doesNotContain("correct horse battery staple");
    assertThat(String.join("\n", output.lines)).doesNotContain("{argon2}");
  }

  /** email/displayName option이 없으면 비밀이 아닌 값만 prompt로 받고 password는 masked 입력으로 받습니다. */
  @Test
  void promptsMissingNonSecretOptionsBeforePasswordInput() {
    useCase.nextResult = BootstrapSuperAdminAccountResult.alreadyBootstrapped();
    inputReader.lines.add("admin@example.com");
    inputReader.lines.add("Initial Admin");
    inputReader.passwords.add("correct horse battery staple".toCharArray());
    inputReader.passwords.add("correct horse battery staple".toCharArray());

    runner.run(new DefaultApplicationArguments("--command=bootstrap-super-admin"));

    assertThat(useCase.lastCommand.email()).isEqualTo("admin@example.com");
    assertThat(useCase.lastCommand.displayName()).isEqualTo("Initial Admin");
    assertThat(exitHandler.exitCodes).containsExactly(0);
    assertThat(String.join("\n", output.lines)).contains("ALREADY_BOOTSTRAPPED");
  }

  /** password 확인이 다르면 usecase를 호출하지 않고 INVALID_INPUT exit code로 종료합니다. */
  @Test
  void exitsWithInvalidInputWhenPasswordConfirmationDoesNotMatch() {
    inputReader.passwords.add("correct horse battery staple".toCharArray());
    inputReader.passwords.add("different horse battery staple".toCharArray());

    runner.run(
        new DefaultApplicationArguments(
            "--command=bootstrap-super-admin",
            "--admin-email=admin@example.com",
            "--admin-display-name=Initial Admin"));

    assertThat(useCase.lastCommand).isNull();
    assertThat(exitHandler.exitCodes).containsExactly(2);
    assertThat(String.join("\n", output.errorLines)).contains("INVALID_INPUT");
    assertThat(String.join("\n", output.errorLines)).doesNotContain("correct horse battery staple");
  }

  private static class FakeBootstrapSuperAdminAccountUseCase
      implements BootstrapSuperAdminAccountUseCase {

    private BootstrapSuperAdminAccountResult nextResult =
        BootstrapSuperAdminAccountResult.alreadyBootstrapped();
    private BootstrapSuperAdminAccountCommand lastCommand;

    @Override
    public BootstrapSuperAdminAccountResult execute(BootstrapSuperAdminAccountCommand command) {
      lastCommand = command;
      return nextResult;
    }
  }

  private static class FakeBootstrapCommandInputReader implements BootstrapCommandInputReader {

    private final List<String> lines = new ArrayList<>();
    private final List<char[]> passwords = new ArrayList<>();

    @Override
    public String readLine(String prompt) {
      return lines.removeFirst();
    }

    @Override
    public char[] readPassword(String prompt) {
      return passwords.removeFirst();
    }
  }

  private static class FakeCommandOutput implements CommandOutput {

    private final List<String> lines = new ArrayList<>();
    private final List<String> errorLines = new ArrayList<>();

    @Override
    public void println(String message) {
      lines.add(message);
    }

    @Override
    public void error(String message) {
      errorLines.add(message);
    }
  }

  private static class FakeCommandExitHandler implements CommandExitHandler {

    private final List<Integer> exitCodes = new ArrayList<>();

    @Override
    public void exit(int exitCode) {
      exitCodes.add(exitCode);
    }
  }
}
