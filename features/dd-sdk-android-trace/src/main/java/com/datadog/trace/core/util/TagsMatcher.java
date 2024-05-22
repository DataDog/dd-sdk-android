package com.datadog.trace.core.util;

import com.datadog.trace.core.CoreSpan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class TagsMatcher {

  public static TagsMatcher create(Map<String, String> tags) {
      // this method does not have to support concurrency so it is safe just to use an iterator
      final Set<Map.Entry<String, String>> entries = tags.entrySet();
      final Map<String, Matcher>  matchers = new HashMap<>();
      for(final Map.Entry<String, String> entry : entries) {
          final String tagKey = entry.getKey();
          final String tagValue = entry.getValue();
          if (Matchers.isExact(tagValue)) {
              matchers.put(tagKey,new Matchers.ExactMatcher(tagValue));
          } else {
              Pattern pattern = GlobPattern.globToRegexPattern(tagValue);
              matchers.put(tagKey, new Matchers.PatternMatcher(pattern));
          }
      }
    return new TagsMatcher(matchers);
  }

  private final Map<String, Matcher> matchers;

  public TagsMatcher(Map<String, Matcher> matchers) {
    this.matchers = matchers;
  }

  public <T extends CoreSpan<T>> boolean matches(T span) {
    // we will do a copy just in case
    final Set<Map.Entry<String, Matcher>> copyEntrySet = new HashSet<>(matchers.entrySet());
    for(final Map.Entry<String, Matcher> entry : copyEntrySet) {
        final String tagValue = span.getTag(entry.getKey());
        if (tagValue == null || !entry.getValue().matches(tagValue)) {
            return false;
        }
    }
    return true;
  }
}
