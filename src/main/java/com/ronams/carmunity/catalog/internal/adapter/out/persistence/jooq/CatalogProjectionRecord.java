package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import java.math.BigDecimal;
import org.jooq.Record;

/** jOOQ Record의 null·숫자 변환을 상세 read model mapping에 맞게 제한합니다. */
final class CatalogProjectionRecord {

  private final Record record;

  CatalogProjectionRecord(Record record) {
    this.record = record;
  }

  <T> T getObject(String field, Class<T> type) {
    return record.get(field, type);
  }

  String getString(String field) {
    return record.get(field, String.class);
  }

  long getLong(String field) {
    Long value = record.get(field, Long.class);
    return value == null ? 0L : value;
  }

  int getInt(String field) {
    Integer value = record.get(field, Integer.class);
    return value == null ? 0 : value;
  }

  boolean getBoolean(String field) {
    Boolean value = record.get(field, Boolean.class);
    return Boolean.TRUE.equals(value);
  }

  BigDecimal getBigDecimal(String field) {
    return record.get(field, BigDecimal.class);
  }
}
