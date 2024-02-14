package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import androidx.annotation.NonNull;

public interface HeaderInjectionModule extends IastModule {

  void onHeader(@NonNull String name, String value);
}
