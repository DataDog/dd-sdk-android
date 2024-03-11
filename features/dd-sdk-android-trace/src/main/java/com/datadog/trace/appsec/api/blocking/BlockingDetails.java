/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.appsec.api.blocking;

import java.util.Map;

public class BlockingDetails {
  public final int statusCode;
  public final BlockingContentType blockingContentType;
  public final Map<String, String> extraHeaders;

  public BlockingDetails(
          int statusCode, BlockingContentType blockingContentType, Map<String, String> extraHeaders) {
    this.statusCode = statusCode;
    this.blockingContentType = blockingContentType;
    this.extraHeaders = extraHeaders;
  }
}
