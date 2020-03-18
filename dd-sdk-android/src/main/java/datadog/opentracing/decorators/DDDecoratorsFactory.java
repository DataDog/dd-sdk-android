package datadog.opentracing.decorators;

import datadog.trace.api.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Create DDSpanDecorators */
public class DDDecoratorsFactory {
  public static List<AbstractDecorator> createBuiltinDecorators() {

    final List<AbstractDecorator> decorators = new ArrayList<>();

    for (final AbstractDecorator decorator :
        Arrays.asList(
            new AnalyticsSampleRateDecorator(),
            new DBStatementAsResourceName(),
            new DBTypeDecorator(),
            new ErrorFlag(),
            new ForceManualDropDecorator(),
            new ForceManualKeepDecorator(),
            new OperationDecorator(),
            new PeerServiceDecorator(),
            new ResourceNameDecorator(),
            new ServiceNameDecorator(),
            new ServiceNameDecorator("service", false),
            new ServletContextDecorator(),
            new SpanTypeDecorator(),
            new Status404Decorator(),
            new Status5XXDecorator(),
            new URLAsResourceName())) {

      if (Config.get().isDecoratorEnabled(decorator.getClass().getSimpleName())) {
        decorators.add(decorator);
      } else {
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
