package com.ronams.carmunity.shared.error;

/**
 * application/web 경계에서 업무 실패를 공통 API 오류 계약으로 전달하는 예외입니다.
 *
 * <p>domain 내부에서는 HTTP 응답 변환 책임을 갖지 않도록 이 예외를 사용하지 않습니다.
 */
public class ApiException extends RuntimeException {

  private final ErrorCode errorCode;

  /**
   * 원인 예외 없이 공통 오류 코드만 전달합니다.
   *
   * @param errorCode 클라이언트에 노출할 오류 계약입니다.
   */
  public ApiException(ErrorCode errorCode) {
    super(errorCode.detail());
    this.errorCode = errorCode;
  }

  /**
   * 하위 계층 예외를 보존하면서 클라이언트에는 공통 오류 코드만 노출합니다.
   *
   * @param errorCode 클라이언트에 노출할 오류 계약입니다.
   * @param cause 서버 내부에서 추적할 원인 예외입니다.
   */
  public ApiException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.detail(), cause);
    this.errorCode = errorCode;
  }

  /**
   * 예외를 HTTP 응답으로 변환할 때 사용할 오류 계약을 반환합니다.
   *
   * @return 클라이언트에 노출할 오류 계약입니다.
   */
  public ErrorCode errorCode() {
    return errorCode;
  }
}
