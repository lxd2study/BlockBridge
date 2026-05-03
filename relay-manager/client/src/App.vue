<template>
  <div class="app-shell">
    <header class="topbar">
      <button class="brand" type="button" @click="go('/')">
        <span class="brand-mark">BB</span>
        <span>
          <strong>BlockBridge</strong>
          <small>Relay Manager</small>
        </span>
      </button>
      <nav>
        <button type="button" @click="go('/')">首页</button>
        <button type="button" @click="go('/status')">服务器状态</button>
        <button type="button" @click="go('/pricing')">赞助</button>
        <button v-if="!me" type="button" @click="go('/login')">登录</button>
        <button v-if="me?.role === 'admin'" type="button" @click="go('/admin')">管理后台</button>
        <button v-if="me?.role === 'user'" type="button" @click="go('/account')">用户中心</button>
        <button v-if="me" type="button" @click="logout">退出</button>
      </nav>
    </header>

    <main v-if="page === 'home'" class="landing">
      <section class="hero-band">
        <div class="hero-copy">
          <p class="eyebrow">Minecraft 多人联机中转</p>
          <h1>打开局域网，朋友直接连公网地址进服</h1>
          <p class="hero-text">不用折腾路由器端口映射。主机玩家在游戏里开启 LAN，配置一次中转地址和 Key，系统自动把本地世界发布到公网节点，朋友复制地址就能加入。</p>
          <div class="hero-actions">
            <button type="button" class="primary" @click="go('/login')">开始配置</button>
            <button type="button" @click="go('/status')">查看服务器状态</button>
          </div>
          <div class="setup-steps">
            <article><strong>1</strong><span>安装 Mod</span></article>
            <article><strong>2</strong><span>填写节点和 Key</span></article>
            <article><strong>3</strong><span>开启 LAN 分享地址</span></article>
          </div>
        </div>
        <div class="game-visual" aria-hidden="true">
          <div class="minecraft-window">
            <div class="window-bar"><span></span><span></span><span></span></div>
            <div class="world-preview">
              <div class="sun"></div>
              <div class="hill h1"></div>
              <div class="hill h2"></div>
              <div class="player p1"></div>
              <div class="player p2"></div>
              <div class="portal-card">公网分享<br><strong>play.example.com:25565</strong></div>
            </div>
          </div>
          <div class="relay-pill">LAN -> Relay -> Friends</div>
        </div>
      </section>
      <section class="feature-strip multiplayer-strip">
        <article><strong>多人游戏优先</strong><span>围绕 Minecraft 开 LAN 后的真实流程设计，主机和朋友都少填配置。</span></article>
        <article><strong>配置更省事</strong><span>管理员分配节点等级，用户只需选择可用节点生成 Key。</span></article>
        <article><strong>状态可见</strong><span>公开状态页能看到节点在线、延迟、连接数和当前隧道。</span></article>
      </section>
    </main>

    <main v-else-if="page === 'status'" class="status-page">
      <section class="section-head status-head">
        <div>
          <p class="eyebrow">Relay Status</p>
          <h1>服务器监测</h1>
          <p>公开查看中转节点在线情况、响应延迟和当前联机负载，方便玩家选择稳定节点。</p>
        </div>
        <button type="button" class="primary" @click="loadPublicStatus">刷新状态</button>
      </section>
      <section class="metrics-row public-status-metrics">
        <article><span>节点总数</span><strong>{{ publicStatus.totals?.nodes || 0 }}</strong></article>
        <article><span>在线节点</span><strong>{{ publicStatus.totals?.online || 0 }}</strong></article>
        <article><span>在线隧道</span><strong>{{ publicStatus.totals?.tunnels || 0 }}</strong></article>
        <article><span>实时连接</span><strong>{{ publicStatus.totals?.streams || 0 }}</strong></article>
        <article><span>等待连接</span><strong>{{ publicStatus.totals?.pending || 0 }}</strong></article>
      </section>
      <section class="status-grid">
        <article v-for="node in publicStatus.nodes" :key="node.id" class="status-card">
          <header>
            <div>
              <p class="eyebrow">{{ node.region }}</p>
              <h2>{{ node.name }}</h2>
            </div>
            <span :class="['state', node.online ? 'on' : 'off']">{{ node.online ? '在线' : '离线' }}</span>
          </header>
          <div class="status-line"><span>公网地址</span><strong>{{ node.publicHost }}</strong></div>
          <div class="status-line"><span>响应延迟</span><strong>{{ node.online ? `${node.latencyMillis} ms` : '-' }}</strong></div>
          <div class="status-line"><span>使用等级</span><strong>{{ levelLabel(node.minUserLevel) }}</strong></div>
          <div class="status-load">
            <span>隧道 {{ node.activeTunnels }}</span>
            <span>连接 {{ node.activeStreams }}</span>
            <span>等待 {{ node.pendingClients || 0 }}</span>
          </div>
          <p v-if="node.error" class="error">{{ node.error }}</p>
        </article>
      </section>
      <p class="status-time">最后检测：{{ publicStatus.checkedAt ? shortTime(publicStatus.checkedAt) : '尚未检测' }}</p>
    </main>

    <main v-else-if="page === 'pricing'" class="simple-page">
      <section class="section-head">
        <p class="eyebrow">Sponsor Preview</p>
        <h1>赞助订阅页已预留</h1>
        <p>当前版本只展示等级权益和占位按钮，不接支付。后续可以接入手动订阅或支付回调。</p>
      </section>
      <div class="tier-grid">
        <article v-for="level in levels" :key="level.level" class="tier">
          <LevelBadge :level="level.level" :label="level.displayName" :color="level.badgeColor" />
          <strong>{{ level.keyLimit }} 个 Key</strong>
          <p>{{ level.description }}</p>
          <button type="button">即将开放</button>
        </article>
      </div>
    </main>

    <main v-else-if="page === 'login'" class="login-page">
      <form class="login-panel" @submit.prevent="login">
        <p class="eyebrow">Secure Console</p>
        <h1>登录 BlockBridge</h1>
        <label>账号<input v-model="loginForm.username" autocomplete="username" required /></label>
        <label>密码<input v-model="loginForm.password" type="password" autocomplete="current-password" required /></label>
        <button class="primary" type="submit">登录</button>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
    </main>

    <main v-else-if="page === 'admin'" class="console">
      <aside class="sidebar">
        <div>
          <p class="eyebrow">Admin</p>
          <h2>{{ me?.displayName }}</h2>
        </div>
        <button v-for="tab in adminTabs" :key="tab.id" type="button" :class="{ active: adminTab === tab.id }" @click="adminTab = tab.id">{{ tab.name }}</button>
      </aside>

      <section class="workspace">
        <div v-if="adminTab === 'overview'" class="stack">
          <section class="ops-head">
            <div>
              <p class="eyebrow">Operations Center</p>
              <h1>中转节点运行态</h1>
            </div>
            <button type="button" class="primary" @click="loadAdmin">刷新监控</button>
          </section>
          <section class="metrics-row">
            <article><span>节点</span><strong>{{ overview.totals?.nodes || 0 }}</strong></article>
            <article><span>在线</span><strong>{{ overview.totals?.online || 0 }}</strong></article>
            <article><span>隧道</span><strong>{{ overview.totals?.tunnels || 0 }}</strong></article>
            <article><span>连接</span><strong>{{ overview.totals?.streams || 0 }}</strong></article>
            <article><span>等待</span><strong>{{ overview.totals?.pending || 0 }}</strong></article>
            <article><span>流量</span><strong>{{ bytes(overview.totals?.traffic || 0) }}</strong></article>
          </section>
          <section class="health-board">
            <article v-for="item in overview.nodes" :key="item.record.id" class="health-card">
              <div class="health-ring" :class="{ bad: !item.online }">{{ item.online ? 'ON' : 'OFF' }}</div>
              <div>
                <h3>{{ item.record.name }}</h3>
                <p>{{ item.record.region }} · {{ item.record.publicHost }}</p>
                <small>{{ item.error || `${item.status?.runtime?.activeTunnels || 0} 隧道 / ${item.status?.runtime?.activeStreams || 0} 连接 / ${item.status?.runtime?.pendingClients || 0} 等待` }}</small>
              </div>
            </article>
          </section>
          <section class="panel">
            <div class="panel-title"><h2>流量趋势</h2><select v-model="metricField"><option value="activeStreams">实时连接</option><option value="activeTunnels">在线隧道</option><option value="totalBytesToUser">下行流量</option></select></div>
            <SparkChart :samples="metrics" :field="metricField" />
          </section>
          <section class="node-list compact-node-list">
            <article v-for="item in overview.nodes" :key="item.record.id" class="node-card">
              <header><strong>{{ item.record.name }}</strong><span :class="['state', item.online ? 'on' : 'off']">{{ item.online ? '在线' : '离线' }}</span></header>
              <p>{{ item.record.publicHost }} · {{ item.record.apiBase }}</p>
              <small>{{ item.error || `${item.status?.runtime?.activeTunnels || 0} 隧道 / ${item.status?.runtime?.activeStreams || 0} 连接 / 端口池 ${item.status?.runtime?.portPoolUsed || 0}/${item.status?.runtime?.portPoolTotal || 0}` }}</small>
              <div v-if="item.status?.runtime?.tunnels?.length" class="tunnel-mini-list">
                <span v-for="tunnel in item.status.runtime.tunnels" :key="`${item.record.id}-${tunnel.tokenName}`">
                  {{ tunnel.tokenName }} · {{ tunnel.publicPort }} · {{ tunnel.clientVersion || 'legacy' }} · {{ bytes((tunnel.totalBytesToHost || 0) + (tunnel.totalBytesToUser || 0)) }}
                </span>
              </div>
            </article>
          </section>
        </div>

        <div v-if="adminTab === 'nodes'" class="stack">
          <section class="panel">
            <div class="panel-title"><h2>添加节点</h2><button type="button" @click="createNode">添加</button></div>
            <div class="form-grid node-form">
              <input v-model="nodeForm.id" placeholder="节点 ID" />
              <input v-model="nodeForm.name" placeholder="节点名称" />
              <input v-model="nodeForm.region" placeholder="区域" />
              <input v-model="nodeForm.publicHost" placeholder="公网地址" />
              <input v-model="nodeForm.apiBase" placeholder="http://节点IP:8080" />
              <input v-model="nodeForm.apiUsername" placeholder="节点 API 账号" />
              <input v-model="nodeForm.apiPassword" type="password" placeholder="节点 API 密码" />
              <select v-model.number="nodeForm.minUserLevel"><option :value="1">一级可用</option><option :value="2">二级可用</option><option :value="3">三级可用</option></select>
              <select v-model="nodeForm.groupId"><option value="">无分组</option><option v-for="group in groups" :key="group.id" :value="group.id">{{ group.name }}</option></select>
              <div class="tag-select"><label v-for="tag in tags" :key="tag.id"><input type="checkbox" :value="tag.id" v-model="nodeForm.tagIds" />{{ tag.name }}</label></div>
            </div>
          </section>
          <section class="panel">
            <div class="panel-title">
              <h2>节点列表</h2>
              <div class="tools">
                <button type="button" @click="bulk('enable')">批量启用</button>
                <button type="button" @click="bulk('disable')">批量停用</button>
                <button type="button" class="danger" @click="bulk('delete')">批量删除</button>
              </div>
            </div>
            <div class="table">
              <div class="row head"><span></span><span>节点</span><span>最低等级</span><span>分组</span><span>状态</span><span>操作</span></div>
              <div v-for="node in nodes" :key="node.id" class="row">
                <input type="checkbox" :value="node.id" v-model="selectedNodes" />
                <span><strong>{{ node.name }}</strong><small>{{ node.publicHost }}</small></span>
                <select v-model.number="node.editMinUserLevel"><option :value="1">一级</option><option :value="2">二级</option><option :value="3">三级</option></select>
                <select v-model="node.editGroupId"><option value="">无分组</option><option v-for="group in groups" :key="group.id" :value="group.id">{{ group.name }}</option></select>
                <label class="switch"><input type="checkbox" v-model="node.editEnabled" />启用</label>
                <span class="actions"><button type="button" @click="saveNode(node)">保存</button><button type="button" class="danger" @click="deleteNode(node.id)">删除</button></span>
              </div>
            </div>
          </section>
        </div>

        <div v-if="adminTab === 'users'" class="stack">
          <section class="panel">
            <div class="panel-title"><h2>创建账号</h2><button type="button" @click="createUser">创建</button></div>
            <div class="form-grid">
              <input v-model="userForm.username" placeholder="账号" />
              <input v-model="userForm.displayName" placeholder="显示名" />
              <input v-model="userForm.password" type="password" placeholder="密码" />
              <select v-model="userForm.role"><option value="user">用户</option><option value="admin">管理员</option></select>
              <select v-model.number="userForm.level" :disabled="userForm.role === 'admin'"><option :value="1">一级</option><option :value="2">二级</option><option :value="3">三级</option></select>
            </div>
          </section>
          <section class="panel table-panel">
            <div class="row head"><span>账号</span><span>角色</span><span>等级</span><span>状态</span><span>操作</span></div>
            <div v-for="user in users" :key="user.id" class="row">
              <span><strong>{{ user.username }}</strong><small>{{ user.displayName }}</small></span>
              <select v-model="user.editRole"><option value="user">用户</option><option value="admin">管理员</option></select>
              <LevelBadge v-if="user.editRole === 'user'" :level="user.editLevel" :label="levelLabel(user.editLevel)" :color="levelColor(user.editLevel)" />
              <span v-else>管理员</span>
              <label class="switch"><input type="checkbox" v-model="user.editEnabled" />启用</label>
              <span class="actions"><button type="button" @click="saveUser(user)">保存</button><button type="button" class="danger" @click="deleteUser(user.id)">删除</button></span>
            </div>
          </section>
        </div>

        <div v-if="adminTab === 'levels'" class="stack">
          <section class="panel levels-grid">
            <article v-for="level in levels" :key="level.level" class="level-editor">
              <LevelBadge :level="level.level" :label="level.displayName" :color="level.badgeColor" />
              <label>名称<input v-model="level.displayName" /></label>
              <label>说明<input v-model="level.description" /></label>
              <label>Key 上限<input v-model.number="level.keyLimit" type="number" min="0" /></label>
              <label>颜色<input v-model="level.badgeColor" type="color" /></label>
              <label class="switch"><input type="checkbox" v-model="level.enabled" />启用</label>
            </article>
          </section>
          <button class="primary" type="button" @click="saveLevels">保存等级配置</button>
        </div>

        <div v-if="adminTab === 'keys'" class="stack">
          <section class="panel">
            <div class="panel-title"><h2>为用户生成 Key</h2><button type="button" @click="adminCreateKey">生成</button></div>
            <div class="form-grid">
              <select v-model="adminKeyForm.accountId"><option value="">选择用户</option><option v-for="user in normalUsers" :key="user.id" :value="user.id">{{ user.username }}</option></select>
              <select v-model="adminKeyForm.nodeId"><option value="">选择节点</option><option v-for="node in nodes" :key="node.id" :value="node.id">{{ node.name }}</option></select>
            </div>
            <div v-if="createdToken" class="success token-copy">
              <span>新 Key 明文：</span>
              <button type="button" class="copy-token" title="复制 Token" @click="copyToken(createdToken)"><code>{{ createdToken }}</code></button>
            </div>
          </section>
          <section class="panel table-panel">
            <div class="row head"><span>用户</span><span>节点</span><span>Key 名称</span><span>Token</span><span>操作</span></div>
            <div v-for="key in keys" :key="key.id" class="row">
              <span>{{ key.username }}</span><span>{{ key.nodeName }}</span><span>{{ key.tokenName }}</span><code>{{ key.tokenMasked }}</code>
              <span class="actions"><button type="button" @click="copyStoredToken('admin', key.id)">复制</button><button type="button" class="danger" @click="deleteAdminKey(key.id)">撤销</button></span>
            </div>
          </section>
        </div>

        <div v-if="adminTab === 'taxonomy'" class="stack two-col">
          <section class="panel">
            <div class="panel-title"><h2>分组</h2><button type="button" @click="saveGroup">保存</button></div>
            <input v-model="groupForm.name" placeholder="分组名称" />
            <input v-model="groupForm.description" placeholder="说明" />
            <div v-for="group in groups" :key="group.id" class="pill-line"><span>{{ group.name }}</span><button class="danger" type="button" @click="deleteGroup(group.id)">删除</button></div>
          </section>
          <section class="panel">
            <div class="panel-title"><h2>标签</h2><button type="button" @click="saveTag">保存</button></div>
            <input v-model="tagForm.name" placeholder="标签名称" />
            <input v-model="tagForm.color" type="color" />
            <div v-for="tag in tags" :key="tag.id" class="pill-line"><span :style="{ color: tag.color }">{{ tag.name }}</span><button class="danger" type="button" @click="deleteTag(tag.id)">删除</button></div>
          </section>
        </div>

        <div v-if="adminTab === 'logs'" class="stack">
          <section class="panel table-panel">
            <div class="row head"><span>时间</span><span>操作者</span><span>动作</span><span>目标</span></div>
            <div v-for="log in auditLogs" :key="log.id" class="row">
              <span>{{ shortTime(log.createdAt) }}</span><span>{{ log.actorUsername }}</span><span>{{ log.action }}</span><span>{{ log.targetType }} / {{ log.targetId }}</span>
            </div>
          </section>
        </div>
      </section>
    </main>

    <main v-else-if="page === 'account'" class="account">
      <section class="account-head">
        <LevelBadge :level="me?.level || 1" :label="levelLabel(me?.level || 1)" :color="levelColor(me?.level || 1)" />
        <div><p class="eyebrow">User Center</p><h1>{{ me?.displayName }}</h1><p>已使用 {{ accountQuota.used }} / {{ accountQuota.limit }} 个 Key</p></div>
      </section>
      <section class="panel">
        <div class="panel-title"><h2>生成 Key</h2><button type="button" @click="createAccountKey">生成</button></div>
        <select v-model="accountKeyForm.nodeId"><option value="">选择可用节点</option><option v-for="node in accountNodes" :key="node.id" :value="node.id">{{ node.name }} · {{ node.publicHost }}</option></select>
        <div v-if="createdToken" class="success token-copy">
          <span>请立即保存 Key 明文：</span>
          <button type="button" class="copy-token" title="复制 Token" @click="copyToken(createdToken)"><code>{{ createdToken }}</code></button>
        </div>
      </section>
      <section class="node-list">
        <article v-for="key in accountKeys" :key="key.id" class="node-card">
          <header><strong>{{ key.nodeName }}</strong><button class="danger" type="button" @click="deleteAccountKey(key.id)">删除</button></header>
          <p>{{ key.tokenName }}</p>
          <button type="button" class="copy-token" title="复制 Token" @click="copyStoredToken('account', key.id)"><code>{{ key.tokenMasked }}</code></button>
        </article>
      </section>
    </main>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import LevelBadge from "./components/LevelBadge.vue";
