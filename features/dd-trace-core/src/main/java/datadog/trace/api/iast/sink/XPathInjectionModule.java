package datadog.trace.api.iast.sink;

import androidx.annotation.Nullable;

import datadog.trace.api.iast.IastModule;

public interface XPathInjectionModule extends IastModule {
  void onExpression(@Nullable final String expression);
}
