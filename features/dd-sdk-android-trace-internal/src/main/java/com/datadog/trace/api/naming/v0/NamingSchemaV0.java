package com.datadog.trace.api.naming.v0;

import com.datadog.trace.api.Config;
import com.datadog.trace.api.naming.NamingSchema;
import com.datadog.trace.api.naming.v1.PeerServiceNamingV1;

public class NamingSchemaV0 implements NamingSchema {

  private final boolean allowInferredServices =
      !Config.get().isRemoveIntegrationServiceNamesEnabled();
  private final ForCache cacheNaming = new CacheNamingV0(allowInferredServices);
  private final ForClient clientNaming = new ClientNamingV0();
  private final ForCloud cloudNaming = new CloudNamingV0(allowInferredServices);
  private final ForDatabase databaseNaming =
      new DatabaseNamingV0(allowInferredServices);
  private final ForMessaging messagingNaming =
      new MessagingNamingV0(allowInferredServices);
  private final ForPeerService peerServiceNaming =
      Config.get().isPeerServiceDefaultsEnabled()
          ? new PeerServiceNamingV1(Config.get().getPeerServiceComponentOverrides())
          : new PeerServiceNamingV0();
  private final ForServer serverNaming = new ServerNamingV0();

  @Override
  public ForCache cache() {
    return cacheNaming;
  }

  @Override
  public ForClient client() {
    return clientNaming;
  }

  @Override
  public ForCloud cloud() {
    return cloudNaming;
  }

  @Override
  public ForDatabase database() {
    return databaseNaming;
  }

  @Override
  public ForMessaging messaging() {
    return messagingNaming;
  }

  @Override
  public ForServer server() {
    return serverNaming;
  }

  @Override
  public ForPeerService peerService() {
    return peerServiceNaming;
  }

  @Override
  public boolean allowInferredServices() {
    return allowInferredServices;
  }
}
