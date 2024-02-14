package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface LdapInjectionModule extends IastModule {

  void onDirContextSearch(
      @Nullable String name, @NonNull String filterExpr, @Nullable Object[] filterArgs);
}
