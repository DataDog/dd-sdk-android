package datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;

import datadog.trace.api.iast.IastModule;

public interface WeakHashModule extends IastModule {

  void onHashingAlgorithm(@NonNull String algorithm);
}
