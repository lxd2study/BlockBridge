import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import Database from "better-sqlite3";
import bcrypt from "bcryptjs";
import { nowIso } from "./crypto.js";

const defaultLevels = [
  {
    level: 1,
    code: "level_1",
    displayName: "一级用户",
    description: "适合轻量联机，使用基础节点。",
    keyLimit: 1,
    badgeColor: "#ad6f3b",
    enabled: 1,
  },
  {
    level: 2,
    code: "level_2",
    displayName: "二级用户",
    description: "适合常用联机，开放更多区域节点。",
    keyLimit: 3,
    badgeColor: "#5f7f9d",
    enabled: 1,
  },
  {
    level: 3,
    code: "level_3",
    displayName: "三级用户",
    description: "适合高频联机，可使用全部节点。",
    keyLimit: 10,
    badgeColor: "#c99724",
    enabled: 1,
  },
];

export class Store {
  constructor(config, cryptoBox) {
    this.config = config;
    this.crypto = cryptoBox;
    fs.mkdirSync(path.dirname(config.databasePath), { recursive: true });
    this.db = new Database(config.databasePath);
    this.db.pragma("journal_mode = WAL");
    this.db.pragma("foreign_keys = ON");
    this.migrate();
    this.seed(config);
  }

  migrate() {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS accounts (
        id TEXT PRIMARY KEY,
        username TEXT NOT NULL UNIQUE,
        display_name TEXT NOT NULL,
        password_hash TEXT NOT NULL,
        role TEXT NOT NULL CHECK(role IN ('admin', 'user')),
        level INTEGER,
        enabled INTEGER NOT NULL DEFAULT 1,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        CHECK(role = 'admin' OR level IN (1, 2, 3))
      );

      CREATE TABLE IF NOT EXISTS sessions (
        id TEXT PRIMARY KEY,
        account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
        expires_at TEXT NOT NULL,
        created_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS user_levels (
        level INTEGER PRIMARY KEY CHECK(level IN (1, 2, 3)),
        code TEXT NOT NULL UNIQUE,
        display_name TEXT NOT NULL,
        description TEXT NOT NULL,
        key_limit INTEGER NOT NULL CHECK(key_limit >= 0),
        badge_color TEXT NOT NULL,
        enabled INTEGER NOT NULL DEFAULT 1,
        updated_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS node_groups (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL UNIQUE,
        description TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS tags (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL UNIQUE,
        color TEXT NOT NULL DEFAULT '#4d6b8a',
        created_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS nodes (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        region TEXT NOT NULL DEFAULT 'default',
        public_host TEXT NOT NULL,
        api_base TEXT NOT NULL,
        api_username TEXT NOT NULL,
        api_password_enc TEXT NOT NULL,
        enabled INTEGER NOT NULL DEFAULT 1,
        min_user_level INTEGER NOT NULL DEFAULT 1 CHECK(min_user_level IN (1, 2, 3)),
        group_id TEXT REFERENCES node_groups(id) ON DELETE SET NULL,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS node_tags (
        node_id TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
        tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
        PRIMARY KEY (node_id, tag_id)
      );

      CREATE TABLE IF NOT EXISTS keys (
        id TEXT PRIMARY KEY,
        account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
        node_id TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
        token_name TEXT NOT NULL,
        token_enc TEXT NOT NULL,
        token_masked TEXT NOT NULL,
        enabled INTEGER NOT NULL DEFAULT 1,
        created_at TEXT NOT NULL,
        revoked_at TEXT,
        UNIQUE(node_id, token_name)
      );

      CREATE TABLE IF NOT EXISTS metric_samples (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        node_id TEXT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
        sampled_at TEXT NOT NULL,
        online INTEGER NOT NULL,
        active_tunnels INTEGER NOT NULL DEFAULT 0,
        active_streams INTEGER NOT NULL DEFAULT 0,
        pending_clients INTEGER NOT NULL DEFAULT 0,
        total_public_connections INTEGER NOT NULL DEFAULT 0,
        total_bytes_to_host INTEGER NOT NULL DEFAULT 0,
        total_bytes_to_user INTEGER NOT NULL DEFAULT 0,
        error TEXT
      );

      CREATE TABLE IF NOT EXISTS audit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        actor_id TEXT,
        actor_username TEXT NOT NULL DEFAULT 'system',
        action TEXT NOT NULL,
        target_type TEXT NOT NULL,
        target_id TEXT,
        detail TEXT NOT NULL DEFAULT '{}',
        ip TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL
      );

      CREATE INDEX IF NOT EXISTS idx_sessions_account ON sessions(account_id);
      CREATE INDEX IF NOT EXISTS idx_keys_account ON keys(account_id);
      CREATE INDEX IF NOT EXISTS idx_keys_node ON keys(node_id);
      CREATE INDEX IF NOT EXISTS idx_metrics_node_time ON metric_samples(node_id, sampled_at);
      CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at);
    `);
  }

  seed(config) {
    const now = nowIso();
    const insertLevel = this.db.prepare(`
      INSERT OR IGNORE INTO user_levels
      (level, code, display_name, description, key_limit, badge_color, enabled, updated_at)
      VALUES (@level, @code, @displayName, @description, @keyLimit, @badgeColor, @enabled, @updatedAt)
    `);
    for (const level of defaultLevels) {
      insertLevel.run({ ...level, updatedAt: now });
    }

    const adminCount = this.db.prepare("SELECT COUNT(*) AS count FROM accounts WHERE role = 'admin'").get().count;
    if (adminCount === 0) {
      this.createAccount({
        username: config.initialAdmin.username,
        displayName: config.initialAdmin.displayName || config.initialAdmin.username,
        password: config.initialAdmin.password,
        role: "admin",
        level: null,
        enabled: true,
      });
      this.audit(null, "system.create_initial_admin", "account", config.initialAdmin.username, {});
    }
  }

  audit(actor, action, targetType, targetId, detail = {}, ip = "") {
    this.db.prepare(`
      INSERT INTO audit_logs (actor_id, actor_username, action, target_type, target_id, detail, ip, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(actor?.id || null, actor?.username || "system", action, targetType, targetId || "", JSON.stringify(detail), ip, nowIso());
  }

  createAccount(input) {
    if (!clean(input.username)) {
      throw httpError(400, "username is required");
    }
    if (!input.password) {
      throw httpError(400, "password is required");
    }
    const now = nowIso();
    const id = crypto.randomUUID();
    const role = input.role === "user" ? "user" : "admin";
    const level = role === "user" ? normalizeLevel(input.level || 1) : null;
    const passwordHash = bcrypt.hashSync(input.password, 12);
    this.db.prepare(`
      INSERT INTO accounts (id, username, display_name, password_hash, role, level, enabled, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(id, clean(input.username), clean(input.displayName || input.username), passwordHash, role, level, input.enabled === false ? 0 : 1, now, now);
    return this.getAccount(id);
  }

  listAccounts() {
    return this.db.prepare(`
      SELECT id, username, display_name AS displayName, role, level, enabled, created_at AS createdAt, updated_at AS updatedAt
      FROM accounts
      ORDER BY role, username
    `).all().map(boolify);
  }

  getAccount(id) {
    const row = this.db.prepare(`
      SELECT id, username, display_name AS displayName, password_hash AS passwordHash, role, level, enabled, created_at AS createdAt, updated_at AS updatedAt
      FROM accounts WHERE id = ?
    `).get(id);
    return row ? boolify(row) : null;
  }

  getAccountByUsername(username) {
    const row = this.db.prepare(`
      SELECT id, username, display_name AS displayName, password_hash AS passwordHash, role, level, enabled, created_at AS createdAt, updated_at AS updatedAt
      FROM accounts WHERE username = ?
    `).get(clean(username));
    return row ? boolify(row) : null;
  }

  updateAccount(id, input) {
    const existing = this.getAccount(id);
    if (!existing) {
      throw httpError(404, "account not found");
    }
    const role = input.role === "user" ? "user" : "admin";
    const level = role === "user" ? normalizeLevel(input.level || existing.level || 1) : null;
    const next = {
      username: clean(input.username || existing.username),
      displayName: clean(input.displayName || existing.displayName),
      role,
      level,
      enabled: input.enabled === false ? 0 : 1,
      updatedAt: nowIso(),
    };
    this.db.prepare(`
      UPDATE accounts
      SET username = @username, display_name = @displayName, role = @role, level = @level, enabled = @enabled, updated_at = @updatedAt
      WHERE id = @id
    `).run({ ...next, id });
    if (input.password) {
      this.db.prepare("UPDATE accounts SET password_hash = ?, updated_at = ? WHERE id = ?")
        .run(bcrypt.hashSync(input.password, 12), nowIso(), id);
    }
    return this.getAccount(id);
  }

  deleteAccount(id) {
    const info = this.db.prepare("DELETE FROM accounts WHERE id = ?").run(id);
    if (info.changes === 0) {
      throw httpError(404, "account not found");
    }
  }

  createSession(accountId, sessionDays) {
    const id = crypto.randomUUID();
    const expiresAt = new Date(Date.now() + sessionDays * 86400000).toISOString();
    this.db.prepare("INSERT INTO sessions (id, account_id, expires_at, created_at) VALUES (?, ?, ?, ?)")
      .run(id, accountId, expiresAt, nowIso());
    return { id, expiresAt };
  }

  getSession(id) {
    if (!id) {
      return null;
    }
    const row = this.db.prepare(`
      SELECT s.id, s.expires_at AS expiresAt, a.id AS accountId, a.username, a.display_name AS displayName, a.role, a.level, a.enabled
      FROM sessions s
      JOIN accounts a ON a.id = s.account_id
      WHERE s.id = ? AND s.expires_at > ?
    `).get(id, nowIso());
    return row ? boolify(row) : null;
  }

  deleteSession(id) {
    this.db.prepare("DELETE FROM sessions WHERE id = ?").run(id);
  }

  levels() {
    return this.db.prepare(`
      SELECT level, code, display_name AS displayName, description, key_limit AS keyLimit, badge_color AS badgeColor, enabled, updated_at AS updatedAt
      FROM user_levels ORDER BY level
    `).all().map(boolify);
  }

  updateLevels(levels) {
    const stmt = this.db.prepare(`
      UPDATE user_levels
      SET display_name = @displayName, description = @description, key_limit = @keyLimit,
          badge_color = @badgeColor, enabled = @enabled, updated_at = @updatedAt
      WHERE level = @level
    `);
    const tx = this.db.transaction((items) => {
      for (const item of items) {
        stmt.run({
          level: normalizeLevel(item.level),
          displayName: clean(item.displayName),
          description: clean(item.description),
          keyLimit: Math.max(0, Number(item.keyLimit || 0)),
          badgeColor: clean(item.badgeColor || "#4d6b8a"),
          enabled: item.enabled === false ? 0 : 1,
          updatedAt: nowIso(),
        });
      }
    });
    tx(levels);
    return this.levels();
  }

  listNodes({ includeSecrets = false } = {}) {
    const rows = this.db.prepare(`
      SELECT n.*, g.name AS group_name
      FROM nodes n
      LEFT JOIN node_groups g ON g.id = n.group_id
      ORDER BY n.created_at DESC
    `).all();
    return rows.map((row) => this.publicNode(row, includeSecrets));
  }

  getNode(id, { includeSecrets = false } = {}) {
    const row = this.db.prepare("SELECT * FROM nodes WHERE id = ?").get(id);
    return row ? this.publicNode(row, includeSecrets) : null;
  }

  createNode(input) {
    const now = nowIso();
    const id = clean(input.id || slug(input.name || "node"));
    if (!clean(input.publicHost)) {
      throw httpError(400, "publicHost is required");
    }
    if (!clean(input.apiBase)) {
      throw httpError(400, "apiBase is required");
    }
    if (!clean(input.apiUsername || input.username)) {
      throw httpError(400, "apiUsername is required");
    }
    if (!clean(input.apiPassword || input.password)) {
      throw httpError(400, "apiPassword is required");
    }
    this.db.prepare(`
      INSERT INTO nodes
      (id, name, region, public_host, api_base, api_username, api_password_enc, enabled, min_user_level, group_id, created_at, updated_at)
      VALUES (@id, @name, @region, @publicHost, @apiBase, @apiUsername, @apiPasswordEnc, @enabled, @minUserLevel, @groupId, @createdAt, @updatedAt)
    `).run({
      id,
      name: clean(input.name || id),
      region: clean(input.region || "default"),
      publicHost: clean(input.publicHost),
      apiBase: trimRight(clean(input.apiBase), "/"),
      apiUsername: clean(input.apiUsername || input.username),
      apiPasswordEnc: this.crypto.encrypt(input.apiPassword || input.password || ""),
      enabled: input.enabled === false ? 0 : 1,
      minUserLevel: normalizeLevel(input.minUserLevel || 1),
      groupId: input.groupId || null,
      createdAt: now,
      updatedAt: now,
    });
    this.replaceNodeTags(id, input.tagIds || []);
    return this.getNode(id);
  }

  updateNode(id, input) {
    const existing = this.getNode(id, { includeSecrets: true });
    if (!existing) {
      throw httpError(404, "node not found");
    }
    const apiPasswordEnc = input.apiPassword ? this.crypto.encrypt(input.apiPassword) : existing.apiPasswordEnc;
    this.db.prepare(`
      UPDATE nodes
      SET name = @name, region = @region, public_host = @publicHost, api_base = @apiBase,
          api_username = @apiUsername, api_password_enc = @apiPasswordEnc, enabled = @enabled,
          min_user_level = @minUserLevel, group_id = @groupId, updated_at = @updatedAt
      WHERE id = @id
    `).run({
      id,
      name: clean(input.name || existing.name),
      region: clean(input.region || existing.region),
      publicHost: clean(input.publicHost || existing.publicHost),
      apiBase: trimRight(clean(input.apiBase || existing.apiBase), "/"),
      apiUsername: clean(input.apiUsername || existing.apiUsername),
      apiPasswordEnc,
      enabled: input.enabled === false ? 0 : 1,
      minUserLevel: normalizeLevel(input.minUserLevel || existing.minUserLevel),
      groupId: input.groupId || null,
      updatedAt: nowIso(),
    });
    if (Array.isArray(input.tagIds)) {
      this.replaceNodeTags(id, input.tagIds);
    }
    return this.getNode(id);
  }

  deleteNode(id) {
    const info = this.db.prepare("DELETE FROM nodes WHERE id = ?").run(id);
    if (info.changes === 0) {
      throw httpError(404, "node not found");
    }
  }

  replaceNodeTags(nodeId, tagIds) {
    const tx = this.db.transaction((ids) => {
      this.db.prepare("DELETE FROM node_tags WHERE node_id = ?").run(nodeId);
      const stmt = this.db.prepare("INSERT OR IGNORE INTO node_tags (node_id, tag_id) VALUES (?, ?)");
      for (const tagId of ids) {
        stmt.run(nodeId, tagId);
      }
    });
    tx(tagIds);
  }

  groups() {
    return this.db.prepare("SELECT * FROM node_groups ORDER BY name").all();
  }

  upsertGroup(input) {
    const id = input.id || crypto.randomUUID();
    this.db.prepare(`
      INSERT INTO node_groups (id, name, description, created_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET name = excluded.name, description = excluded.description
    `).run(id, clean(input.name), clean(input.description || ""), nowIso());
    return this.groups().find((item) => item.id === id);
  }

  deleteGroup(id) {
    this.db.prepare("DELETE FROM node_groups WHERE id = ?").run(id);
  }

  tags() {
    return this.db.prepare("SELECT * FROM tags ORDER BY name").all();
  }

  upsertTag(input) {
    const id = input.id || crypto.randomUUID();
    this.db.prepare(`
      INSERT INTO tags (id, name, color, created_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET name = excluded.name, color = excluded.color
    `).run(id, clean(input.name), clean(input.color || "#4d6b8a"), nowIso());
    return this.tags().find((item) => item.id === id);
  }

  deleteTag(id) {
    this.db.prepare("DELETE FROM tags WHERE id = ?").run(id);
  }

  listKeys({ accountId = "", includeToken = false } = {}) {
    const params = [];
    let where = "WHERE k.revoked_at IS NULL";
    if (accountId) {
      where += " AND k.account_id = ?";
      params.push(accountId);
    }
    const rows = this.db.prepare(`
      SELECT k.*, a.username, a.display_name AS display_name, n.name AS node_name, n.public_host AS public_host
      FROM keys k
      JOIN accounts a ON a.id = k.account_id
      JOIN nodes n ON n.id = k.node_id
      ${where}
      ORDER BY k.created_at DESC
    `).all(...params);
    return rows.map((row) => ({
      id: row.id,
      accountId: row.account_id,
      username: row.username,
      displayName: row.display_name,
      nodeId: row.node_id,
      nodeName: row.node_name,
      publicHost: row.public_host,
      tokenName: row.token_name,
      tokenMasked: row.token_masked,
      token: includeToken ? this.crypto.decrypt(row.token_enc) : undefined,
      enabled: Boolean(row.enabled),
      createdAt: row.created_at,
    }));
  }

  activeKeyCount(accountId) {
    return this.db.prepare("SELECT COUNT(*) AS count FROM keys WHERE account_id = ? AND revoked_at IS NULL").get(accountId).count;
  }

  insertKey({ accountId, nodeId, tokenName, token }) {
    const id = crypto.randomUUID();
    this.db.prepare(`
      INSERT INTO keys (id, account_id, node_id, token_name, token_enc, token_masked, enabled, created_at)
      VALUES (?, ?, ?, ?, ?, ?, 1, ?)
    `).run(id, accountId, nodeId, tokenName, this.crypto.encrypt(token), maskToken(token), nowIso());
    return this.listKeys({ includeToken: false }).find((item) => item.id === id);
  }

  getKey(id, { includeToken = false } = {}) {
    return this.listKeys({ includeToken }).find((item) => item.id === id) || null;
  }

  revokeKey(id) {
    const info = this.db.prepare("UPDATE keys SET revoked_at = ?, enabled = 0 WHERE id = ? AND revoked_at IS NULL").run(nowIso(), id);
    if (info.changes === 0) {
      throw httpError(404, "key not found");
    }
  }

  insertMetric(sample) {
    this.db.prepare(`
      INSERT INTO metric_samples
      (node_id, sampled_at, online, active_tunnels, active_streams, pending_clients,
       total_public_connections, total_bytes_to_host, total_bytes_to_user, error)
      VALUES (@nodeId, @sampledAt, @online, @activeTunnels, @activeStreams, @pendingClients,
              @totalPublicConnections, @totalBytesToHost, @totalBytesToUser, @error)
    `).run(sample);
  }

  metrics({ nodeId = "", since }) {
    const params = [since];
    let where = "WHERE sampled_at >= ?";
    if (nodeId) {
      where += " AND node_id = ?";
      params.push(nodeId);
    }
    return this.db.prepare(`
      SELECT node_id AS nodeId, sampled_at AS sampledAt, online, active_tunnels AS activeTunnels,
             active_streams AS activeStreams, pending_clients AS pendingClients,
             total_public_connections AS totalPublicConnections,
             total_bytes_to_host AS totalBytesToHost, total_bytes_to_user AS totalBytesToUser, error
      FROM metric_samples
      ${where}
      ORDER BY sampled_at ASC
      LIMIT 2000
    `).all(...params).map(boolify);
  }

  auditLogs(limit = 200) {
    return this.db.prepare(`
      SELECT id, actor_id AS actorId, actor_username AS actorUsername, action, target_type AS targetType,
             target_id AS targetId, detail, ip, created_at AS createdAt
      FROM audit_logs
      ORDER BY id DESC
      LIMIT ?
    `).all(Math.min(Number(limit) || 200, 1000)).map((row) => ({ ...row, detail: safeJson(row.detail) }));
  }

  publicNode(row, includeSecrets = false) {
    const tagRows = this.db.prepare(`
      SELECT t.id, t.name, t.color FROM tags t
      JOIN node_tags nt ON nt.tag_id = t.id
      WHERE nt.node_id = ?
      ORDER BY t.name
    `).all(row.id);
    return {
      id: row.id,
      name: row.name,
      region: row.region,
      publicHost: row.public_host,
      apiBase: row.api_base,
      apiUsername: row.api_username,
      apiPasswordEnc: includeSecrets ? row.api_password_enc : undefined,
      enabled: Boolean(row.enabled),
      minUserLevel: row.min_user_level,
      groupId: row.group_id,
      groupName: row.group_name || "",
      tags: tagRows,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    };
  }
}

export function httpError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}

export function normalizeLevel(value) {
  const level = Number(value);
  if (![1, 2, 3].includes(level)) {
    throw httpError(400, "level must be 1, 2 or 3");
  }
  return level;
}

function clean(value) {
  return String(value || "").trim();
}

function trimRight(value, suffix) {
  let out = value;
  while (out.endsWith(suffix)) {
    out = out.slice(0, -suffix.length);
  }
  return out;
}

function slug(value) {
  const base = clean(value).toLowerCase().replace(/[^a-z0-9-]+/g, "-").replace(/^-+|-+$/g, "");
  return base || `node-${crypto.randomBytes(3).toString("hex")}`;
}

function boolify(row) {
  const out = { ...row };
  for (const key of ["enabled", "online"]) {
    if (key in out) {
      out[key] = Boolean(out[key]);
    }
  }
  return out;
}

function safeJson(value) {
  try {
    return JSON.parse(value || "{}");
  } catch {
    return {};
  }
}

function maskToken(token) {
  if (!token || token.length <= 10) {
    return "****";
  }
  return `${token.slice(0, 5)}...${token.slice(-5)}`;
}
