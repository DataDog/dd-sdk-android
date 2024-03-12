package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import androidx.annotation.NonNull;

public interface WeakHashModule extends IastModule {

  void onHashingAlgorithm(@NonNull String algorithm);
}
