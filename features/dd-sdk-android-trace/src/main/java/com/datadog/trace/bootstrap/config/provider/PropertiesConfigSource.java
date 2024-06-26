package com.datadog.trace.bootstrap.config.provider;

import static com.datadog.trace.util.Strings.propertyNameToSystemPropertyName;

import com.datadog.trace.api.ConfigOrigin;

import java.util.Properties;

final class PropertiesConfigSource extends ConfigProvider.Source {
  // start key with underscore, so it isn't visible using the public 'get' method
  static final String CONFIG_FILE_STATUS = "_dd.config.file.status";

  private final Properties props;
  private final boolean useSystemPropertyFormat;

  public PropertiesConfigSource(Properties props, boolean useSystemPropertyFormat) {
    assert props != null;
    this.props = props;
    this.useSystemPropertyFormat = useSystemPropertyFormat;
  }

  public String getConfigFileStatus() {
    return props.getProperty(CONFIG_FILE_STATUS);
  }

  @Override
  protected String get(String key) {
    return props.getProperty(useSystemPropertyFormat ? propertyNameToSystemPropertyName(key) : key);
  }

  @Override
  public ConfigOrigin origin() {
    return ConfigOrigin.JVM_PROP;
  }
}
