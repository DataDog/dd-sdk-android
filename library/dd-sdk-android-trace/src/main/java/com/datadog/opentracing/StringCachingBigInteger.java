/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing;

import java.math.BigInteger;
import java.util.Random;

/**
 * Because we are using BigInteger for Trace and Span Id, the toString() operator may result in
 * heavy computation and string allocation overhead. In order to limit this, we are caching the
 * result of toString, thereby taking advantage of the immutability of BigInteger.
 */
public class StringCachingBigInteger extends BigInteger {

  private String cachedString;

  public StringCachingBigInteger(byte[] val) {
    super(val);
  }

  public StringCachingBigInteger(int signum, byte[] magnitude) {
    super(signum, magnitude);
  }

  public StringCachingBigInteger(String val, int radix) {
    super(val, radix);
  }

  public StringCachingBigInteger(String val) {
    super(val);
  }

  public StringCachingBigInteger(int numBits, Random rnd) {
    super(numBits, rnd);
  }

  public StringCachingBigInteger(int bitLength, int certainty, Random rnd) {
    super(bitLength, certainty, rnd);
  }

  @Override
  public String toString() {
    if (cachedString == null) {
      this.cachedString = super.toString();
    }
    return cachedString;
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
