package com.ronams.carmunity.account.internal.adapter.out.community;

import com.ronams.carmunity.account.api.CommunityAuthorProfile;
import com.ronams.carmunity.account.api.CommunityAuthorProfileLookup;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JdbcCommunityAuthorProfileLookup implements CommunityAuthorProfileLookup {
  private final JdbcClient jdbc;

  @Override
  public Map<UUID, CommunityAuthorProfile> findAll(Set<UUID> accountIds) {
    if (accountIds.isEmpty()) return Map.of();
    return jdbc
        .sql(
            "SELECT a.public_id,p.display_name,a.status FROM account a JOIN account_profile p ON p.account_id=a.id WHERE a.public_id IN (:ids)")
        .param("ids", accountIds)
        .query(
            (row, number) ->
                Map.entry(
                    (UUID) row.getObject("public_id"),
                    new CommunityAuthorProfile(
                        row.getString("display_name"),
                        !"WITHDRAWN".equals(row.getString("status")))))
        .list()
        .stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
