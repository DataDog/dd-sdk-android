package datadog.appsec.api.blocking;

import java.util.Map;
import androidx.annotation.NonNull;

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
