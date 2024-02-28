package datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;

import datadog.trace.api.iast.IastModule;
import datadog.trace.api.iast.util.Cookie;

public interface HttpCookieModule<T> extends IastModule {

  boolean isVulnerable(@NonNull final Cookie cookie);

  T getType();
}
