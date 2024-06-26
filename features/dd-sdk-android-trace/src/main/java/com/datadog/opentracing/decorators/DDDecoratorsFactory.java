/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.decorators;

import com.datadog.legacy.trace.api.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Create DDSpanDecorators */
public class DDDecoratorsFactory {
  public static List<AbstractDecorator> createBuiltinDecorators() {

    final List<AbstractDecorator> decorators = new ArrayList<>();

    for (final AbstractDecorator decorator :
        Arrays.asList(
            new ForceManualDropDecorator(),
            new ForceManualKeepDecorator(),
            new PeerServiceDecorator(),
            new ServiceNameDecorator(),
            new ServiceNameDecorator("service", false))) {

      if (Config.get().isRuleEnabled(decorator.getClass().getSimpleName())) {
        decorators.add(decorator);
      }
    }

    // SplitByTags purposely does not check for ServiceNameDecorator being enabled
    // This allows for ServiceNameDecorator to be disabled above while keeping SplitByTags
    // SplitByTags can be disable by removing SplitByTags config
    for (final String splitByTag : Config.get().getSplitByTags()) {
      decorators.add(new ServiceNameDecorator(splitByTag, true));
    }

    return decorators;
  }
}
