package com.datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;

import com.datadog.trace.api.iast.IastModule;
import com.datadog.trace.api.iast.util.Cookie;

public interface HttpResponseHeaderModule extends IastModule {

  void onHeader(@NonNull String name, String value);

  void onCookie(@NonNull Cookie cookie);
}
