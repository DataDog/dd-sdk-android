package com.datadog.trace.api.naming.v1;

import androidx.annotation.NonNull;

import com.datadog.trace.api.naming.NamingSchema;

public class CacheNamingV1 implements NamingSchema.ForCache {
  @NonNull
  @Override
  public String operation(@NonNull String cacheSystem) {
    return cacheSystem + ".command";
  }

  @Override
  public String service(@NonNull String cacheSystem) {
    return null;
  }
}
