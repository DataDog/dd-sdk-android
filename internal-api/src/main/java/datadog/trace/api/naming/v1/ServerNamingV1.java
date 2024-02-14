package datadog.trace.api.naming.v1;

import datadog.trace.api.naming.NamingSchema;
import androidx.annotation.NonNull;

public class ServerNamingV1 implements NamingSchema.ForServer {

  @NonNull
  @Override
  public String operationForProtocol(@NonNull String protocol) {
    return protocol + ".server.request";
  }

  @NonNull
  @Override
  public String operationForComponent(@NonNull String component) {
    return "http.server.request";
  }
}
