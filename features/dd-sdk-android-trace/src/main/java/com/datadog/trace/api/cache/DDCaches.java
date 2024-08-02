package com.datadog.trace.api.cache;

public final class DDCaches {

  private DDCaches() {}

  /**
   * Creates a cache which cannot grow beyond a fixed capacity. Useful for caching relationships
   * between low cardinality but potentially unbounded keys with values, without risking using
   * unbounded space.
   *
   * @param capacity the cache's fixed capacity
   * @param <K> the key type
   * @param <V> the value type
   */
  public static <K, V> DDCache<K, V> newFixedSizeCache(final int capacity) {
    return new FixedSizeCache.ObjectHash<>(capacity);
  }


  public static <K, V> DDPartialKeyCache<K, V> newFixedSizePartialKeyCache(final int capacity) {
    return new FixedSizePartialKeyCache<>(capacity);
  }
}
