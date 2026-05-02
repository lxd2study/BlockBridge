import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const required = [
  "package.json",
  "server/index.js",
  "server/store.js",
  "client/src/App.vue",
  "client/src/styles.css",
  "config/manager.json",
];

for (const file of required) {
  const fullPath = path.join(root, file);
  if (!fs.existsSync(fullPath)) {
    throw new Error(`missing ${file}`);
  }
}

JSON.parse(fs.readFileSync(path.join(root, "config/manager.json"), "utf8"));
console.log("relay-manager smoke check passed");
