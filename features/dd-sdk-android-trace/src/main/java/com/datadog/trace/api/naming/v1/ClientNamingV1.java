package com.datadog.trace.api.naming.v1;

import androidx.annotation.NonNull;

import com.datadog.trace.api.naming.NamingSchema;

public class ClientNamingV1 implements NamingSchema.ForClient {

  @NonNull
  private String normalizeProtocol(@NonNull final String protocol) {
    switch (protocol) {
      case "http":
      case "https":
        return "http";
      case "ftp":
      case "ftps":
        return "ftp";
      default:
        return protocol;
    }
  }

  @NonNull
  @Override
  public String operationForProtocol(@NonNull String protocol) {
    return normalizeProtocol(protocol) + ".client.request";
  }

  @NonNull
  @Override
  public String operationForComponent(@NonNull String component) {
    return "http.client.request";
  }
}
