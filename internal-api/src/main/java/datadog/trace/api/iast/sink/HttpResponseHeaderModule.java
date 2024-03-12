package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import datadog.trace.api.iast.util.Cookie;
import androidx.annotation.NonNull;

public interface HttpResponseHeaderModule extends IastModule {

  void onHeader(@NonNull String name, String value);

  void onCookie(@NonNull Cookie cookie);
}
