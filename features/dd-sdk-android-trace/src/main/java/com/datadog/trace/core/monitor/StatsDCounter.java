package com.datadog.trace.core.monitor;

import static com.datadog.trace.core.monitor.Utils.mergeTags;

import com.datadog.trace.api.StatsDClient;
import com.datadog.trace.monitor.Counter;

public final class StatsDCounter implements Counter {

  private final String name;
  private final String[] tags;
  private final StatsDClient statsd;

  StatsDCounter(String name, StatsDClient statsd) {
    this.name = name;
    this.tags = new String[0];
    this.statsd = statsd;
  }

  public void increment(int delta) {
    statsd.count(name, delta, tags);
  }

  public void incrementErrorCount(String cause, int delta) {
    statsd.count(name, delta, mergeTags(tags, new String[] {"cause:" + cause.replace(' ', '_')}));
  }
}
