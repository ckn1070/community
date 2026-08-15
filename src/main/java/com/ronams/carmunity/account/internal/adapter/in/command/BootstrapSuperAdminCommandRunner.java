package com.ronams.carmunity.account.internal.adapter.in.command;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.BootstrapSuperAdminAccountCommand;
import com.ronams.carmunity.account.internal.application.port.in.BootstrapSuperAdminAccountUseCase;
import com.ronams.carmunity.account.internal.application.result.BootstrapSuperAdminAccountResult;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 최초 최고 관리자 bootstrap 운영 command adapter입니다. */
@RequiredArgsConstructor
@Component
class BootstrapSuperAdminCommandRunner implements ApplicationRunner {

  private static final String COMMAND_OPTION = "command";
  private static final String BOOTSTRAP_SUPER_ADMIN_COMMAND = "bootstrap-super-admin";
  private static final String ADMIN_EMAIL_OPTION = "admin-email";
  private static final String ADMIN_DISPLAY_NAME_OPTION = "admin-display-name";

  private final BootstrapSuperAdminAccountUseCase useCase;
  private final BootstrapCommandInputReader inputReader;
  private final CommandOutput output;
  private final CommandExitHandler exitHandler;

  /** bootstrap command가 아닌 애플리케이션 실행에서는 process 종료 없이 그대로 통과합니다. */
  @Override
  public void run(ApplicationArguments args) {
    if (!isBootstrapCommand(args)) {
      return;
    }

    try {
      executeBootstrap(args);
    } catch (CommandInputException exception) {
      output.error("INVALID_INPUT: " + exception.getMessage());
      exitHandler.exit(2);
    } catch (ApiException exception) {
      CommandFailure failure = mapFailure(exception);
      output.error(failure.resultCode());
      exitHandler.exit(failure.exitCode());
    } catch (RuntimeException exception) {
      output.error("FAILED");
      exitHandler.exit(1);
    }
  }

  /** 초기 비밀번호는 command option으로 받지 않고 masked 입력 후 즉시 command 객체로만 전달합니다. */
  private void executeBootstrap(ApplicationArguments args) {
    String email = optionValue(args, ADMIN_EMAIL_OPTION).orElseGet(this::readEmail);
    String displayName =
        optionValue(args, ADMIN_DISPLAY_NAME_OPTION).orElseGet(this::readDisplayName);

    char[] password = inputReader.readPassword("Initial password: ");
    char[] passwordConfirmation = inputReader.readPassword("Confirm initial password: ");
    try {
      if (!Arrays.equals(password, passwordConfirmation)) {
        output.error("INVALID_INPUT: password confirmation does not match");
        exitHandler.exit(2);
        return;
      }

      BootstrapSuperAdminAccountResult result =
          useCase.execute(
              new BootstrapSuperAdminAccountCommand(email, displayName, new String(password)));
      printResult(result);
      exitHandler.exit(0);
    } finally {
      Arrays.fill(password, '\0');
      Arrays.fill(passwordConfirmation, '\0');
    }
  }

  /** `--command=bootstrap-super-admin`일 때만 최고 관리자 bootstrap 흐름을 실행합니다. */
  private boolean isBootstrapCommand(ApplicationArguments args) {
    return optionValue(args, COMMAND_OPTION)
        .filter(BOOTSTRAP_SUPER_ADMIN_COMMAND::equals)
        .isPresent();
  }

  /** 빈 option 값은 잘못된 값으로 고정하지 않고 interactive 입력 fallback 대상으로 봅니다. */
  private Optional<String> optionValue(ApplicationArguments args, String optionName) {
    List<String> values = args.getOptionValues(optionName);
    if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(values.getFirst());
  }

  /** command option이 없을 때만 interactive email 입력으로 보완합니다. */
  private String readEmail() {
    return inputReader.readLine("Admin email: ");
  }

  /** command option이 없을 때만 interactive display name 입력으로 보완합니다. */
  private String readDisplayName() {
    return inputReader.readLine("Admin display name: ");
  }

  /** 운영 script가 판단할 수 있는 result code만 출력하고 password나 내부 DB id는 출력하지 않습니다. */
  private void printResult(BootstrapSuperAdminAccountResult result) {
    if (result.status() == BootstrapSuperAdminAccountResult.Status.CREATED) {
      output.println("CREATED accountPublicId=" + result.accountPublicId().orElseThrow());
      output.println("NEXT_ACTION change the initial password before using any API");
      return;
    }
    output.println("ALREADY_BOOTSTRAPPED");
  }