import SparkChart from "./components/SparkChart.vue";

const route = ref(window.location.pathname);
const me = ref(null);
const levels = ref([]);
const overview = ref({ nodes: [], totals: {} });
const publicStatus = ref({ nodes: [], totals: {}, checkedAt: "" });
const users = ref([]);
const nodes = ref([]);
const groups = ref([]);
const tags = ref([]);
const keys = ref([]);
const metrics = ref([]);
const auditLogs = ref([]);
const accountNodes = ref([]);
const accountKeys = ref([]);
const accountQuota = ref({ used: 0, limit: 0 });
const error = ref("");
const toast = ref("");
const createdToken = ref("");
const adminTab = ref("overview");
const metricField = ref("activeStreams");
const selectedNodes = ref([]);

const loginForm = ref({ username: "", password: "" });
const userForm = ref({ username: "", displayName: "", password: "", role: "user", level: 1 });
const nodeForm = ref({ id: "", name: "", region: "default", publicHost: "", apiBase: "", apiUsername: "node-api", apiPassword: "", minUserLevel: 1, groupId: "", tagIds: [] });
const adminKeyForm = ref({ accountId: "", nodeId: "" });
const accountKeyForm = ref({ nodeId: "" });
const groupForm = ref({ name: "", description: "" });
const tagForm = ref({ name: "", color: "#4d6b8a" });

