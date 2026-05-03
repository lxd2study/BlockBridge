export class RelayClient {
  constructor(store, timeoutMillis = 6000) {
    this.store = store;
    this.timeoutMillis = timeoutMillis;
  }

  async status(node) {
    return this.request(node, "GET", "/api/status");
  }

  async health(node) {
    return this.request(node, "GET", "/api/health");
  }

  async tunnels(node) {
    return this.request(node, "GET", "/api/tunnels");
  }

  async tokenUsage(node, name) {
    return this.request(node, "GET", `/api/tokens/${encodeURIComponent(name)}/usage`);
  }

  async addToken(node, token) {
    return this.request(node, "POST", "/api/tokens", token);
  }

  async deleteToken(node, name) {
    return this.request(node, "DELETE", `/api/tokens/${encodeURIComponent(name)}`);
  }

  async closeTunnel(node, name) {
    return this.request(node, "POST", `/api/tunnels/${encodeURIComponent(name)}/close`);
  }

  async request(node, method, path, body) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMillis);
    try {
      const password = node.apiPasswordEnc ? this.store.crypto.decrypt(node.apiPasswordEnc) : node.apiPassword || "";
      const auth = Buffer.from(`${node.apiUsername}:${password}`).toString("base64");
      const response = await fetch(`${node.apiBase}${path}`, {
        method,
        signal: controller.signal,
        headers: {
          Accept: "application/json",
          Authorization: `Basic ${auth}`,
          ...(body ? { "Content-Type": "application/json" } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
      });
      const text = await response.text();
      if (!response.ok) {
        throw new Error(text.trim() || response.statusText);
      }
      return text ? JSON.parse(text) : {};
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function applyPublicHost(node, status) {
  if (!status?.runtime?.tunnels || !node.publicHost) {
    return status;
  }
  for (const tunnel of status.runtime.tunnels) {
    if (!tunnel.publicAddress && tunnel.publicPort) {
      tunnel.publicAddress = `${node.publicHost}:${tunnel.publicPort}`;
    }
  }
  return status;
}
