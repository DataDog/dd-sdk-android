package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import androidx.annotation.NonNull;

public interface TrustBoundaryViolationModule extends IastModule {

  void onSessionValue(@NonNull String name, Object value);
}
