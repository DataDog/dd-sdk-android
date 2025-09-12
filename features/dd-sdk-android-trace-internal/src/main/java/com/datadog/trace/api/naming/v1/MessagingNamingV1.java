package com.datadog.trace.api.naming.v1;

import androidx.annotation.NonNull;

import com.datadog.trace.api.naming.NamingSchema;

public class MessagingNamingV1 implements NamingSchema.ForMessaging {

  private String normalizeForCloud(@NonNull final String messagingSystem) {
    switch (messagingSystem) {
      case "sns":
      case "sqs":
        return "aws." + messagingSystem;
      case "google-pubsub":
        return "gcp.pubsub";
      default:
        return messagingSystem;
    }
  }

  @NonNull
  @Override
  public String outboundOperation(@NonNull String messagingSystem) {
    return normalizeForCloud(messagingSystem) + ".send";
  }

  @Override
  public String outboundService(@NonNull String messagingSystem, boolean useLegacyTracing) {
    return null;
  }

  @NonNull
  @Override
  public String inboundOperation(@NonNull String messagingSystem) {
    return normalizeForCloud(messagingSystem) + ".process";
  }

  @Override
  public String inboundService(@NonNull String messagingSystem, boolean useLegacyTracing) {
    return null;
  }

  @Override
  @NonNull
  public String timeInQueueService(@NonNull String messagingSystem) {
    return messagingSystem + "-queue";
  }

  @NonNull
  @Override
  public String timeInQueueOperation(@NonNull String messagingSystem) {
    return normalizeForCloud(messagingSystem) + ".deliver";
  }
}
