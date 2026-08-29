package com.ronams.carmunity.catalog.internal.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.stereotype.Component;

/** PostgreSQL projection SQL의 named parameter binding과 jOOQ record 변환을 한 경계에서 담당합니다. */
@Component
final class CatalogJooqQuery {

  private final DSLContext dsl;

  CatalogJooqQuery(DSLContext dsl) {
    this.dsl = dsl;
  }

  List<CatalogRecord> fetch(String sql, Map<String, ?> parameters) {
    ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
    MapSqlParameterSource parameterSource = new MapSqlParameterSource(parameters);
    String preparedSql = NamedParameterUtils.substituteNamedParameters(parsedSql, parameterSource);
    Object[] bindings = NamedParameterUtils.buildValueArray(parsedSql, parameterSource, null);
    return dsl.fetch(preparedSql, bindings).stream().map(CatalogRecord::new).toList();
  }

  static final class CatalogRecord {
    private final Record record;

    private CatalogRecord(Record record) {
      this.record = record;
    }

    <T> T getObject(String field, Class<T> type) throws SQLException {
      return record.get(field, type);
    }

    String getString(String field) throws SQLException {
      return record.get(field, String.class);
    }

    long getLong(String field) throws SQLException {
      Long value = record.get(field, Long.class);
      return value == null ? 0L : value;
    }

    int getInt(String field) throws SQLException {
      Integer value = record.get(field, Integer.class);
      return value == null ? 0 : value;
    }

    boolean getBoolean(String field) throws SQLException {
      Boolean value = record.get(field, Boolean.class);
      return Boolean.TRUE.equals(value);
    }

    BigDecimal getBigDecimal(String field) throws SQLException {
      return record.get(field, BigDecimal.class);
    }
  }
}
