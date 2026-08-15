package com.ronams.carmunity.auth.internal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuthTokenEventJpaRepository extends JpaRepository<AuthTokenEventJpaEntity, Long> {}
