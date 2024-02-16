/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.appsec.api.blocking;

import androidx.annotation.NonNull;

import java.util.Map;

public interface BlockingService {
  BlockingService NOOP = new BlockingServiceNoop();

  BlockingDetails shouldBlockUser(@NonNull String userId);

  boolean tryCommitBlockingResponse(
          int statusCode, @NonNull BlockingContentType type, @NonNull Map<String, String> extraHeaders);

  class BlockingServiceNoop implements BlockingService {
    private BlockingServiceNoop() {}

    @Override
    public BlockingDetails shouldBlockUser(@NonNull String userId) {
      return null;
    }

    @Override
    public boolean tryCommitBlockingResponse(
        int statusCode,
        @NonNull BlockingContentType type,
        @NonNull Map<String, String> extraHeaders) {
      return false;
    }
  }
}
