package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import androidx.annotation.NonNull;

public interface WeakCipherModule extends IastModule {

  void onCipherAlgorithm(@NonNull String algorithm);
}
