package com.datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;

import com.datadog.trace.api.iast.IastModule;
import com.datadog.trace.api.iast.util.Cookie;

public interface HttpCookieModule<T> extends IastModule {

  boolean isVulnerable(@NonNull final Cookie cookie);

  T getType();
}
