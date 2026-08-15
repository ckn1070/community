package com.ronams.carmunity.account.internal.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * auth_role 기준 데이터의 JPA 저장 모델입니다.
 *
 * <p>가입 흐름은 seed된 role id만 참조하고, role code 체계의 소유권은 DB seed와 domain enum이 함께 고정합니다.
 */
@Entity
@Table(name = "auth_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuthRoleJpaEntity {

  @Id private Long id;

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  Long id() {
    return id;
  }

  String code() {
    return code;
  }
}
