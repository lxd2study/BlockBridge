import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const serverDir = path.dirname(fileURLToPath(import.meta.url));

const defaultConfig = {
  bind: "0.0.0.0:8090",
  databasePath: "data/manager.sqlite",
  security: {
    secret: "change-this-to-a-long-random-secret-before-production",
    sessionDays: 7,
  },
  initialAdmin: {
    username: "admin",
    password: "change-this-manager-password",
    displayName: "系统管理员",
  },
  metrics: {
    sampleIntervalSeconds: 60,
    requestTimeoutMillis: 6000,
  },
};

export function readConfig(argv = process.argv) {
  const explicitPath = readFlag(argv, "--config");
  const configPath = resolveConfigPath(explicitPath || "config/manager.json");
  const raw = JSON.parse(fs.readFileSync(configPath, "utf8"));
  const cfg = mergeConfig(defaultConfig, raw);

  cfg.configPath = configPath;
  cfg.rootDir = path.resolve(serverDir, "..");
  cfg.databasePath = resolveMaybeRelative(cfg.databasePath, cfg.rootDir);
  cfg.security.sessionDays = Number(cfg.security.sessionDays || 7);
  cfg.metrics.sampleIntervalSeconds = Math.max(15, Number(cfg.metrics.sampleIntervalSeconds || 60));
  cfg.metrics.requestTimeoutMillis = Math.max(1000, Number(cfg.metrics.requestTimeoutMillis || 6000));

  if (!cfg.security.secret || cfg.security.secret.length < 24) {
    throw new Error("security.secret must be at least 24 characters");
  }
  if (!cfg.initialAdmin.username || !cfg.initialAdmin.password) {
    throw new Error("initialAdmin.username and initialAdmin.password are required");
  }
  return cfg;
}

export function parseBind(bind) {
  const raw = String(bind || defaultConfig.bind);
  const index = raw.lastIndexOf(":");
  if (index <= 0) {
    return { host: "0.0.0.0", port: Number(raw) || 8090 };
  }
  return {
    host: raw.slice(0, index),
    port: Number(raw.slice(index + 1)) || 8090,
  };
}

function readFlag(argv, name) {
  const index = argv.indexOf(name);
  if (index >= 0 && argv[index + 1]) {
    return argv[index + 1];
  }
  const prefix = `${name}=`;
  const item = argv.find((value) => value.startsWith(prefix));
  return item ? item.slice(prefix.length) : "";
}

function resolveConfigPath(input) {
  const candidates = [];
  const add = (value) => candidates.push(path.resolve(value));
  if (path.isAbsolute(input)) {
    add(input);
  } else {
    add(input);
    add(path.join(serverDir, "..", input));
    add(path.join(process.cwd(), "relay-manager", input));
  }
  for (const candidate of [...new Set(candidates)]) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  throw new Error(`config file not found: ${input}`);
}

function resolveMaybeRelative(value, baseDir) {
  return path.isAbsolute(value) ? value : path.resolve(baseDir, value);
}

function mergeConfig(base, value) {
  const out = { ...base, ...value };
  out.security = { ...base.security, ...(value.security || {}) };
  out.initialAdmin = { ...base.initialAdmin, ...(value.initialAdmin || {}) };
  out.metrics = { ...base.metrics, ...(value.metrics || {}) };
  return out;
}
