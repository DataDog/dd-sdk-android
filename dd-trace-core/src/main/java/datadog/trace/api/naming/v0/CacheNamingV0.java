package datadog.trace.api.naming.v0;

import androidx.annotation.NonNull;

import datadog.trace.api.naming.NamingSchema;

public class CacheNamingV0 implements NamingSchema.ForCache {

  private final boolean allowInferredServices;

  public CacheNamingV0(boolean allowInferredServices) {
    this.allowInferredServices = allowInferredServices;
  }

  @NonNull
  @Override
  public String operation(@NonNull String cacheSystem) {
    String postfix;
    switch (cacheSystem) {
      case "ignite":
        postfix = ".cache";
        break;
      case "hazelcast":
        postfix = ".invoke";
        break;
      default:
        postfix = ".query";
        break;
    }
    return cacheSystem + postfix;
  }

  @Override
  public String service(@NonNull String cacheSystem) {
    if (!allowInferredServices) {
      return null;
    }
    if ("hazelcast".equals(cacheSystem)) {
      return "hazelcast-sdk";
    }
    return cacheSystem;
  }
}
