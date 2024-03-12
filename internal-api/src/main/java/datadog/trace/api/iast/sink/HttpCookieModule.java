package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import datadog.trace.api.iast.util.Cookie;
import androidx.annotation.NonNull;

public interface HttpCookieModule<T> extends IastModule {

  boolean isVulnerable(@NonNull final Cookie cookie);

  T getType();
}
