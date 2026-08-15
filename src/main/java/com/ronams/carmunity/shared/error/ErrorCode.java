package com.ronams.carmunity.shared.error;

import java.io.Serializable;
import org.springframework.http.HttpStatus;

/**
 * 업무 모듈과 공통 오류 변환 계층 사이에서 공유하는 HTTP API 오류 계약입니다.
 *
 * <p>HTTP API 계약이므로 status는 Spring {@link HttpStatus}를 직접 사용합니다. Domain 내부 모델은 이 계약에 의존하지 않습니다.
 */
public interface ErrorCode extends Serializable {

  /**
   * 클라이언트와 문서에서 사용하는 안정적인 오류 식별자를 반환합니다.
   *
   * @return 대문자 snake case 형식의 프로젝트 오류 코드입니다.
   */
  String code();

  /**
   * 오류 응답의 HTTP status를 반환합니다.
   *
   * @return 클라이언트가 실패 유형을 구분할 수 있는 HTTP status입니다.
   */
  HttpStatus status();

  /**
   * Problem Details의 title에 사용할 짧은 오류 제목을 반환합니다.
   *
   * @return 오류 범주를 나타내는 짧은 영문 제목입니다.
   */
  String title();

  /**
   * Problem Details의 detail에 사용할 사용자 노출 메시지를 반환합니다.
   *
   * @return 내부 구현 정보를 포함하지 않는 오류 설명입니다.
   */
  String detail();
}
