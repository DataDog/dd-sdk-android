/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.appsec.api.blocking;

public class BlockingException extends RuntimeException {
  public BlockingException() {}

  public BlockingException(String message) {
    super(message);
  }

  public BlockingException(String message, Throwable cause) {
    super(message, cause);
  }

  public BlockingException(Throwable cause) {
    super(cause);
  }
}
