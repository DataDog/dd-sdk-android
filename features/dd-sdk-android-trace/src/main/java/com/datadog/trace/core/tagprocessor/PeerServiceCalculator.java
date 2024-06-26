package com.datadog.trace.core.tagprocessor;

import androidx.annotation.NonNull;

import com.datadog.trace.api.Config;
import com.datadog.trace.api.DDTags;
import com.datadog.trace.api.naming.NamingSchema;
import com.datadog.trace.api.naming.SpanNaming;
import com.datadog.trace.bootstrap.instrumentation.api.Tags;

import java.util.Map;

public class PeerServiceCalculator implements TagsPostProcessor {
  private final NamingSchema.ForPeerService peerServiceNaming;

  private final Map<String, String> peerServiceMapping;

  private final boolean canRemap;

  public PeerServiceCalculator() {
    this(SpanNaming.instance().namingSchema().peerService(), Config.get().getPeerServiceMapping());
  }

  // Visible for testing
  PeerServiceCalculator(
      @NonNull final NamingSchema.ForPeerService peerServiceNaming,
      @NonNull final Map<String, String> peerServiceMapping) {
    this.peerServiceNaming = peerServiceNaming;
    this.peerServiceMapping = peerServiceMapping;
    this.canRemap = !peerServiceMapping.isEmpty();
  }

  @Override
  public Map<String, Object> processTags(Map<String, Object> unsafeTags) {
    Object peerService = unsafeTags.get(Tags.PEER_SERVICE);
    // the user set it
    if (peerService != null) {
      if (canRemap) {
        return remapPeerService(unsafeTags, peerService);
      }
    } else if (peerServiceNaming.supports()) {
      // calculate the defaults (if any)
      peerServiceNaming.tags(unsafeTags);
      // only remap if the mapping is not empty (saves one get)
      return remapPeerService(unsafeTags, canRemap ? unsafeTags.get(Tags.PEER_SERVICE) : null);
    }
    // we have no peer.service and we do not compute defaults. Leave the map untouched
    return unsafeTags;
  }

  private Map<String, Object> remapPeerService(Map<String, Object> unsafeTags, Object value) {
    if (value != null) {
      String mapped = peerServiceMapping.get(value);
      if (mapped != null) {
        unsafeTags.put(Tags.PEER_SERVICE, mapped);
        unsafeTags.put(DDTags.PEER_SERVICE_REMAPPED_FROM, value);
      }
    }
    return unsafeTags;
  }
}
