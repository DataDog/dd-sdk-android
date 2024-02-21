package datadog.trace.bootstrap.instrumentation.api;

import java.net.URI;
import java.util.function.Function;

public abstract class URIDataAdapterBase implements URIDataAdapter {
  /** Unique instance that signifies an uninitialized field, to allow for == comparison */
  protected static final String UNINITIALIZED = new String("uninitialized");

  private String raw = supportsRaw() ? UNINITIALIZED : null;

  @Override
  public boolean hasPlusEncodedSpaces() {
    return false;
  }

  @Override
  public String raw() {
    String raw = this.raw;
    if (raw == UNINITIALIZED) {
      String p = rawPath();
      String q = rawQuery();
      StringBuilder builder = new StringBuilder();
      if (null != p && !p.isEmpty()) {
        builder.append(p);
      }
      if (null != q && !q.isEmpty()) {
        builder.append('?');
        builder.append(q);
      }
      this.raw = raw = builder.toString();
    }
    return raw;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  public static URIDataAdapter fromURI(String uri, Function<URI, URIDataAdapter> mapper) {
    final URI parsed = URIUtils.safeParse(uri);
    if (parsed != null) {
      return mapper.apply(parsed);
    }
    return new UnparseableURIDataAdapter(uri);
  }
}