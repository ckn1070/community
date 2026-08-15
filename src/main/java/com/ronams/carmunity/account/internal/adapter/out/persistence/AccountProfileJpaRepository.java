package com.ronams.carmunity.account.internal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountProfileJpaRepository extends JpaRepository<AccountProfileJpaEntity, Long> {

  boolean existsByDisplayNameNormalized(String displayNameNormalized);
}
