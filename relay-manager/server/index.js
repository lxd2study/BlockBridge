import path from "node:path";
import { fileURLToPath } from "node:url";
import express from "express";
import bcrypt from "bcryptjs";
import { readConfig, parseBind } from "./config.js";
import { createCryptoBox, randomToken } from "./crypto.js";
import { Store, httpError, normalizeLevel } from "./store.js";
import { RelayClient, applyPublicHost } from "./relay-client.js";
import { startMetricsSampler } from "./metrics.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");

const config = readConfig();
const cryptoBox = createCryptoBox(config.security.secret);
const store = new Store(config, cryptoBox);
const relay = new RelayClient(store, config.metrics.requestTimeoutMillis);
const app = express();

app.disable("x-powered-by");
app.use(express.json({ limit: "1mb" }));
app.use(attachSession);

registerRoutes(app);

const publicDir = path.join(rootDir, "public");
app.use(express.static(publicDir, { index: false }));
app.get(["/", "/status", "/pricing", "/login", "/admin", "/account"], (_req, res) => {
  res.sendFile(path.join(publicDir, "index.html"));
});

app.use((req, res) => {
  if (req.path.startsWith("/api/")) {
    res.status(404).json({ error: "not found" });
    return;
  }
  res.sendFile(path.join(publicDir, "index.html"));
});

app.use((error, req, res, _next) => {
  const status = error.status || 500;
  if (status >= 500) {
    console.error(error);
  }
  res.status(status).json({ error: error.message || "server error" });
});

const bind = parseBind(config.bind);
const stopSampler = startMetricsSampler(store, relay, config.metrics.sampleIntervalSeconds);
const server = app.listen(bind.port, bind.host, () => {
  console.log(`relay manager listening on http://${bind.host}:${bind.port}`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    stopSampler();
    server.close(() => process.exit(0));
  });
}

