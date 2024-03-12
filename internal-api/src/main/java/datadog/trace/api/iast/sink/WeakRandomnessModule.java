package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import androidx.annotation.NonNull;

public interface WeakRandomnessModule extends IastModule {

  void onWeakRandom(@NonNull final Class<?> instance);
}
