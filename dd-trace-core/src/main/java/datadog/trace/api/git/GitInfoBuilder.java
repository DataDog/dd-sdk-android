package datadog.trace.api.git;

import androidx.annotation.Nullable;

public interface GitInfoBuilder {
  GitInfo build(@Nullable String repositoryPath);

  int order();
}
