import { nowIso } from "./crypto.js";

export function startMetricsSampler(store, relayClient, intervalSeconds) {
  const sample = async () => {
    const nodes = store.listNodes({ includeSecrets: true }).filter((node) => node.enabled);
    await Promise.all(nodes.map(async (node) => {
      try {
        const status = await relayClient.status(node);
        const runtime = status.runtime || {};
        store.insertMetric({
          nodeId: node.id,
          sampledAt: nowIso(),
          online: 1,
          activeTunnels: Number(runtime.activeTunnels || 0),
          activeStreams: Number(runtime.activeStreams || 0),
          pendingClients: Number(runtime.pendingClients || 0),
          totalPublicConnections: Number(runtime.totalPublicConnections || 0),
          totalBytesToHost: Number(runtime.totalBytesToHost || 0),
          totalBytesToUser: Number(runtime.totalBytesToUser || 0),
          error: "",
        });
      } catch (error) {
        store.insertMetric({
          nodeId: node.id,
          sampledAt: nowIso(),
          online: 0,
          activeTunnels: 0,
          activeStreams: 0,
          pendingClients: 0,
          totalPublicConnections: 0,
          totalBytesToHost: 0,
          totalBytesToUser: 0,
          error: error.message,
        });
      }
    }));
  };

  const timer = setInterval(() => {
    sample().catch((error) => console.error("metrics sampler failed:", error));
  }, intervalSeconds * 1000);
  setTimeout(() => sample().catch((error) => console.error("metrics sampler failed:", error)), 3000);
  return () => clearInterval(timer);
}