const adminTabs = [
  { id: "overview", name: "总览" },
  { id: "nodes", name: "节点" },
  { id: "users", name: "用户" },
  { id: "levels", name: "等级" },
  { id: "keys", name: "Key" },
  { id: "taxonomy", name: "分组标签" },
  { id: "logs", name: "日志" },
];

const page = computed(() => {
  if (route.value.startsWith("/admin")) return "admin";
  if (route.value.startsWith("/account")) return "account";
  if (route.value.startsWith("/login")) return "login";
  if (route.value.startsWith("/status")) return "status";
  if (route.value.startsWith("/pricing")) return "pricing";
  return "home";
});
const normalUsers = computed(() => users.value.filter((item) => item.role === "user"));

onMounted(async () => {
  window.addEventListener("popstate", () => { route.value = window.location.pathname; });
  await loadLevels();
  await loadMe();
  await routeLoad();
});

watch(adminTab, () => {
  if (page.value === "admin") loadAdmin();
});

async function api(path, options = {}) {
  const response = await fetch(path, {
    method: options.method || "GET",
    headers: { "Content-Type": "application/json" },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.error || response.statusText);
  }
  return response.json();
}

async function loadMe() {
  const data = await api("/api/auth/me");
  me.value = data.account;
}

async function loadLevels() {
  levels.value = (await api("/api/levels")).levels;
}