function registerRoutes(app) {
  app.post("/api/auth/login", asyncHandler(async (req, res) => {
    const account = store.getAccountByUsername(req.body.username);
    if (!account || !account.enabled || !bcrypt.compareSync(String(req.body.password || ""), account.passwordHash)) {
      throw httpError(401, "用户名或密码错误");
    }
    const session = store.createSession(account.id, config.security.sessionDays);
    setSessionCookie(res, session.id, session.expiresAt);
    store.audit(account, "auth.login", "account", account.id, {}, req.ip);
    res.json({ account: publicAccount(account) });
  }));

  app.post("/api/auth/logout", requireAuth, asyncHandler(async (req, res) => {
    store.deleteSession(req.sessionId);
    clearSessionCookie(res);
    store.audit(req.account, "auth.logout", "account", req.account.id, {}, req.ip);
    res.json({ status: "ok" });
  }));

  app.get("/api/auth/me", asyncHandler(async (req, res) => {
    res.json({ account: req.account ? publicAccount(req.account) : null });
  }));

  app.get("/api/levels", asyncHandler(async (_req, res) => {
    res.json({ levels: store.levels() });
  }));

  app.get("/api/public/status", asyncHandler(async (_req, res) => {
    const nodes = store.listNodes({ includeSecrets: true }).filter((node) => node.enabled);
    const items = await Promise.all(nodes.map(async (node) => {
      const item = {
        id: node.id,
        name: node.name,
        region: node.region,
        publicHost: node.publicHost,
        minUserLevel: node.minUserLevel,
        online: false,
        latencyMillis: null,
        activeTunnels: 0,
        activeStreams: 0,
        error: "",
      };
      const startedAt = Date.now();
      try {
        const status = await relay.health(node).catch(() => relay.status(node));
        item.online = true;
        item.latencyMillis = Date.now() - startedAt;
        item.activeTunnels = Number(status.runtime?.activeTunnels || 0);
        item.activeStreams = Number(status.runtime?.activeStreams || 0);
        item.pendingClients = Number(status.runtime?.pendingClients || 0);
        item.portPoolUsed = Number(status.runtime?.portPoolUsed || 0);
        item.portPoolTotal = Number(status.runtime?.portPoolTotal || 0);
      } catch (error) {
        item.error = error.message;
      }
      return item;
    }));
    res.json({
      checkedAt: new Date().toISOString(),
      totals: {
        nodes: items.length,
        online: items.filter((item) => item.online).length,
        tunnels: items.reduce((sum, item) => sum + item.activeTunnels, 0),
        streams: items.reduce((sum, item) => sum + item.activeStreams, 0),
        pending: items.reduce((sum, item) => sum + item.pendingClients, 0),
      },
      nodes: items,
    });
  }));

  app.get("/api/overview", requireRole("admin"), asyncHandler(async (_req, res) => {
    const nodes = store.listNodes({ includeSecrets: true });
    const items = await Promise.all(nodes.map(async (node) => {
      const item = { record: stripNodeSecret(node), online: false };
      if (!node.enabled) {
        item.error = "disabled";
        return item;
      }
      try {
        item.status = applyPublicHost(node, await relay.status(node));
        item.online = true;
      } catch (error) {
        item.error = error.message;
      }
      return item;
    }));
    res.json({ nodes: items, totals: summarizeOverview(items) });
  }));

  app.get("/api/admin/nodes/:id/health", requireRole("admin"), asyncHandler(async (req, res) => {
    const node = store.getNode(req.params.id, { includeSecrets: true });
    if (!node) {
      throw httpError(404, "node not found");
    }
    const health = await relay.health(node);
    const tunnels = await relay.tunnels(node).catch(() => ({ tunnels: health.runtime?.tunnels || [] }));
    res.json({ health: applyPublicHost(node, { runtime: health.runtime }).runtime, tunnels: tunnels.tunnels || [] });
  }));

  app.get("/api/admin/users", requireRole("admin"), asyncHandler(async (_req, res) => {
    res.json({ users: store.listAccounts() });
  }));
  app.post("/api/admin/users", requireRole("admin"), asyncHandler(async (req, res) => {
    const account = store.createAccount(req.body);
    store.audit(req.account, "account.create", "account", account.id, { username: account.username, role: account.role }, req.ip);
    res.status(201).json({ user: publicAccount(account) });
  }));
  app.put("/api/admin/users/:id", requireRole("admin"), asyncHandler(async (req, res) => {
    const account = store.updateAccount(req.params.id, req.body);
    store.audit(req.account, "account.update", "account", account.id, { username: account.username, role: account.role, level: account.level }, req.ip);
    res.json({ user: publicAccount(account) });
  }));
  app.delete("/api/admin/users/:id", requireRole("admin"), asyncHandler(async (req, res) => {
    if (req.params.id === req.account.id) {
      throw httpError(400, "不能删除当前登录账号");
    }
    const ownedKeys = store.listKeys({ accountId: req.params.id });
    for (const key of ownedKeys) {
      await revokeKey(key.id, req.account, req.ip);
    }
    store.deleteAccount(req.params.id);
    store.audit(req.account, "account.delete", "account", req.params.id, {}, req.ip);
    res.json({ status: "deleted" });
  }));

  app.get("/api/admin/levels", requireRole("admin"), asyncHandler(async (_req, res) => {
    res.json({ levels: store.levels() });
  }));
  app.put("/api/admin/levels", requireRole("admin"), asyncHandler(async (req, res) => {
    const levels = store.updateLevels(req.body.levels || []);
    store.audit(req.account, "levels.update", "level", "all", { levels }, req.ip);
    res.json({ levels });
  }));

  app.get("/api/admin/nodes", requireRole("admin"), asyncHandler(async (_req, res) => {
    res.json({ nodes: store.listNodes(), groups: store.groups(), tags: store.tags() });
  }));
  app.post("/api/admin/nodes", requireRole("admin"), asyncHandler(async (req, res) => {
    const node = store.createNode(req.body);
    store.audit(req.account, "node.create", "node", node.id, { minUserLevel: node.minUserLevel }, req.ip);
    res.status(201).json({ node });
  }));
  app.put("/api/admin/nodes/:id", requireRole("admin"), asyncHandler(async (req, res) => {
    const node = store.updateNode(req.params.id, req.body);
    store.audit(req.account, "node.update", "node", node.id, { minUserLevel: node.minUserLevel, enabled: node.enabled }, req.ip);
    res.json({ node });
  }));
  app.delete("/api/admin/nodes/:id", requireRole("admin"), asyncHandler(async (req, res) => {
    store.deleteNode(req.params.id);
    store.audit(req.account, "node.delete", "node", req.params.id, {}, req.ip);
    res.json({ status: "deleted" });
  }));
  app.post("/api/admin/nodes/bulk", requireRole("admin"), asyncHandler(async (req, res) => {
    const result = bulkNodes(req.body);
    store.audit(req.account, "node.bulk", "node", "bulk", req.body, req.ip);
    res.json(result);
  }));

  app.get("/api/admin/groups", requireRole("admin"), asyncHandler(async (_req, res) => {
    res.json({ groups: store.groups() });
  }));
  app.post("/api/admin/groups", requireRole("admin"), asyncHandler(async (req, res) => {
    const group = store.upsertGroup(req.body);
    store.audit(req.account, "group.upsert", "group", group.id, { name: group.name }, req.ip);
    res.json({ group });
  }));
  app.delete("/api/admin/groups/:id", requireRole("admin"), asyncHandler(async (req, res) => {
    store.deleteGroup(req.params.id);
    store.audit(req.account, "group.delete", "group", req.params.id, {}, req.ip);
    res.json({ status: "deleted" });
  }));

  app.get("/api/admin/tags", requireRole("admin"), asyncHandler(async (_req, res) => {
    res.json({ tags: store.tags() });
  }));
  app.post("/api/admin/tags", requireRole("admin"), asyncHandler(async (req, res) => {
    const tag = store.upsertTag(req.body);
    store.audit(req.account, "tag.upsert", "tag", tag.id, { name: tag.name }, req.ip);
    res.json({ tag });
  }));
  app.delete("/api/admin/tags/:id", requireRole("admin"), asyncHandler(async (req, res) => {
    store.deleteTag(req.params.id);
    store.audit(req.account, "tag.delete", "tag", req.params.id, {}, req.ip);
    res.json({ status: "deleted" });
  }));

  app.get("/api/admin/keys", requireRole("admin"), asyncHandler(async (_req, res) => {
    res.json({ keys: store.listKeys() });
  }));
  app.get("/api/admin/keys/:id/token", requireRole("admin"), asyncHandler(async (req, res) => {
    const key = store.getKey(req.params.id, { includeToken: true });
    if (!key) {
      throw httpError(404, "key not found");
    }
    store.audit(req.account, "key.copy_token", "key", key.id, { nodeId: key.nodeId, tokenName: key.tokenName }, req.ip);
    res.json({ token: key.token });
  }));
  app.get("/api/admin/keys/:id/usage", requireRole("admin"), asyncHandler(async (req, res) => {
    const key = store.getKey(req.params.id);
    if (!key) {
      throw httpError(404, "key not found");
    }
    const node = store.getNode(key.nodeId, { includeSecrets: true });
    if (!node) {
      throw httpError(404, "node not found");
    }
    res.json({ usage: await relay.tokenUsage(node, key.tokenName) });
  }));
  app.post("/api/admin/keys", requireRole("admin"), asyncHandler(async (req, res) => {
    const result = await createKeyForAccount(req.body.accountId, req.body.nodeId);
    store.audit(req.account, "key.create_for_user", "key", result.key.id, { accountId: req.body.accountId, nodeId: req.body.nodeId }, req.ip);
    res.status(201).json(result);
  }));
  app.delete(["/api/admin/keys", "/api/admin/keys/:id"], requireRole("admin"), asyncHandler(async (req, res) => {
    const keyId = req.params.id || req.query.id;
    await revokeKey(keyId, req.account, req.ip);
    res.json({ status: "deleted" });
  }));

  app.get("/api/account/nodes", requireRole("user"), asyncHandler(async (req, res) => {
    const nodes = store.listNodes().filter((node) => node.enabled && node.minUserLevel <= req.account.level);
    res.json({ nodes });
  }));
  app.get("/api/account/keys", requireRole("user"), asyncHandler(async (req, res) => {
    const level = store.levels().find((item) => item.level === req.account.level);
    const keys = store.listKeys({ accountId: req.account.id });
    res.json({
      keys,
      quota: { used: keys.length, limit: level?.keyLimit || 0, exceeded: keys.length > (level?.keyLimit || 0) },
    });
  }));
  app.get("/api/account/keys/:id/token", requireRole("user"), asyncHandler(async (req, res) => {
    const key = store.getKey(req.params.id, { includeToken: true });
    if (!key || key.accountId !== req.account.id) {
      throw httpError(404, "key not found");
    }
    store.audit(req.account, "key.copy_token", "key", key.id, { nodeId: key.nodeId, tokenName: key.tokenName }, req.ip);
    res.json({ token: key.token });
  }));
  app.get("/api/account/keys/:id/usage", requireRole("user"), asyncHandler(async (req, res) => {
    const key = store.getKey(req.params.id);
    if (!key || key.accountId !== req.account.id) {
      throw httpError(404, "key not found");
    }
    const node = store.getNode(key.nodeId, { includeSecrets: true });
    if (!node) {
      throw httpError(404, "node not found");
    }
    res.json({ usage: await relay.tokenUsage(node, key.tokenName) });
  }));
  app.post("/api/account/keys", requireRole("user"), asyncHandler(async (req, res) => {
    const result = await createKeyForAccount(req.account.id, req.body.nodeId);
    store.audit(req.account, "key.create", "key", result.key.id, { nodeId: req.body.nodeId }, req.ip);
    res.status(201).json(result);
  }));
  app.delete("/api/account/keys/:id", requireRole("user"), asyncHandler(async (req, res) => {
    const key = store.getKey(req.params.id);
    if (!key || key.accountId !== req.account.id) {
      throw httpError(404, "key not found");
    }
    await revokeKey(req.params.id, req.account, req.ip);
    res.json({ status: "deleted" });
  }));

  app.get("/api/metrics", requireRole("admin"), asyncHandler(async (req, res) => {
    res.json({ samples: store.metrics({ nodeId: req.query.nodeId || "", since: sinceFromRange(req.query.range || "24h") }) });
  }));
  app.get("/api/audit-logs", requireRole("admin"), asyncHandler(async (req, res) => {
    res.json({ logs: store.auditLogs(req.query.limit) });
  }));
}

