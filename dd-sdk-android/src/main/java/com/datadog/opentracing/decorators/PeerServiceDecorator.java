/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.decorators;

import com.datadog.opentracing.DDSpanContext;
import io.opentracing.tag.Tags;

public class PeerServiceDecorator extends AbstractDecorator {
  public PeerServiceDecorator() {
    super();
    this.setMatchingTag(Tags.PEER_SERVICE.getKey());
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    context.setServiceName(String.valueOf(value));
    return false;
  }
}
