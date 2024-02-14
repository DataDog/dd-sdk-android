package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface XssModule extends IastModule {

  void onXss(@NonNull String s);

  void onXss(@NonNull String s, @NonNull String clazz, @NonNull String method);

  void onXss(@NonNull char[] array);

  void onXss(@NonNull String format, @Nullable Object[] args);

  void onXss(@NonNull CharSequence s, @Nullable String file, int line);
}