async function routeLoad() {
  createdToken.value = "";
  if (page.value === "admin") {
    if (me.value?.role !== "admin") return go("/login");
    await loadAdmin();
  }
  if (page.value === "account") {
    if (me.value?.role !== "user") return go("/login");
    await loadAccount();
  }
  if (page.value === "status") {
    await loadPublicStatus();
  }
}

async function loadAdmin() {
  const [overviewData, userData, levelData, nodeData, keyData, metricData, logData] = await Promise.all([
    api("/api/overview"),
    api("/api/admin/users"),
    api("/api/admin/levels"),
    api("/api/admin/nodes"),
    api("/api/admin/keys"),
    api("/api/metrics?range=24h"),
    api("/api/audit-logs?limit=120"),
  ]);
  overview.value = overviewData;
  users.value = userData.users.map((user) => ({ ...user, editRole: user.role, editLevel: user.level || 1, editEnabled: user.enabled }));
  levels.value = levelData.levels;
  nodes.value = nodeData.nodes.map((node) => ({ ...node, editMinUserLevel: node.minUserLevel, editGroupId: node.groupId || "", editEnabled: node.enabled }));
  groups.value = nodeData.groups;
  tags.value = nodeData.tags;
  keys.value = keyData.keys;
  metrics.value = metricData.samples;
  auditLogs.value = logData.logs;
}

