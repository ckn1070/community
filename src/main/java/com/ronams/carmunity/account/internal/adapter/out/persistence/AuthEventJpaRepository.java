package com.ronams.carmunity.account.internal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuthEventJpaRepository extends JpaRepository<AuthEventJpaEntity, Long> {}
