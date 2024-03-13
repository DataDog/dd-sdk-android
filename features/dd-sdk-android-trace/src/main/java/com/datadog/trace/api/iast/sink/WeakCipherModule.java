package com.datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;

import com.datadog.trace.api.iast.IastModule;

public interface WeakCipherModule extends IastModule {

  void onCipherAlgorithm(@NonNull String algorithm);
}
