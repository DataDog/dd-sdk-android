package com.datadog.trace.api.civisibility.coverage;

import androidx.annotation.Nullable;

public interface TestReportHolder {
  @Nullable
  TestReport getReport();
}
