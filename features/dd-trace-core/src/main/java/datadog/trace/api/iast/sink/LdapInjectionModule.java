package datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import datadog.trace.api.iast.IastModule;

public interface LdapInjectionModule extends IastModule {

  void onDirContextSearch(
      @Nullable String name, @NonNull String filterExpr, @Nullable Object[] filterArgs);
}