async function loadAccount() {
  const [nodesData, keysData] = await Promise.all([api("/api/account/nodes"), api("/api/account/keys")]);
  accountNodes.value = nodesData.nodes;
  accountKeys.value = keysData.keys;
  accountQuota.value = keysData.quota;
}

async function loadPublicStatus() {
  publicStatus.value = await api("/api/public/status");
}

async function login() {
  error.value = "";
  try {
    const data = await api("/api/auth/login", { method: "POST", body: loginForm.value });
    me.value = data.account;
    go(me.value.role === "admin" ? "/admin" : "/account");
  } catch (err) {
    error.value = err.message;
  }
}

async function logout() {
  await api("/api/auth/logout", { method: "POST" }).catch(() => {});
  me.value = null;
  go("/");
}

async function createUser() {
  await api("/api/admin/users", { method: "POST", body: userForm.value });
  userForm.value = { username: "", displayName: "", password: "", role: "user", level: 1 };
  await loadAdmin();
  flash("账号已创建");
}

async function saveUser(user) {
  await api(`/api/admin/users/${user.id}`, { method: "PUT", body: { ...user, role: user.editRole, level: user.editLevel, enabled: user.editEnabled } });
  await loadAdmin();
  flash("账号已保存");
}

async function deleteUser(id) {
  await api(`/api/admin/users/${id}`, { method: "DELETE" });
  await loadAdmin();
}

