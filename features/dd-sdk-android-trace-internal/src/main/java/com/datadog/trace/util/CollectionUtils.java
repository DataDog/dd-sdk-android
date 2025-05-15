package com.datadog.trace.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CollectionUtils {

  /**
   * Converts the input to an immutable set if available on the current platform. Otherwise returns
   * the input as a set.
   *
   * @param input the input.
   * @param <T> the type of the elements of the input.
   * @return an immutable copy of the input if possible.
   */
  @SuppressWarnings("unchecked")
  public static <T> Set<T> tryMakeImmutableSet(Collection<T> input) {
    return new HashSet<>(input);
  }

  /**
   * Converts the input to an immutable list if available on the current platform. Otherwise returns
   * the input as a list.
   *
   * @param input the input.
   * @param <T> the type of the elements of the input.
   * @return an immutable copy of the input if possible.
   */
  @SuppressWarnings("unchecked")
  public static <T> List<T> tryMakeImmutableList(Collection<T> input) {
    return new ArrayList<>(input);
  }

  /**
   * Converts the input to an immutable map if available on the current platform. Otherwise returns
   * the input.
   *
   * @param input the input.
   * @param <K> the key type
   * @param <V> the value type
   * @return an immutable copy of the input if possible.
   */
  @SuppressWarnings("unchecked")
  public static <K, V> Map<K, V> tryMakeImmutableMap(Map<K, V> input) {
    return new HashMap<>(input);
  }

}
