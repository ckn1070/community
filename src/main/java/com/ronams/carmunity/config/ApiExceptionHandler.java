package com.ronams.carmunity.config;

import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import com.ronams.carmunity.shared.error.ErrorCode;
import com.ronams.carmunity.shared.error.FieldErrorDetail;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Spring MVC 예외를 프로젝트 공통 Problem Details 응답으로 변환합니다.
 *
 * <p>framework/common 예외는 중앙에서 처리하고, 업무 모듈별 오류는 {@link ApiException}의 {@link ErrorCode}로 연결합니다.
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class ApiExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  /**
   * DTO validation 실패를 {@code 400 INVALID_REQUEST}로 변환합니다.
   *
   * <p>필드별 오류는 {@code errors} extension으로 노출하고, Spring 내부 binding 객체는 응답에 포함하지 않습니다.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception, WebRequest request) {
    List<FieldErrorDetail> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                fieldError ->
                    new FieldErrorDetail(
                        fieldError.getField(),
                        fieldError.getCode(),
                        fieldError.getDefaultMessage()))
            .toList();

    return toResponseEntity(
        responseFactory.create(CommonErrorCode.INVALID_REQUEST, request, fieldErrors));
  }

  /**
   * 읽을 수 없는 request body를 {@code 400 INVALID_REQUEST}로 변환합니다.
   *
   * <p>JSON syntax 오류, 타입 불일치, 빈 body처럼 클라이언트가 요청 형식을 고쳐야 하는 경우입니다.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception, WebRequest request) {
    return toResponseEntity(responseFactory.create(CommonErrorCode.INVALID_REQUEST, request));
  }

  /** path·query parameter를 공개 타입으로 변환하지 못하면 {@code 400 INVALID_REQUEST}로 응답합니다. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException exception, WebRequest request) {
    return toResponseEntity(responseFactory.create(CommonErrorCode.INVALID_REQUEST, request));
  }

  /** 존재하는 resource가 지원하지 않는 HTTP method는 {@code 405 METHOD_NOT_ALLOWED}로 응답합니다. */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException exception, WebRequest request) {
    return toResponseEntity(responseFactory.create(CommonErrorCode.METHOD_NOT_ALLOWED, request));
  }

  /**
   * 지원하지 않는 Content-Type을 {@code 415 UNSUPPORTED_MEDIA_TYPE}으로 변환합니다.
   *
   * <p>endpoint가 받을 수 없는 media type은 validation 오류와 구분해 content negotiation 문제로 응답합니다.
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException exception, WebRequest request) {
    return toResponseEntity(
        responseFactory.create(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, request));
  }

  /** multipart parser가 상한을 넘긴 요청을 controller 진입 전에 413으로 종료합니다. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException exception, WebRequest request) {
    return toResponseEntity(responseFactory.create(CommonErrorCode.PAYLOAD_TOO_LARGE, request));
  }

  /**
   * application/web 경계에서 명시한 업무 오류를 해당 {@link ErrorCode} 응답으로 변환합니다.
   *
   * <p>공통 handler는 업무 모듈 내부 예외 타입을 직접 알지 않고, 공개 오류 계약만 해석합니다.
   */
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApiException(ApiException exception, WebRequest request) {
    ErrorCode errorCode = exception.errorCode();
    return toResponseEntity(responseFactory.create(errorCode, request));
  }

  /**
   * 미분류 예외를 {@code 500 INTERNAL_ERROR}로 변환합니다.
   *
   * <p>클라이언트 응답에는 stack trace, 내부 class 이름, persistence 세부 정보를 노출하지 않습니다. 로그도 원문 예외 메시지를 그대로 남기지 않고
   * request id와 요청 위치 중심으로 기록합니다.
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception, WebRequest request) {
    logUnexpectedException(exception, request);
    return toResponseEntity(responseFactory.create(CommonErrorCode.INTERNAL_ERROR, request));
  }

  private ResponseEntity<ProblemDetail> toResponseEntity(ProblemDetail problemDetail) {
    return ResponseEntity.status(problemDetail.getStatus()).body(problemDetail);
  }

  private void logUnexpectedException(Exception exception, WebRequest request) {
    ServletWebRequest servletWebRequest =
        request instanceof ServletWebRequest typedRequest ? typedRequest : null;
    String requestId =
        servletWebRequest != null ? ApiRequestIdContext.from(servletWebRequest.getRequest()) : "";
    String method =
        servletWebRequest != null ? servletWebRequest.getRequest().getMethod() : "unknown";
    String path =
        servletWebRequest != null ? servletWebRequest.getRequest().getRequestURI() : "unknown";

    log.atError()
        .addKeyValue("requestId", requestId)
        .addKeyValue("method", method)
        .addKeyValue("path", path)
        .addKeyValue("errorCode", CommonErrorCode.INTERNAL_ERROR.code())
        .addKeyValue("exceptionType", exception.getClass().getName())
        // 원문 예외 메시지는 비밀번호나 token 같은 민감값을 포함할 수 있으므로 class 이름만 남긴 wrapper로 대체합니다.
        .setCause(sanitizedForLogging(exception))
        .log(
            "예상하지 못한 예외를 INTERNAL_ERROR로 응답합니다. requestId={}, method={}, path={}, errorCode={}",
            requestId,
            method,
            path,
            CommonErrorCode.INTERNAL_ERROR.code());
  }

  // stack trace는 진단에 필요하지만 원문 message는 민감값을 포함할 수 있어 exception type으로 대체합니다.
  private Exception sanitizedForLogging(Exception exception) {
    Exception sanitized = new Exception(exception.getClass().getName());
    sanitized.setStackTrace(exception.getStackTrace());
    return sanitized;
  }
}
