package datadog.trace.api.naming.v1;

import androidx.annotation.NonNull;

import datadog.trace.api.naming.NamingSchema;

public class DatabaseNamingV1 implements NamingSchema.ForDatabase {
  @Override
  public String normalizedName(@NonNull String rawName) {
    switch (rawName) {
      case "mongo":
        return "mongodb";
      case "sqlserver":
        return "mssql";
    }
    return rawName;
  }

  @NonNull
  @Override
  public String operation(@NonNull String databaseType) {
    final String prefix;
    switch (databaseType) {
      case "elasticsearch.rest":
        prefix = "elasticsearch";
        break;
      case "opensearch.rest":
        prefix = "opensearch";
        break;
      default:
        prefix = databaseType;
    }
    // already normalized when calling dbType on the decorator. It saves one operation
    return prefix + ".query";
  }

  @Override
  public String service(@NonNull String databaseType) {
    return null;
  }
}
