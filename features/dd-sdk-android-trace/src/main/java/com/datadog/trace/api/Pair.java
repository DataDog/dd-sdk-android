package com.datadog.trace.api;

import androidx.annotation.Nullable;

import java.util.Objects;

public final class Pair<T, U> {

  public static <T, U> Pair<T, U> of(T left, U right) {
    return new Pair<>(left, right);
  }

  private final T left;
  private final U right;

  Pair(@Nullable T left, @Nullable U right) {
    this.left = left;
    this.right = right;
  }

  @Nullable
  public T getLeft() {
    return left;
  }

  @Nullable
  public U getRight() {
    return right;
  }

  public boolean hasLeft() {
    return null != left;
  }

  public boolean hasRight() {
    return null != right;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof Pair) {
      Pair<?, ?> pair = (Pair<?, ?>) o;
      return Objects.equals(left, pair.left) && Objects.equals(right, pair.right);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return 31 * (null == left ? 0 : left.hashCode()) + (null == right ? 0 : right.hashCode());
  }
}
