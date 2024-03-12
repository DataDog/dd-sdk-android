package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import androidx.annotation.Nullable;

public interface ApplicationModule extends IastModule {

  void onRealPath(@Nullable String realPath);
}
