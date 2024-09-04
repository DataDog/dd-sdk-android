/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.legacy.trace.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Set this annotation to a method so the dd-java-agent considers it for tracing. */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Trace {

  /** The operation name to set. By default it takes the method's name */
  String operationName() default "";

  /** The resource name. By default it uses the same value as the operation name */
  String resourceName() default "";
}
