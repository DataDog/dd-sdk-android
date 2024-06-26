package com.datadog.trace.bootstrap.config.provider;

import com.datadog.trace.api.ConfigCollector;
import com.datadog.trace.api.ConfigOrigin;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class ConfigProvider {
  private static final class Singleton {
    private static final ConfigProvider INSTANCE = ConfigProvider.createDefault();
  }

  private static final Logger log = LoggerFactory.getLogger(ConfigProvider.class);

  private final boolean collectConfig;

  private final Source[] sources;

  private ConfigProvider(Source... sources) {
    this(true, sources);
  }

  private ConfigProvider(boolean collectConfig, Source... sources) {
    this.collectConfig = collectConfig;
    this.sources = sources;
  }

  public String getConfigFileStatus() {
    for (Source source : sources) {
      if (source instanceof PropertiesConfigSource) {
        String configFileStatus = ((PropertiesConfigSource) source).getConfigFileStatus();
        if (null != configFileStatus) {
          return configFileStatus;
        }
      }
    }
    return "no config file present";
  }

  public String getString(String key) {
    return getString(key, null);
  }

  public <T extends Enum<T>> T getEnum(String key, Class<T> enumType, T defaultValue) {
    String value = getString(key);
    if (null != value) {
      try {
        return Enum.valueOf(enumType, value);
      } catch (Exception ignoreAndUseDefault) {
        log.debug("failed to parse {} for {}, defaulting to {}", value, key, defaultValue);
      }
    }
    if (collectConfig) {
      ConfigCollector.get().put(key, String.valueOf(defaultValue), ConfigOrigin.DEFAULT);
    }
    return defaultValue;
  }

  public String getString(String key, String defaultValue, String... aliases) {
    for (Source source : sources) {
      String value = source.get(key, aliases);
      if (value != null) {
        if (collectConfig) {
          ConfigCollector.get().put(key, value, source.origin());
        }
        return value;
      }
    }
    if (collectConfig && defaultValue != null) {
      ConfigCollector.get().put(key, defaultValue, ConfigOrigin.DEFAULT);
    }
    return defaultValue;
  }

  /**
   * Like {@link #getString(String, String, String...)} but falls back to next source if a value is
   * an empty or blank string.
   */
  public String getStringNotEmpty(String key, String defaultValue, String... aliases) {
    for (Source source : sources) {
      String value = source.get(key, aliases);
      if (value != null && !value.trim().isEmpty()) {
        if (collectConfig) {
          ConfigCollector.get().put(key, value, source.origin());
        }
        return value;
      }
    }
    if (collectConfig && defaultValue != null) {
      ConfigCollector.get().put(key, defaultValue, ConfigOrigin.DEFAULT);
    }
    return defaultValue;
  }

  public String getStringExcludingSource(
      String key,
      String defaultValue,
      Class<? extends Source> excludedSource,
      String... aliases) {
    for (Source source : sources) {
      if (excludedSource.isAssignableFrom(source.getClass())) {
        continue;
      }

      String value = source.get(key, aliases);
      if (value != null) {
        if (collectConfig) {
          ConfigCollector.get().put(key, value, source.origin());
        }
        return value;
      }
    }
    if (collectConfig && defaultValue != null) {
      ConfigCollector.get().put(key, defaultValue, ConfigOrigin.DEFAULT);
    }
    return defaultValue;
  }

  public boolean isSet(String key) {
    String value = getString(key);
    return value != null && !value.isEmpty();
  }

  public Boolean getBoolean(String key) {
    return get(key, null, Boolean.class);
  }

  public Boolean getBoolean(String key, String... aliases) {
    return get(key, null, Boolean.class, aliases);
  }

  public boolean getBoolean(String key, boolean defaultValue, String... aliases) {
    return get(key, defaultValue, Boolean.class, aliases);
  }

  public Integer getInteger(String key) {
    return get(key, null, Integer.class);
  }

  public Integer getInteger(String key, String... aliases) {
    return get(key, null, Integer.class, aliases);
  }

  public int getInteger(String key, int defaultValue, String... aliases) {
    return get(key, defaultValue, Integer.class, aliases);
  }

  public Long getLong(String key) {
    return get(key, null, Long.class);
  }

  public Long getLong(String key, String... aliases) {
    return get(key, null, Long.class, aliases);
  }

  public long getLong(String key, long defaultValue, String... aliases) {
    return get(key, defaultValue, Long.class, aliases);
  }

  public Float getFloat(String key, String... aliases) {
    return get(key, null, Float.class, aliases);
  }

  public float getFloat(String key, float defaultValue) {
    return get(key, defaultValue, Float.class);
  }

  public Double getDouble(String key) {
    return get(key, null, Double.class);
  }

  public double getDouble(String key, double defaultValue) {
    return get(key, defaultValue, Double.class);
  }

  private <T> T get(String key, T defaultValue, Class<T> type, String... aliases) {
    for (Source source : sources) {
      try {
        String sourceValue = source.get(key, aliases);
        T value = ConfigConverter.valueOf(sourceValue, type);
        if (value != null) {
          if (collectConfig) {
            ConfigCollector.get().put(key, sourceValue, source.origin());
          }
          return value;
        }
      } catch (NumberFormatException ex) {
        // continue
      }
    }
    if (collectConfig && defaultValue != null) {
      ConfigCollector.get().put(key, defaultValue, ConfigOrigin.DEFAULT);
    }
    return defaultValue;
  }

  public List<String> getList(String key) {
    return ConfigConverter.parseList(getString(key));
  }

  public List<String> getList(String key, List<String> defaultValue) {
    String list = getString(key);
    if (null == list) {
      if (collectConfig && defaultValue != null) {
        ConfigCollector.get().put(key, String.join(",", defaultValue), ConfigOrigin.DEFAULT);
      }
      return defaultValue;
    } else {
      return ConfigConverter.parseList(getString(key));
    }
  }

  public Set<String> getSet(String key, Set<String> defaultValue) {
    String list = getString(key);
    if (null == list) {
      if (collectConfig && defaultValue != null) {
        String defaultValueStr = String.join(",", defaultValue);
        ConfigCollector.get().put(key, defaultValueStr, ConfigOrigin.DEFAULT);
      }
      return defaultValue;
    } else {
      return new HashSet(ConfigConverter.parseList(getString(key)));
    }
  }

  public List<String> getSpacedList(String key) {
    return ConfigConverter.parseList(getString(key), " ");
  }

  public Map<String, String> getMergedMap(String key) {
    Map<String, String> merged = new HashMap<>();
    ConfigOrigin origin = ConfigOrigin.DEFAULT;
    // System properties take precedence over env
    // prior art:
    // https://docs.spring.io/spring-boot/docs/1.5.6.RELEASE/reference/html/boot-features-external-config.html
    // We reverse iterate to allow overrides
    for (int i = sources.length - 1; 0 <= i; i--) {
      String value = sources[i].get(key);
      Map<String, String> parsedMap = ConfigConverter.parseMap(value, key);
      if (!parsedMap.isEmpty()) {
        origin = sources[i].origin();
      }
      merged.putAll(parsedMap);
    }
    collectMapSetting(key, merged, origin);
    return merged;
  }

  public Map<String, String> getOrderedMap(String key) {
    LinkedHashMap<String, String> merged = new LinkedHashMap<>();
    ConfigOrigin origin = ConfigOrigin.DEFAULT;
    // System properties take precedence over env
    // prior art:
    // https://docs.spring.io/spring-boot/docs/1.5.6.RELEASE/reference/html/boot-features-external-config.html
    // We reverse iterate to allow overrides
    for (int i = sources.length - 1; 0 <= i; i--) {
      String value = sources[i].get(key);
      Map<String, String> parsedMap = ConfigConverter.parseOrderedMap(value, key);
      if (!parsedMap.isEmpty()) {
        origin = sources[i].origin();
      }
      merged.putAll(parsedMap);
    }
    collectMapSetting(key, merged, origin);
    return merged;
  }

  public Map<String, String> getMergedMapWithOptionalMappings(
      String defaultPrefix, boolean lowercaseKeys, String... keys) {
    Map<String, String> merged = new HashMap<>();
    ConfigOrigin origin = ConfigOrigin.DEFAULT;
    // System properties take precedence over env
    // prior art:
    // https://docs.spring.io/spring-boot/docs/1.5.6.RELEASE/reference/html/boot-features-external-config.html
    // We reverse iterate to allow overrides
    for (String key : keys) {
      for (int i = sources.length - 1; 0 <= i; i--) {
        String value = sources[i].get(key);
        Map<String, String> parsedMap =
            ConfigConverter.parseMapWithOptionalMappings(value, key, defaultPrefix, lowercaseKeys);
        if (!parsedMap.isEmpty()) {
          origin = sources[i].origin();
        }
        merged.putAll(parsedMap);
      }
      collectMapSetting(key, merged, origin);
    }
    return merged;
  }

  public BitSet getIntegerRange(final String key, final BitSet defaultValue) {
    final String value = getString(key);
    try {
      if (value != null) {
        return ConfigConverter.parseIntegerRangeSet(value, key);
      }
    } catch (final NumberFormatException e) {
      log.warn("Invalid configuration for {}", key, e);
    }
    if (collectConfig) {
      String defaultValueStr = ConfigConverter.renderIntegerRange(defaultValue);
      ConfigCollector.get().put(key, defaultValueStr, ConfigOrigin.DEFAULT);
    }
    return defaultValue;
  }

  public boolean isEnabled(
      final Iterable<String> settingNames,
      final String settingPrefix,
      final String settingSuffix,
      final boolean defaultEnabled) {
    // If default is enabled, we want to disable individually.
    // If default is disabled, we want to enable individually.
    boolean anyEnabled = defaultEnabled;
    for (final String name : settingNames) {
      final String configKey = settingPrefix + name + settingSuffix;
      final String fullKey = configKey.startsWith("trace.") ? configKey : "trace." + configKey;
      final boolean configEnabled = getBoolean(fullKey, defaultEnabled, configKey);
      if (defaultEnabled) {
        anyEnabled &= configEnabled;
      } else {
        anyEnabled |= configEnabled;
      }
    }
    return anyEnabled;
  }

  public static ConfigProvider getInstance() {
    return Singleton.INSTANCE;
  }

  public static ConfigProvider createDefault() {
      return new ConfigProvider();
  }

  public static ConfigProvider withPropertiesOverride(Properties properties) {
    PropertiesConfigSource providedConfigSource = new PropertiesConfigSource(properties, false);
    return new ConfigProvider(providedConfigSource);
  }

  private void collectMapSetting(String key, Map<String, String> merged, ConfigOrigin origin) {
    if (!collectConfig || merged.isEmpty()) {
      return;
    }
    StringBuilder mergedValue = new StringBuilder();
    for (Map.Entry<String, String> entry : merged.entrySet()) {
      if (mergedValue.length() > 0) {
        mergedValue.append(',');
      }
      mergedValue.append(entry.getKey());
      mergedValue.append(':');
      mergedValue.append(entry.getValue());
    }
    ConfigCollector.get().put(key, mergedValue.toString(), origin);
  }

  public abstract static class Source {
    public final String get(String key, String... aliases) {
      String value = get(key);
      if (value != null) {
        return value;
      }
      for (String alias : aliases) {
        value = get(alias);
        if (value != null) {
          return value;
        }
      }
      return null;
    }

    protected abstract String get(String key);

    public abstract ConfigOrigin origin();
  }
}