async function createKeyForAccount(accountId, nodeId) {
  const account = store.getAccount(accountId);
  if (!account || account.role !== "user" || !account.enabled) {
    throw httpError(400, "有效用户不存在");
  }
  const level = store.levels().find((item) => item.level === account.level && item.enabled);
  if (!level) {
    throw httpError(400, "用户等级未启用");
  }
  if (store.activeKeyCount(account.id) >= level.keyLimit) {
    throw httpError(400, "已达到当前等级可生成 key 数量上限");
  }
  const node = store.getNode(nodeId, { includeSecrets: true });
  if (!node || !node.enabled) {
    throw httpError(400, "节点不可用");
  }
  if (node.minUserLevel > account.level) {
    throw httpError(403, "当前等级不能使用该节点");
  }

  const tokenName = `u-${account.username.replace(/[^a-zA-Z0-9_-]/g, "-")}-${randomToken(5)}`;
  const token = randomToken(32);
  await relay.addToken(node, { name: tokenName, token, enabled: true });
  try {
    const key = store.insertKey({ accountId: account.id, nodeId: node.id, tokenName, token });
    return { key, token };
  } catch (error) {
    await relay.deleteToken(node, tokenName).catch(() => {});
    throw error;
  }
}

async function revokeKey(keyId, actor, ip) {
  if (!keyId) {
    throw httpError(400, "key id is required");
  }
  const key = store.getKey(keyId);
  if (!key) {
    throw httpError(404, "key not found");
  }
  const node = store.getNode(key.nodeId, { includeSecrets: true });
  if (node) {
    await relay.deleteToken(node, key.tokenName);
  }
  store.revokeKey(key.id);
  store.audit(actor, "key.revoke", "key", key.id, { nodeId: key.nodeId, tokenName: key.tokenName }, ip);
}

