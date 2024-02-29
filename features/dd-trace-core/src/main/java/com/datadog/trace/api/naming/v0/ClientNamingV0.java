package com.datadog.trace.api.naming.v0;

import androidx.annotation.NonNull;

import com.datadog.trace.api.naming.NamingSchema;

public class ClientNamingV0 implements NamingSchema.ForClient {

  @NonNull
  @Override
  public String operationForProtocol(@NonNull String protocol) {
    final String postfix;
    switch (protocol) {
      case "grpc":
        postfix = ".client";
        break;
      case "rmi":
        postfix = ".invoke";
        break;
      default:
        postfix = ".request";
    }

    return protocol + postfix;
  }

  @NonNull
  @Override
  public String operationForComponent(@NonNull String component) {
    switch (component) {
      case "play-ws":
      case "okhttp":
        return component + ".request";
      case "netty-client":
        return "netty.client.request";
      case "akka-http-client":
        return "akka-http.client.request";
      case "pekko-http-client":
        return "pekko-http.client.request";
      case "jax-rs.client":
        return "jax-rs.client.call";
      default:
        return "http.request";
    }
  }
}
