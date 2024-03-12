/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.appsec.api.blocking;

public enum BlockingContentType {
  /**
   * Automatically choose between HTML and JSON, depending on the value of the <code>Accept</code>
   * header. If the preference value is the same, {@link #JSON} will be preferred.
   */
  AUTO,
  /** An HTTP response. */
  HTML,
  /** A JSON response. */
  JSON,
  /** No body in the response */
  NONE,
}
