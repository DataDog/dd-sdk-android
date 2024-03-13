package com.datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;

import com.datadog.trace.api.iast.IastModule;

public interface WeakRandomnessModule extends IastModule {

  void onWeakRandom(@NonNull final Class<?> instance);
}
