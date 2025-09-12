package com.datadog.trace.api.naming.v0;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.trace.api.naming.NamingSchema;

public class CloudNamingV0 implements NamingSchema.ForCloud {
  private final boolean allowInferredServices;

  public CloudNamingV0(boolean allowInferredServices) {
    this.allowInferredServices = allowInferredServices;
  }

  @NonNull
  @Override
  public String operationForRequest(
      @NonNull final String provider,
      @NonNull final String cloudService,
      @NonNull final String qualifiedOperation) {
    // only aws sdk is right now implemented
    return "aws.http";
  }

  @Override
  public String serviceForRequest(
      @NonNull final String provider, @Nullable final String cloudService) {
    if (!allowInferredServices) {
      return null;
    }

    // we only manage aws. Future switch for other cloud providers will be needed in the future
    if (cloudService == null) {
      return "java-aws-sdk";
    }

    switch (cloudService) {
      case "sns":
      case "sqs":
        return cloudService;
      default:
        return "java-aws-sdk";
    }
  }

  @NonNull
  @Override
  public String operationForFaas(@NonNull final String provider) {
    return "dd-tracer-serverless-span";
  }
}
