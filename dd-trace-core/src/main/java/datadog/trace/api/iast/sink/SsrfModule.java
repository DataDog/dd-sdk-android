package datadog.trace.api.iast.sink;

import androidx.annotation.Nullable;

import datadog.trace.api.iast.IastModule;

public interface SsrfModule extends IastModule {

  void onURLConnection(@Nullable Object url);

  void onURLConnection(@Nullable String url, @Nullable Object host, @Nullable Object uri);
}
