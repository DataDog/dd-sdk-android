package com.datadog.trace.core.tagprocessor;

import androidx.annotation.NonNull;

import com.datadog.trace.core.DDSpanContext;

import java.util.Map;
import java.util.Objects;

public class PostProcessorChain implements TagsPostProcessor {
  private final TagsPostProcessor[] chain;

  public PostProcessorChain(@NonNull final TagsPostProcessor... processors) {
    chain = Objects.requireNonNull(processors);
  }

  @Override
  public Map<String, Object> processTags(Map<String, Object> unsafeTags) {
    return processTagsWithContext(unsafeTags, null);
  }

  @Override
  public Map<String, Object> processTagsWithContext(
      Map<String, Object> unsafeTags, DDSpanContext spanContext) {
    Map<String, Object> currentTags = unsafeTags;
    for (final TagsPostProcessor tagsPostProcessor : chain) {
      currentTags = tagsPostProcessor.processTagsWithContext(currentTags, spanContext);
    }
    return currentTags;
  }
}
