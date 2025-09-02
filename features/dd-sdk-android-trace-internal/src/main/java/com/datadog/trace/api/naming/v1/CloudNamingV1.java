package com.datadog.trace.api.naming.v1;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.trace.api.naming.NamingSchema;
import com.datadog.trace.api.naming.SpanNaming;
import com.datadog.trace.util.Strings;

import java.util.Locale;

public class CloudNamingV1 implements NamingSchema.ForCloud {
  @NonNull
  @Override
  public String operationForRequest(
      @NonNull final String provider,
      @NonNull final String cloudService,
      @NonNull final String qualifiedOperation) {
    // only aws sdk is right now implemented
    switch (qualifiedOperation) {
        // sdk 1.x format
      case "SQS.SendMessage":
      case "SQS.SendMessageBatch":
        // sdk 2.x format
      case "Sqs.SendMessage":
      case "Sqs.SendMessageBatch":
        return SpanNaming.instance().namingSchema().messaging().outboundOperation("sqs");

      case "Sqs.ReceiveMessage":
      case "SQS.ReceiveMessage":
        return SpanNaming.instance().namingSchema().messaging().inboundOperation("sqs");
      case "Sns.Publish":
      case "SNS.Publish":
        return SpanNaming.instance().namingSchema().messaging().outboundOperation("sns");
      default:
        final String lowercaseService = cloudService.toLowerCase(Locale.ROOT);
        return Strings.join(".", provider, lowercaseService, "request"); // aws.s3.request
    }
  }

  @Override
  public String serviceForRequest(
      @NonNull final String provider, @Nullable final String cloudService) {
    return null;
  }

  @NonNull
  @Override
  public String operationForFaas(@NonNull final String provider) {
    // for now only aws is implemented. For the future provider might be used to return specific
    // function as a service name
    // (e.g. azure automation)
    return "aws.lambda.invoke";
  }
}