function bulkNodes(input) {
  const ids = Array.isArray(input.nodeIds) ? input.nodeIds : [];
  const results = [];
  for (const id of ids) {
    const node = store.getNode(id, { includeSecrets: true });
    if (!node) {
      results.push({ id, ok: false, error: "node not found" });
      continue;
    }
    try {
      if (input.action === "enable") {
        store.updateNode(id, { ...node, enabled: true });
      } else if (input.action === "disable") {
        store.updateNode(id, { ...node, enabled: false });
      } else if (input.action === "minLevel") {
        store.updateNode(id, { ...node, minUserLevel: normalizeLevel(input.value) });
      } else if (input.action === "group") {
        store.updateNode(id, { ...node, groupId: input.value || null });
      } else if (input.action === "tagAdd") {
        const tagIds = new Set((node.tags || []).map((tag) => tag.id));
        if (input.value) tagIds.add(input.value);
        store.updateNode(id, { ...node, tagIds: [...tagIds] });
      } else if (input.action === "tagClear") {
        store.updateNode(id, { ...node, tagIds: [] });
      } else if (input.action === "delete") {
        store.deleteNode(id);
      } else {
        throw httpError(400, "unsupported bulk action");
      }
      results.push({ id, ok: true });
    } catch (error) {
      results.push({ id, ok: false, error: error.message });
    }
  }
  return { results };
}

