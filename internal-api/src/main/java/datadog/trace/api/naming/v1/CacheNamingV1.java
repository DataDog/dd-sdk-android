package datadog.trace.api.naming.v1;

import datadog.trace.api.naming.NamingSchema;
import androidx.annotation.NonNull;

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