async function saveLevels() {
  await api("/api/admin/levels", { method: "PUT", body: { levels: levels.value } });
  await loadAdmin();
  flash("等级配置已保存");
}

async function createNode() {
  await api("/api/admin/nodes", { method: "POST", body: nodeForm.value });
  nodeForm.value = { id: "", name: "", region: "default", publicHost: "", apiBase: "", apiUsername: "node-api", apiPassword: "", minUserLevel: 1, groupId: "", tagIds: [] };
  await loadAdmin();
  flash("节点已添加");
}

async function saveNode(node) {
  await api(`/api/admin/nodes/${node.id}`, { method: "PUT", body: { ...node, minUserLevel: node.editMinUserLevel, groupId: node.editGroupId, enabled: node.editEnabled } });
  await loadAdmin();
  flash("节点已保存");
}

async function deleteNode(id) {
  await api(`/api/admin/nodes/${id}`, { method: "DELETE" });
  await loadAdmin();
}

async function bulk(action) {
  await api("/api/admin/nodes/bulk", { method: "POST", body: { nodeIds: selectedNodes.value, action } });
  selectedNodes.value = [];
  await loadAdmin();
}

async function saveGroup() {
  await api("/api/admin/groups", { method: "POST", body: groupForm.value });
  groupForm.value = { name: "", description: "" };
  await loadAdmin();
}