function attachSession(req, _res, next) {
  const sessionId = parseCookies(req.headers.cookie || "").ltm_session || "";
  const session = store.getSession(sessionId);
  req.sessionId = sessionId;
  if (session) {
    req.account = {
      id: session.accountId,
      username: session.username,
      displayName: session.displayName,
      role: session.role,
      level: session.level,
      enabled: session.enabled,
    };
  }
  next();
}

function requireAuth(req, _res, next) {
  if (!req.account || !req.account.enabled) {
    throw httpError(401, "authentication required");
  }
  next();
}

function requireRole(role) {
  return [requireAuth, (req, _res, next) => {
    if (req.account.role !== role) {
      throw httpError(403, "permission denied");
    }
    next();
  }];
}

function setSessionCookie(res, value, expiresAt) {
  res.cookie("ltm_session", value, {
    httpOnly: true,
    sameSite: "lax",
    expires: new Date(expiresAt),
    path: "/",
  });
}

function clearSessionCookie(res) {
  res.clearCookie("ltm_session", { path: "/" });
}

function parseCookies(header) {
  const out = {};
  for (const item of header.split(";")) {
    const index = item.indexOf("=");
    if (index > -1) {
      out[item.slice(0, index).trim()] = decodeURIComponent(item.slice(index + 1).trim());
    }
  }
  return out;
}

function publicAccount(account) {
  return {
    id: account.id,
    username: account.username,
    displayName: account.displayName,
    role: account.role,
    level: account.level,
    enabled: account.enabled,
  };
}

function stripNodeSecret(node) {
  const { apiPasswordEnc, ...publicNode } = node;
  return publicNode;
}

function summarizeOverview(items) {
  const totals = { nodes: items.length, online: 0, tunnels: 0, streams: 0, pending: 0, traffic: 0 };
  for (const item of items) {
    if (!item.online) continue;
    totals.online += 1;
    totals.tunnels += Number(item.status?.runtime?.activeTunnels || 0);
    totals.streams += Number(item.status?.runtime?.activeStreams || 0);
    totals.pending += Number(item.status?.runtime?.pendingClients || 0);
    totals.traffic += Number(item.status?.runtime?.totalBytesToHost || 0) + Number(item.status?.runtime?.totalBytesToUser || 0);
  }
  return totals;
}

function sinceFromRange(range) {
  const map = { "1h": 3600000, "24h": 86400000, "7d": 604800000 };
  return new Date(Date.now() - (map[range] || map["24h"])).toISOString();
}

function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}
