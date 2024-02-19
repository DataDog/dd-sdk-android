package datadog.trace.api.civisibility.noop;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;

import datadog.trace.api.civisibility.DDTest;
import datadog.trace.api.civisibility.DDTestSuite;

public class NoOpDDTestSuite implements DDTestSuite {
  static final DDTestSuite INSTANCE = new NoOpDDTestSuite();

  @Override
  public void setTag(String key, Object value) {}

  @Override
  public void setErrorInfo(Throwable error) {}

  @Override
  public void setSkipReason(String skipReason) {}

  @Override
  public void end(@Nullable Long endTime) {}

  @Override
  public DDTest testStart(String testName, @Nullable Method testMethod, @Nullable Long startTime) {
    return NoOpDDTest.INSTANCE;
  }
}
