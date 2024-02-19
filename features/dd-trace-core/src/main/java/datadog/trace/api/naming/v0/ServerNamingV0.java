package datadog.trace.api.naming.v0;

import androidx.annotation.NonNull;

import datadog.trace.api.naming.NamingSchema;

public class ServerNamingV0 implements NamingSchema.ForServer {
  @NonNull
  @Override
  public String operationForProtocol(@NonNull String protocol) {
    if ("grpc".equals(protocol)) {
      return "grpc.server";
    }
    return protocol + ".request";
  }

  @NonNull
  @Override
  public String operationForComponent(@NonNull String component) {
    final String prefix;
    switch (component) {
      case "undertow-http-server":
        prefix = "undertow-http";
        break;
      case "akka-http-server":
        prefix = "akka-http";
        break;
      case "pekko-http-server":
        prefix = "pekko-http";
        break;
      case "netty":
      case "finatra":
      case "axway-http":
        prefix = component;
        break;
      case "spray-http-server":
        prefix = "spray-http";
        break;
      case "restlet-http-server":
        prefix = "restlet-http";
        break;
      case "synapse-server":
        prefix = "synapse";
        break;
      default:
        prefix = "servlet";
        break;
    }
    return prefix + ".request";
  }
}