async function deleteGroup(id) {
  await api(`/api/admin/groups/${id}`, { method: "DELETE" });
  await loadAdmin();
}

async function saveTag() {
  await api("/api/admin/tags", { method: "POST", body: tagForm.value });
  tagForm.value = { name: "", color: "#4d6b8a" };
  await loadAdmin();
}

async function deleteTag(id) {
  await api(`/api/admin/tags/${id}`, { method: "DELETE" });
  await loadAdmin();
}

async function adminCreateKey() {
  const data = await api("/api/admin/keys", { method: "POST", body: adminKeyForm.value });
  createdToken.value = data.token;
  await loadAdmin();
}

async function deleteAdminKey(id) {
  await api(`/api/admin/keys/${id}`, { method: "DELETE" });
  await loadAdmin();
}

async function createAccountKey() {
  const data = await api("/api/account/keys", { method: "POST", body: accountKeyForm.value });
  createdToken.value = data.token;
  await loadAccount();
}

async function deleteAccountKey(id) {
  await api(`/api/account/keys/${id}`, { method: "DELETE" });
  await loadAccount();
}

async function copyToken(token) {
  if (!token) return;
  try {
    await navigator.clipboard.writeText(token);
    flash("Token 已复制");
  } catch {
    fallbackCopy(token);
    flash("Token 已复制");
  }
}

async function copyStoredToken(scope, keyId) {
  const path = scope === "admin" ? `/api/admin/keys/${keyId}/token` : `/api/account/keys/${keyId}/token`;
  const data = await api(path);
  await copyToken(data.token);
}

function fallbackCopy(value) {
  const input = document.createElement("textarea");
  input.value = value;
  input.setAttribute("readonly", "");
  input.style.position = "fixed";
  input.style.left = "-9999px";
  document.body.appendChild(input);
  input.select();
  document.execCommand("copy");
  document.body.removeChild(input);
}

function go(path) {
  history.pushState({}, "", path);
  route.value = path;
  routeLoad().catch((err) => flash(err.message));
}

function levelLabel(level) {
  return levels.value.find((item) => item.level === Number(level))?.displayName || `${level}级用户`;
}

function levelColor(level) {
  return levels.value.find((item) => item.level === Number(level))?.badgeColor || "#ad6f3b";
}

function bytes(value) {
  const units = ["B", "KB", "MB", "GB", "TB"];
  let size = Number(value || 0);
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size.toFixed(unit ? 1 : 0)} ${units[unit]}`;
}

function shortTime(value) {
  return new Date(value).toLocaleString();
}

function flash(message) {
  toast.value = message;
  setTimeout(() => { toast.value = ""; }, 2200);
}
</script>
