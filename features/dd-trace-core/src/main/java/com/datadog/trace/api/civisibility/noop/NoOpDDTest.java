package com.datadog.trace.api.civisibility.noop;

import androidx.annotation.Nullable;

import com.datadog.trace.api.civisibility.DDTest;

public class NoOpDDTest implements DDTest {
  static final DDTest INSTANCE = new NoOpDDTest();

  @Override
  public void setTag(String key, Object value) {}

  @Override
  public void setErrorInfo(@Nullable Throwable error) {}

  @Override
  public void setSkipReason(@Nullable String skipReason) {}

  @Override
  public void end(@Nullable Long endTime) {}
}
