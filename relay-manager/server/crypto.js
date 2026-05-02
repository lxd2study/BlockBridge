import crypto from "node:crypto";

export function createCryptoBox(secret) {
  const key = crypto.scryptSync(secret, "lan-tunnel-relay-manager", 32);
  return {
    encrypt(value) {
      if (value == null || value === "") {
        return "";
      }
      const iv = crypto.randomBytes(12);
      const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
      const encrypted = Buffer.concat([cipher.update(String(value), "utf8"), cipher.final()]);
      const tag = cipher.getAuthTag();
      return Buffer.concat([iv, tag, encrypted]).toString("base64url");
    },
    decrypt(value) {
      if (!value) {
        return "";
      }
      const data = Buffer.from(value, "base64url");
      const iv = data.subarray(0, 12);
      const tag = data.subarray(12, 28);
      const encrypted = data.subarray(28);
      const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
      decipher.setAuthTag(tag);
      return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString("utf8");
    },
  };
}

export function randomToken(bytes = 32) {
  return crypto.randomBytes(bytes).toString("base64url");
}

export function maskSecret(value) {
  if (!value) {
    return "";
  }
  if (value.length <= 10) {
    return "****";
  }
  return `${value.slice(0, 5)}...${value.slice(-5)}`;
}

export function nowIso() {
  return new Date().toISOString();
}
