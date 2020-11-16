/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.decorators;

import com.datadog.opentracing.DDSpanContext;
import com.datadog.trace.api.DDSpanTypes;
import com.datadog.trace.api.DDTags;
import io.opentracing.tag.Tags;

/**
 * This span decorator leverages DB tags. It allows the dev to define a custom service name and
 * retrieves some DB meta such as the statement
 */
@Deprecated // This should be covered by instrumentation decorators now.
public class DBTypeDecorator extends AbstractDecorator {

  public DBTypeDecorator() {
    super();
    setMatchingTag(Tags.DB_TYPE.getKey());
    setReplacementTag(DDTags.SERVICE_NAME);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {

    // Assign service name
    if (!super.shouldSetTag(context, tag, value)) {
      if ("couchbase".equals(value) || "elasticsearch".equals(value)) {
        // these instrumentation have different behavior.
        return true;
      }
      // Assign span type to DB
      // Special case: Mongo, set to mongodb
      if ("mongo".equals(value)) {
        // Todo: not sure it's used cos already in the agent mongo helper
        context.setSpanType(DDSpanTypes.MONGO);
      } else if ("cassandra".equals(value)) {
        context.setSpanType(DDSpanTypes.CASSANDRA);
      } else if ("memcached".equals(value)) {
        context.setSpanType(DDSpanTypes.MEMCACHED);
      } else {
        context.setSpanType(DDSpanTypes.SQL);
      }
      // Works for: mongo, cassandra, jdbc
      context.setOperationName(String.valueOf(value) + ".query");
    }
    return true;
  }
}