  /** 운영자가 script에서 분기할 수 있게 application 오류를 command result code와 exit code로 축약합니다. */
  private CommandFailure mapFailure(ApiException exception) {
    if (exception.errorCode() == CommonErrorCode.INVALID_REQUEST) {
      return new CommandFailure("INVALID_INPUT", 2);
    }
    if (exception.errorCode() == AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED) {
      return new CommandFailure("ROLE_NOT_CONFIGURED", 3);
    }
    if (exception.errorCode() == AccountErrorCode.EMAIL_ALREADY_USED
        || exception.errorCode() == AccountErrorCode.DISPLAY_NAME_ALREADY_USED) {
      return new CommandFailure("CONFLICT", 4);
    }
    return new CommandFailure("FAILED", 1);
  }

  private record CommandFailure(String resultCode, int exitCode) {}
}

/** 운영 command 입력을 실제 console 구현과 테스트 대역으로 분리하는 내부 port입니다. */
interface BootstrapCommandInputReader {

  /** email과 display name처럼 secret이 아닌 command 입력을 읽습니다. */
  String readLine(String prompt);

  /** 초기 비밀번호는 echo 없는 입력으로 받고 호출자가 사용 직후 char 배열을 지웁니다. */
  char[] readPassword(String prompt);
}

/** 운영 command 결과 출력을 테스트에서 검증 가능하게 분리하는 내부 port입니다. */
interface CommandOutput {

  void println(String message);

  void error(String message);
}

/** command 완료 후 process 종료를 테스트 대역으로 대체할 수 있게 분리한 내부 port입니다. */
interface CommandExitHandler {

  /** 운영에서는 process를 종료하지만 테스트에서는 exit code만 검증합니다. */
  void exit(int exitCode);
}

/** masked console 입력을 보장할 수 없거나 입력이 끊긴 경우 bootstrap을 중단하는 예외입니다. */
class CommandInputException extends RuntimeException {

  CommandInputException(String message) {
    super(message);
  }
}

/** JVM console을 통해 초기 관리자 입력을 받는 운영 입력 adapter입니다. */
@Component
class SystemConsoleBootstrapCommandInputReader implements BootstrapCommandInputReader {

  /** 일반 입력도 console이 없으면 bootstrap을 계속하지 않습니다. */
  @Override
  public String readLine(String prompt) {
    String value = console().readLine("%s", prompt);
    if (value == null) {
      throw new CommandInputException("CONSOLE_REQUIRED");
    }
    return value;
  }

  /** 비밀번호 입력은 terminal echo를 남기지 않는 JVM console API만 허용합니다. */
  @Override
  public char[] readPassword(String prompt) {
    char[] value = console().readPassword("%s", prompt);
    if (value == null) {
      throw new CommandInputException("CONSOLE_REQUIRED");
    }
    return value;
  }

  /** masked password 입력을 보장할 수 없는 실행 환경에서는 bootstrap을 실패시킵니다. */
  private Console console() {
    Console console = System.console();
    if (console == null) {
      throw new CommandInputException("CONSOLE_REQUIRED");
    }
    return console;
  }
}

/** 운영 command 결과를 표준 출력과 표준 에러로 분리해 쓰는 출력 adapter입니다. */
@Component
class StandardCommandOutput implements CommandOutput {

  private static final PrintStream STANDARD_OUT =
      new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
  private static final PrintStream STANDARD_ERR =
      new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

  /** 성공과 no-op 결과는 stdout으로 내보내 shell script가 분리해서 읽을 수 있게 합니다. */
  @Override
  public void println(String message) {
    STANDARD_OUT.println(message);
  }

  /** 입력 오류와 실패 결과는 stderr로 내보냅니다. */
  @Override
  public void error(String message) {
    STANDARD_ERR.println(message);
  }
}

/** bootstrap command 종료 코드를 실제 process 종료로 연결하는 운영 adapter입니다. */
@Component
class SystemCommandExitHandler implements CommandExitHandler {

  /** 운영 command에서는 결과 출력 후 명시적인 exit code로 process를 종료합니다. */
  @Override
  public void exit(int exitCode) {
    System.exit(exitCode);
  }
}
