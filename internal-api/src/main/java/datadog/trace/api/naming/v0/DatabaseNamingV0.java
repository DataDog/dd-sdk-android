package datadog.trace.api.naming.v0;

import datadog.trace.api.naming.NamingSchema;
import androidx.annotation.NonNull;

public class DatabaseNamingV0 implements NamingSchema.ForDatabase {

  private final boolean allowInferredServices;

  public DatabaseNamingV0(boolean allowInferredServices) {
    this.allowInferredServices = allowInferredServices;
  }

  @Override
  public String normalizedName(@NonNull String rawName) {
    return rawName;
  }

  @NonNull
  @Override
  public String operation(@NonNull String databaseType) {
    String postfix = ".query";
    if ("couchbase".equals(databaseType)) {
      postfix = ".call";
    }
    return databaseType + postfix;
  }

  @Override
  public String service(@NonNull String databaseType) {
    if (allowInferredServices) {
      return databaseType;
    }
    return null;
  }
}
