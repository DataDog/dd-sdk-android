/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.decorators;

import com.datadog.opentracing.DDSpanContext;

/**
 * Span decorators are called when new tags are written and proceed to various remappings and
 * enrichments
 */
public abstract class AbstractDecorator {

  private String matchingTag;

  private Object matchingValue;

  private String replacementTag;

  private String replacementValue;

  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (this.getMatchingValue() == null || this.getMatchingValue().equals(value)) {
      final String targetTag = getReplacementTag() == null ? tag : getReplacementTag();
      final String targetValue =
          getReplacementValue() == null ? String.valueOf(value) : getReplacementValue();

      context.setTag(targetTag, targetValue);
      return false;
    } else {
      return true;
    }
  }

  public String getMatchingTag() {
    return matchingTag;
  }

  public void setMatchingTag(final String tag) {
    this.matchingTag = tag;
  }

  public Object getMatchingValue() {
    return matchingValue;
  }

  public void setMatchingValue(final Object value) {
    this.matchingValue = value;
  }

  public String getReplacementTag() {
    return replacementTag;
  }

  public void setReplacementTag(final String targetTag) {
    this.replacementTag = targetTag;
  }

  public String getReplacementValue() {
    return replacementValue;
  }

  public void setReplacementValue(final String targetValue) {
    this.replacementValue = targetValue;
  }
}
