package com.datadog.trace.api.naming.v0;

import androidx.annotation.NonNull;

import com.datadog.trace.api.Config;
import com.datadog.trace.api.naming.NamingSchema;

public class MessagingNamingV0 implements NamingSchema.ForMessaging {
  private final boolean allowInferredServices;

  public MessagingNamingV0(final boolean allowInferredServices) {
    this.allowInferredServices = allowInferredServices;
  }

  @NonNull
  @Override
  public String outboundOperation(@NonNull final String messagingSystem) {
    if ("amqp".equals(messagingSystem)) {
      return "amqp.command";
    }
    return messagingSystem + ".produce";
  }

  @Override
  public String outboundService(@NonNull final String messagingSystem, boolean useLegacyTracing) {
    return inboundService(messagingSystem, useLegacyTracing);
  }

  @NonNull
  @Override
  public String inboundOperation(@NonNull final String messagingSystem) {
    switch (messagingSystem) {
      case "amqp":
        return "amqp.command";
      case "sqs":
        return "aws.http";
      default:
        return messagingSystem + ".consume";
    }
  }

  @Override
  public String inboundService(@NonNull final String messagingSystem, boolean useLegacyTracing) {
    if (allowInferredServices) {
      return useLegacyTracing ? messagingSystem : Config.get().getServiceName();
    } else {
      return null;
    }
  }

  @Override
  @NonNull
  public String timeInQueueService(@NonNull final String messagingSystem) {
    return messagingSystem;
  }

  @NonNull
  @Override
  public String timeInQueueOperation(@NonNull String messagingSystem) {
    if ("sqs".equals(messagingSystem)) {
      return "aws.http";
    }
    return messagingSystem + ".deliver";
  }
}
