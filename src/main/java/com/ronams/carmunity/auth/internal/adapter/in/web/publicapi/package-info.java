/**
 * auth public Web API adapter입니다.
 *
 * <p>미인증 사용자가 호출해야 하는 session 생성 같은 공개 인증 endpoint만 두고, password/token 원문은 application log로 전달하지
 * 않습니다.
 *
 * <p>HTTP 요청/응답 DTO를 application command/result와 매핑하고, token lifecycle 판단은 application service에
 * 위임합니다.
 */
package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;
