<template>
  <div class="app-shell">
    <header class="topbar">
      <button class="brand" type="button" @click="go('/')">
        <span class="brand-mark">BB</span>
        <span class="brand-copy">
          <strong>BlockBridge</strong>
        </span>
      </button>

      <nav class="topnav" aria-label="主导航">
        <button type="button" :class="{ active: page === 'home' }" @click="go('/')">首页</button>
        <button type="button" :class="{ active: page === 'status' }" @click="go('/status')">节点状态</button>
        <button type="button" :class="{ active: page === 'pricing' }" @click="go('/pricing')">赞助订阅</button>
        <button v-if="!me" type="button" :class="{ active: page === 'login' }" @click="go('/login')">登录</button>
        <button v-if="me?.role === 'admin'" type="button" :class="{ active: page === 'admin' }" @click="go('/admin')">管理后台</button>
        <button v-if="me?.role === 'user'" type="button" :class="{ active: page === 'account' }" @click="go('/account')">个人中心</button>
        <button v-if="me" type="button" class="quiet" @click="logout">退出</button>
      </nav>
    </header>

    <main v-if="page === 'home'" class="landing page-frame">
      <section class="hero-ops">
        <div class="hero-copy">
          <h1>BlockBridge 中转控制台</h1>
          <div class="hero-actions">
            <button v-if="!me" type="button" class="primary" @click="go('/login')">进入控制台</button>
            <button v-else-if="me.role === 'admin'" type="button" class="primary" @click="go('/admin')">打开管理后台</button>
            <button v-else type="button" class="primary" @click="go('/account')">打开个人中心</button>
            <button type="button" @click="go('/status')">查看节点状态</button>
          </div>
        </div>

        <div class="ops-visual" aria-label="BlockBridge 中转网络摘要">
          <div class="visual-metrics">
            <article>
              <span>节点</span>
              <strong>{{ publicStatus.totals?.online || 0 }}/{{ publicStatus.totals?.nodes || 0 }}</strong>
            </article>
            <article>
              <span>在线隧道</span>
              <strong>{{ publicStatus.totals?.tunnels || 0 }}</strong>
            </article>
            <article>
              <span>实时流</span>
              <strong>{{ publicStatus.totals?.streams || 0 }}</strong>
            </article>
          </div>
          <div class="relay-map">
            <span v-for="node in publicStatus.nodes.slice(0, 8)" :key="node.id" :class="['relay-dot', node.online ? 'online' : 'offline']">
              {{ node.region?.slice(0, 2) || 'NA' }}
            </span>
            <span v-if="!publicStatus.nodes.length" class="relay-dot offline">NA</span>
          </div>
        </div>
      </section>

      <section class="process-band">
        <article>
          <strong>注册中转节点</strong>
          <p>录入公网 Host、节点 API 和准入等级，集中管理多台 relay-station。</p>
        </article>
        <article>
          <strong>分配用户 Key</strong>
          <p>按用户等级和 Key 配额发放隧道凭证，创建和撤销会同步到节点。</p>
        </article>
        <article>
          <strong>监控链路状态</strong>
          <p>总览节点在线、隧道、实时流、端口池和审计日志，方便长期运维。</p>
        </article>
      </section>
    </main>

    <main v-else-if="page === 'status'" class="status-page page-frame">
      <section class="page-heading">
        <div>
          <h1>全球中转节点状态</h1>
        </div>
        <button type="button" @click="loadPublicStatus">刷新状态</button>
      </section>

      <section class="metrics-row">
        <article><span>总节点</span><strong>{{ publicStatus.totals?.nodes || 0 }}</strong></article>
        <article><span>活跃节点</span><strong>{{ publicStatus.totals?.online || 0 }}</strong></article>
        <article><span>在线隧道</span><strong>{{ publicStatus.totals?.tunnels || 0 }}</strong></article>
        <article><span>实时流</span><strong>{{ publicStatus.totals?.streams || 0 }}</strong></article>
      </section>

      <section class="status-grid">
        <article v-for="node in publicStatus.nodes" :key="node.id" class="status-card">
          <header>
            <div>
              <span class="region-tag">{{ node.region }}</span>
              <h2>{{ node.name }}</h2>
            </div>
            <span :class="['state', node.online ? 'on' : 'off']">{{ node.online ? '在线' : '离线' }}</span>
          </header>
          <div class="status-body">
            <div class="info-row"><span>接入地址</span><code>{{ node.publicHost }}</code></div>
            <div class="info-row"><span>平均延迟</span><strong>{{ node.online ? `${node.latencyMillis}ms` : '---' }}</strong></div>
            <div class="info-row"><span>准入级别</span><strong>{{ levelLabel(node.minUserLevel) }}</strong></div>
            <div class="info-row"><span>隧道/实时流</span><strong>{{ node.activeTunnels || 0 }} / {{ node.activeStreams || 0 }}</strong></div>
          </div>
        </article>
        <div v-if="!publicStatus.nodes.length" class="empty-state">暂无公开节点状态。</div>
      </section>
    </main>

    <main v-else-if="page === 'pricing'" class="simple-page page-frame">
      <section class="page-heading">
        <div>
          <h1>赞助订阅与准入等级</h1>
        </div>
      </section>

      <section class="tier-grid">
        <article v-for="level in levels" :key="level.level" class="tier-card" :style="{ '--level-color': level.badgeColor }">
          <LevelBadge :level="level.level" :label="level.displayName" :color="level.badgeColor" />
          <div class="tier-meta">
            <span>LEVEL {{ level.level }}</span>
            <strong>{{ level.keyLimit }} 个并发 Key</strong>
            <p>{{ level.description }}</p>
          </div>
          <div class="tier-footer">
            <span :class="['state', level.enabled ? 'on' : 'off']">{{ level.enabled ? '可用' : '暂停' }}</span>
            <button type="button" class="ghost">即将开放</button>
          </div>
        </article>
      </section>
    </main>

    <main v-else-if="page === 'login'" class="login-page page-frame">
      <form class="login-panel" @submit.prevent="login">
        <h1>登录 BlockBridge</h1>
        <div class="input-group">
          <label for="login-username">用户名</label>
          <input id="login-username" v-model="loginForm.username" autocomplete="username" required placeholder="admin 或用户账号" />
        </div>
        <div class="input-group">
          <label for="login-password">密码</label>
          <input id="login-password" v-model="loginForm.password" type="password" autocomplete="current-password" required placeholder="账号密码" />
        </div>
        <button class="primary wide" type="submit">立即登录</button>
        <p v-if="error" class="error-msg">{{ error }}</p>
      </form>
    </main>

    <main v-else-if="page === 'admin'" class="console">
      <aside class="sidebar">
        <div class="sidebar-head">
          <h2>{{ me?.displayName }}</h2>
        </div>
        <nav class="sidebar-nav" aria-label="后台模块">
          <button v-for="tab in adminTabs" :key="tab.id" type="button" :class="{ active: adminTab === tab.id }" @click="adminTab = tab.id">
            <span class="tab-indicator"></span>
            {{ tab.name }}
          </button>
        </nav>
      </aside>

      <section class="workspace">
        <section class="workspace-head">
          <div>
            <h1>{{ currentAdminTabName }}</h1>
          </div>
          <button type="button" class="primary" @click="loadAdmin">同步全网数据</button>
        </section>

        <div v-if="adminTab === 'overview'" class="stack">
          <section class="metrics-row admin-metrics">
            <article><span>中转节点</span><strong>{{ overview.totals?.nodes || 0 }}</strong></article>
            <article><span>就绪状态</span><strong>{{ overview.totals?.online || 0 }}</strong></article>
            <article><span>活跃隧道</span><strong>{{ overview.totals?.tunnels || 0 }}</strong></article>
            <article><span>实时流</span><strong>{{ overview.totals?.streams || 0 }}</strong></article>
            <article><span>总吞吐</span><strong>{{ bytes(overview.totals?.traffic || 0) }}</strong></article>
          </section>

          <section class="health-board">
            <article v-for="item in overview.nodes" :key="item.record.id" class="health-card">
              <div class="health-indicator" :class="{ bad: !item.online }">
                <div class="indicator-inner"></div>
              </div>
              <div class="health-info">
                <h3>{{ item.record.name }}</h3>
                <p>{{ item.record.region }} / <code>{{ item.record.publicHost }}</code></p>
                <small>{{ item.error || `${item.status?.runtime?.activeTunnels || 0} 隧道 / ${item.status?.runtime?.activeStreams || 0} 实时流` }}</small>
              </div>
            </article>
          </section>

          <section class="panel chart-panel">
            <div class="panel-title">
              <h2>网络流量趋势</h2>
              <select v-model="metricField">
                <option value="activeStreams">并发实时流</option>
                <option value="activeTunnels">活跃隧道</option>
                <option value="totalBytesToUser">下行出口流量</option>
                <option value="totalBytesToHost">上行入口流量</option>
              </select>
            </div>
            <SparkChart :samples="metrics" :field="metricField" />
          </section>

          <section class="node-grid compact">
            <article v-for="item in overview.nodes" :key="item.record.id" class="node-card">
              <header>
                <div>
                  <strong>{{ item.record.name }}</strong>
                  <small>{{ item.record.groupName || item.record.region }}</small>
                </div>
                <span :class="['state', item.online ? 'on' : 'off']">{{ item.online ? '就绪' : '离线' }}</span>
              </header>
              <div class="node-details">
                <code>{{ item.record.publicHost }}</code>
                <div class="pool-usage">
                  <span>PORT POOL {{ item.status?.runtime?.portPoolUsed || 0 }}/{{ item.status?.runtime?.portPoolTotal || 0 }}</span>
                  <div class="pool-bar">
                    <div class="pool-progress" :style="{ width: percent(item.status?.runtime?.portPoolUsed || 0, item.status?.runtime?.portPoolTotal || 0) + '%' }"></div>
                  </div>
                </div>
              </div>
              <div v-if="item.status?.runtime?.tunnels?.length" class="tunnel-mini-list">
                <div v-for="tunnel in item.status.runtime.tunnels" :key="`${item.record.id}-${tunnel.tokenName}`" class="tunnel-item">
                  <span>{{ tunnel.tokenName }}</span>
                  <strong>{{ bytes((tunnel.totalBytesToHost || 0) + (tunnel.totalBytesToUser || 0)) }}</strong>
                </div>
              </div>
            </article>
          </section>
        </div>

        <div v-else-if="adminTab === 'nodes'" class="stack">
          <form class="panel" @submit.prevent="createNode">
            <div class="panel-title">
              <h2>注册新节点</h2>
              <button type="submit" class="primary">注册节点</button>
            </div>
            <div class="form-grid node-form">
              <div class="input-group"><label>标识符</label><input v-model="nodeForm.id" placeholder="node-01" /></div>
              <div class="input-group"><label>显示名称</label><input v-model="nodeForm.name" placeholder="上海核心枢纽" required /></div>
              <div class="input-group"><label>部署区域</label><input v-model="nodeForm.region" placeholder="China East" /></div>
              <div class="input-group"><label>公网 Host</label><input v-model="nodeForm.publicHost" placeholder="sh.mesh.net" required /></div>
              <div class="input-group"><label>API 端点</label><input v-model="nodeForm.apiBase" placeholder="http://1.2.3.4:8080" required /></div>
              <div class="input-group"><label>API 用户</label><input v-model="nodeForm.apiUsername" placeholder="node-api" required /></div>
              <div class="input-group"><label>API 密码</label><input v-model="nodeForm.apiPassword" type="password" placeholder="节点 API 密码" required /></div>
              <div class="input-group">
                <label>准入策略</label>
                <select v-model.number="nodeForm.minUserLevel">
                  <option v-for="level in levels" :key="level.level" :value="level.level">{{ level.displayName }}</option>
                </select>
              </div>
              <div class="input-group">
                <label>节点分组</label>
                <select v-model="nodeForm.groupId">
                  <option value="">未分组</option>
                  <option v-for="group in groups" :key="group.id" :value="group.id">{{ group.name }}</option>
                </select>
              </div>
              <div class="input-group wide-field">
                <label>标签</label>
                <select v-model="nodeForm.tagIds" multiple class="multi-select">
                  <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
                </select>
              </div>
            </div>
          </form>

          <section class="panel table-panel">
            <div class="panel-title">
              <h2>受控节点资产清单</h2>
              <div class="panel-actions">
                <span class="selection-count">已选 {{ selectedNodes.length }}</span>
                <button type="button" @click="bulk('enable')">启用</button>
                <button type="button" @click="bulk('disable')">停用</button>
                <button type="button" class="danger" @click="bulk('delete')">删除</button>
              </div>
            </div>
            <div class="data-table node-table">
              <div class="table-row table-head">
                <span></span><span>节点</span><span>策略</span><span>分组/标签</span><span>状态</span><span>控制</span>
              </div>
              <div v-for="node in nodes" :key="node.id" class="table-row">
                <label class="check-cell" :class="{ checked: selectedNodes.includes(node.id) }" :aria-label="`选择 ${node.name}`">
                  <input v-model="selectedNodes" type="checkbox" :value="node.id" />
                  <span></span>
                </label>
                <div class="entity-cell">
                  <strong>{{ node.name }}</strong>
                  <small>{{ node.id }} / {{ node.publicHost }}</small>
                  <code>{{ node.apiBase }}</code>
                </div>
                <select v-model.number="node.editMinUserLevel">
                  <option v-for="level in levels" :key="level.level" :value="level.level">{{ level.displayName }}</option>
                </select>
                <div class="stacked-controls">
                  <select v-model="node.editGroupId">
                    <option value="">未分组</option>
                    <option v-for="group in groups" :key="group.id" :value="group.id">{{ group.name }}</option>
                  </select>
                  <div class="node-tag-strip">
                    <span v-for="tag in node.tags" :key="tag.id" class="tag-chip" :style="{ '--tag-color': tag.color }">{{ tag.name }}</span>
                    <span v-if="!node.tags?.length" class="muted">无标签</span>
                  </div>
                  <select v-model="node.editTagIds" multiple class="multi-select mini">
                    <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
                  </select>
                </div>
                <label class="switch">
                  <input v-model="node.editEnabled" type="checkbox" />
                  <span>{{ node.editEnabled ? '启用' : '停用' }}</span>
                </label>
                <div class="actions">
                  <button type="button" @click="saveNode(node)">更新</button>
                  <button type="button" class="danger" @click="deleteNode(node.id)">移除</button>
                </div>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="adminTab === 'users'" class="stack">
          <form class="panel" @submit.prevent="createUser">
            <div class="panel-title">
              <h2>创建账号</h2>
              <button type="submit" class="primary">创建账号</button>
            </div>
            <div class="form-grid">
              <div class="input-group"><label>用户名</label><input v-model="userForm.username" required placeholder="player01" /></div>
              <div class="input-group"><label>显示名称</label><input v-model="userForm.displayName" required placeholder="玩家名称" /></div>
              <div class="input-group"><label>初始密码</label><input v-model="userForm.password" type="password" required placeholder="至少 1 位" /></div>
              <div class="input-group">
                <label>角色</label>
                <select v-model="userForm.role">
                  <option value="user">普通用户</option>
                  <option value="admin">管理员</option>
                </select>
              </div>
              <div v-if="userForm.role === 'user'" class="input-group">
                <label>等级</label>
                <select v-model.number="userForm.level">
                  <option v-for="level in levels" :key="level.level" :value="level.level">{{ level.displayName }}</option>
                </select>
              </div>
            </div>
          </form>

          <section class="panel table-panel">
            <div class="panel-title">
              <h2>账号目录</h2>
              <span class="muted">{{ users.length }} 个账号 / {{ normalUsers.length }} 个用户</span>
            </div>
            <div class="data-table user-table">
              <div class="table-row table-head">
                <span>账号</span><span>角色</span><span>等级</span><span>状态</span><span>重置密码</span><span>控制</span>
              </div>
              <div v-for="user in users" :key="user.id" class="table-row">
                <div class="entity-cell">
                  <strong>{{ user.displayName }}</strong>
                  <small>{{ user.username }} / {{ user.createdAt ? shortTime(user.createdAt) : '未知时间' }}</small>
                </div>
                <select v-model="user.editRole">
                  <option value="user">普通用户</option>
                  <option value="admin">管理员</option>
                </select>
                <select v-model.number="user.editLevel" :disabled="user.editRole !== 'user'">
                  <option v-for="level in levels" :key="level.level" :value="level.level">{{ level.displayName }}</option>
                </select>
                <label class="switch">
                  <input v-model="user.editEnabled" type="checkbox" />
                  <span>{{ user.editEnabled ? '启用' : '停用' }}</span>
                </label>
                <input v-model="user.editPassword" type="password" placeholder="留空则不修改" />
                <div class="actions">
                  <button type="button" @click="saveUser(user)">保存</button>
                  <button type="button" class="danger" @click="deleteUser(user.id)">删除</button>
                </div>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="adminTab === 'levels'" class="stack">
          <section class="panel">
            <div class="panel-title">
              <h2>用户等级配置</h2>
              <button type="button" class="primary" @click="saveLevels">保存等级配置</button>
            </div>
            <div class="level-editor-grid">
              <article v-for="level in levels" :key="level.level" class="level-editor" :style="{ '--level-color': level.badgeColor }">
                <LevelBadge :level="level.level" :label="level.displayName" :color="level.badgeColor" />
                <div class="input-group"><label>显示名称</label><input v-model="level.displayName" /></div>
                <div class="input-group"><label>说明</label><input v-model="level.description" /></div>
                <div class="inline-fields">
                  <div class="input-group"><label>Key 上限</label><input v-model.number="level.keyLimit" type="number" min="0" /></div>
                  <div class="input-group"><label>徽章颜色</label><input v-model="level.badgeColor" type="color" /></div>
                </div>
                <label class="switch">
                  <input v-model="level.enabled" type="checkbox" />
                  <span>{{ level.enabled ? '启用' : '停用' }}</span>
                </label>
              </article>
            </div>
          </section>
        </div>

        <div v-else-if="adminTab === 'keys'" class="stack">
          <form class="panel" @submit.prevent="adminCreateKey">
            <div class="panel-title">
              <h2>为用户分配 Key</h2>
              <button type="submit" class="primary">生成 Key</button>
            </div>
            <div class="form-grid">
              <div class="input-group">
                <label>用户</label>
                <select v-model="adminKeyForm.accountId" required>
                  <option value="">选择用户</option>
                  <option v-for="user in normalUsers" :key="user.id" :value="user.id">{{ user.displayName }} / {{ user.username }}</option>
                </select>
              </div>
              <div class="input-group">
                <label>节点</label>
                <select v-model="adminKeyForm.nodeId" required>
                  <option value="">选择节点</option>
                  <option v-for="node in nodes.filter((item) => item.enabled)" :key="node.id" :value="node.id">{{ node.name }} / {{ node.publicHost }}</option>
                </select>
              </div>
            </div>
            <div v-if="createdToken" class="token-banner">
              <span>新 Key 只显示一次</span>
              <code @click="copyToken(createdToken)">{{ createdToken }}</code>
            </div>
          </form>

          <section class="panel table-panel">
            <div class="panel-title">
              <h2>活跃 Key 清单</h2>
              <span class="muted">{{ keys.length }} 个活跃 Key</span>
            </div>
            <div class="data-table key-table">
              <div class="table-row table-head">
                <span>Key</span><span>用户</span><span>节点</span><span>创建时间</span><span>控制</span>
              </div>
              <div v-for="key in keys" :key="key.id" class="table-row">
                <div class="entity-cell">
                  <strong>{{ key.tokenName }}</strong>
                  <code @click="copyStoredToken('admin', key.id)">{{ key.tokenMasked }}</code>
                </div>
                <div class="entity-cell">
                  <strong>{{ key.displayName }}</strong>
                  <small>{{ key.username }}</small>
                </div>
                <div class="entity-cell">
                  <strong>{{ key.nodeName }}</strong>
                  <small>{{ key.publicHost }}</small>
                </div>
                <span>{{ shortTime(key.createdAt) }}</span>
                <div class="actions">
                  <button type="button" @click="copyStoredToken('admin', key.id)">复制</button>
                  <button type="button" class="danger" @click="deleteAdminKey(key.id)">撤销</button>
                </div>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="adminTab === 'taxonomy'" class="stack taxonomy-grid">
          <form class="panel" @submit.prevent="saveGroup">
            <div class="panel-title">
              <h2>{{ groupForm.id ? '编辑分组' : '创建分组' }}</h2>
              <button type="submit" class="primary">{{ groupForm.id ? '保存分组' : '创建分组' }}</button>
            </div>
            <div class="input-group"><label>分组名称</label><input v-model="groupForm.name" required placeholder="华东节点组" /></div>
            <div class="input-group"><label>说明</label><input v-model="groupForm.description" placeholder="用途或区域说明" /></div>
            <button v-if="groupForm.id" type="button" class="ghost" @click="resetGroupForm">取消编辑</button>
            <div class="taxonomy-list">
              <article v-for="group in groups" :key="group.id">
                <div>
                  <strong>{{ group.name }}</strong>
                  <small>{{ group.description || '无说明' }} / {{ nodeCountByGroup(group.id) }} 个节点</small>
                </div>
                <div class="actions">
                  <button type="button" @click="editGroup(group)">编辑</button>
                  <button type="button" class="danger" @click="deleteGroup(group.id)">删除</button>
                </div>
              </article>
            </div>
          </form>

          <form class="panel" @submit.prevent="saveTag">
            <div class="panel-title">
              <h2>{{ tagForm.id ? '编辑标签' : '创建标签' }}</h2>
              <button type="submit" class="primary">{{ tagForm.id ? '保存标签' : '创建标签' }}</button>
            </div>
            <div class="input-group"><label>标签名称</label><input v-model="tagForm.name" required placeholder="低延迟" /></div>
            <div class="input-group"><label>标签颜色</label><input v-model="tagForm.color" type="color" /></div>
            <button v-if="tagForm.id" type="button" class="ghost" @click="resetTagForm">取消编辑</button>
            <div class="taxonomy-list">
              <article v-for="tag in tags" :key="tag.id">
                <div>
                  <span class="tag-chip" :style="{ '--tag-color': tag.color }">{{ tag.name }}</span>
                  <small>{{ nodeCountByTag(tag.id) }} 个节点</small>
                </div>
                <div class="actions">
                  <button type="button" @click="editTag(tag)">编辑</button>
                  <button type="button" class="danger" @click="deleteTag(tag.id)">删除</button>
                </div>
              </article>
            </div>
          </form>
        </div>

        <div v-else-if="adminTab === 'logs'" class="stack">
          <section class="panel table-panel">
            <div class="panel-title">
              <h2>审计日志</h2>
              <span class="muted">最近 {{ auditLogs.length }} 条</span>
            </div>
            <div class="data-table log-table">
              <div class="table-row table-head">
                <span>时间</span><span>操作者</span><span>动作</span><span>目标</span><span>IP</span><span>详情</span>
              </div>
              <div v-for="log in auditLogs" :key="log.id" class="table-row">
                <span>{{ shortTime(log.createdAt) }}</span>
                <strong>{{ log.actorUsername }}</strong>
                <code>{{ log.action }}</code>
                <span>{{ log.targetType }} / {{ log.targetId || '-' }}</span>
                <span>{{ log.ip || '-' }}</span>
                <small>{{ formatDetail(log.detail) }}</small>
              </div>
            </div>
          </section>
        </div>
      </section>
    </main>

    <main v-else-if="page === 'account'" class="account page-frame">
      <section class="account-head panel">
        <div class="profile-summary">
          <LevelBadge :level="me?.level || 1" :label="levelLabel(me?.level || 1)" :color="levelColor(me?.level || 1)" />
          <div class="profile-info">
            <h1>{{ me?.displayName }}</h1>
            <div class="quota-info">
              <span>隧道配额 {{ accountQuota.used }} / {{ accountQuota.limit }}</span>
              <div class="quota-bar">
                <div class="quota-fill" :style="{ width: accountQuotaPercent + '%' }"></div>
              </div>
            </div>
          </div>
        </div>
        <div class="account-stats">
          <article><span>可用节点</span><strong>{{ accountNodes.length }}</strong></article>
          <article><span>活跃 Key</span><strong>{{ accountKeys.length }}</strong></article>
        </div>
      </section>

      <div class="account-grid">
        <form class="panel account-create" @submit.prevent="createAccountKey">
          <div class="panel-title">
            <h2>申请新隧道</h2>
          </div>
          <div class="node-choice-list" role="radiogroup" aria-label="可用节点">
            <label v-for="node in accountNodes" :key="node.id" class="node-choice" :class="{ selected: accountKeyForm.nodeId === node.id }">
              <input v-model="accountKeyForm.nodeId" type="radio" :value="node.id" />
              <span class="node-choice-meta">
                <strong>{{ node.name }}</strong>
                <code>{{ node.publicHost }}</code>
              </span>
              <span class="state on">{{ levelLabel(node.minUserLevel) }}</span>
            </label>
            <div v-if="!accountNodes.length" class="empty-state compact">暂无可用节点。</div>
          </div>
          <div class="account-submit-row">
            <span class="selection-count">已选 {{ accountKeyForm.nodeId ? 1 : 0 }}</span>
            <button type="submit" class="primary" :disabled="!accountKeyForm.nodeId">申请 Key</button>
          </div>

          <div v-if="createdToken" class="token-banner">
            <span>申请成功，请立即保存</span>
            <code @click="copyToken(createdToken)">{{ createdToken }}</code>
          </div>
        </form>

        <section class="panel account-keys">
          <div class="panel-title">
            <h2>活跃隧道列表</h2>
            <span class="muted">{{ accountKeys.length }} 个 Key</span>
          </div>
          <div class="account-key-list">
            <article v-for="key in accountKeys" :key="key.id" class="key-card account-key-row">
              <div class="entity-cell">
                <strong>{{ key.nodeName }}</strong>
                <small>{{ key.publicHost }}</small>
              </div>
              <div class="entity-cell">
                <small>Key</small>
                <strong>{{ key.tokenName }}</strong>
              </div>
              <code @click="copyStoredToken('account', key.id)">{{ key.tokenMasked }}</code>
              <div class="actions">
                <button type="button" @click="copyStoredToken('account', key.id)">复制</button>
                <button class="danger" type="button" @click="deleteAccountKey(key.id)">移除</button>
              </div>
            </article>
            <div v-if="!accountKeys.length" class="empty-state compact">暂无活跃隧道。</div>
          </div>
        </section>
      </div>
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
  { id: "overview", name: "系统实时态势" },
  { id: "nodes", name: "节点资产" },
  { id: "users", name: "账号目录" },
  { id: "levels", name: "等级权益" },
  { id: "keys", name: "Key 凭证" },
  { id: "taxonomy", name: "分组标签" },
  { id: "logs", name: "审计日志" },
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
const currentAdminTabName = computed(() => adminTabs.find((tab) => tab.id === adminTab.value)?.name || "管理后台");
const accountQuotaPercent = computed(() => percent(accountQuota.value.used, accountQuota.value.limit));

onMounted(async () => {
  window.addEventListener("popstate", () => {
    route.value = window.location.pathname;
    routeLoad().catch((err) => flash(err.message));
  });
  await loadLevels();
  await loadMe();
  await routeLoad();
});

watch(adminTab, () => {
  if (page.value === "admin") loadAdmin().catch((err) => flash(err.message));
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
  if (page.value === "home" || page.value === "status") {
    await loadPublicStatus();
  }
  if (page.value === "admin") {
    if (me.value?.role !== "admin") return go("/login");
    await loadAdmin();
  }
  if (page.value === "account") {
    if (me.value?.role !== "user") return go("/login");
    await loadAccount();
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
  users.value = userData.users.map((user) => ({
    ...user,
    editRole: user.role,
    editLevel: user.level || 1,
    editEnabled: user.enabled,
    editPassword: "",
  }));
  levels.value = levelData.levels;
  nodes.value = nodeData.nodes.map((node) => ({
    ...node,
    editMinUserLevel: node.minUserLevel,
    editGroupId: node.groupId || "",
    editTagIds: (node.tags || []).map((tag) => tag.id),
    editEnabled: node.enabled,
  }));
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
  await api(`/api/admin/users/${user.id}`, {
    method: "PUT",
    body: {
      username: user.username,
      displayName: user.displayName,
      role: user.editRole,
      level: user.editLevel,
      enabled: user.editEnabled,
      password: user.editPassword || undefined,
    },
  });
  await loadAdmin();
  flash("账号已保存");
}

async function deleteUser(id) {
  await api(`/api/admin/users/${id}`, { method: "DELETE" });
  await loadAdmin();
  flash("账号已删除");
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
  await api(`/api/admin/nodes/${node.id}`, {
    method: "PUT",
    body: {
      ...node,
      minUserLevel: node.editMinUserLevel,
      groupId: node.editGroupId || "",
      tagIds: node.editTagIds || [],
      enabled: node.editEnabled,
    },
  });
  await loadAdmin();
  flash("节点已保存");
}

async function deleteNode(id) {
  await api(`/api/admin/nodes/${id}`, { method: "DELETE" });
  selectedNodes.value = selectedNodes.value.filter((nodeId) => nodeId !== id);
  await loadAdmin();
  flash("节点已移除");
}

async function bulk(action) {
  if (!selectedNodes.value.length) {
    flash("请先选择节点");
    return;
  }
  await api("/api/admin/nodes/bulk", { method: "POST", body: { nodeIds: selectedNodes.value, action } });
  selectedNodes.value = [];
  await loadAdmin();
  flash("批量操作已完成");
}

async function saveGroup() {
  await api("/api/admin/groups", { method: "POST", body: groupForm.value });
  resetGroupForm();
  await loadAdmin();
  flash("分组已保存");
}

async function deleteGroup(id) {
  await api(`/api/admin/groups/${id}`, { method: "DELETE" });
  await loadAdmin();
  flash("分组已删除");
}

async function saveTag() {
  await api("/api/admin/tags", { method: "POST", body: tagForm.value });
  resetTagForm();
  await loadAdmin();
  flash("标签已保存");
}

async function deleteTag(id) {
  await api(`/api/admin/tags/${id}`, { method: "DELETE" });
  await loadAdmin();
  flash("标签已删除");
}

async function adminCreateKey() {
  const data = await api("/api/admin/keys", { method: "POST", body: adminKeyForm.value });
  createdToken.value = data.token;
  adminKeyForm.value = { accountId: "", nodeId: "" };
  await loadAdmin();
}

async function deleteAdminKey(id) {
  try {
    await api(`/api/admin/keys/${id}`, { method: "DELETE" });
    flash("Key 已撤销");
  } catch (err) {
    flash(err.message);
  } finally {
    await loadAdmin();
  }
}

async function createAccountKey() {
  const data = await api("/api/account/keys", { method: "POST", body: accountKeyForm.value });
  createdToken.value = data.token;
  accountKeyForm.value = { nodeId: "" };
  await loadAccount();
}

async function deleteAccountKey(id) {
  try {
    await api(`/api/account/keys/${id}`, { method: "DELETE" });
    flash("Key 已删除");
  } catch (err) {
    flash(err.message);
  } finally {
    await loadAccount();
  }
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
  try {
    const path = scope === "admin" ? `/api/admin/keys/${keyId}/token` : `/api/account/keys/${keyId}/token`;
    const data = await api(path);
    await copyToken(data.token);
  } catch (err) {
    flash(err.message);
  }
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

function editGroup(group) {
  groupForm.value = { id: group.id, name: group.name, description: group.description || "" };
}

function resetGroupForm() {
  groupForm.value = { name: "", description: "" };
}

function editTag(tag) {
  tagForm.value = { id: tag.id, name: tag.name, color: tag.color || "#4d6b8a" };
}

function resetTagForm() {
  tagForm.value = { name: "", color: "#4d6b8a" };
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

function percent(value, total) {
  const max = Number(total || 0);
  if (!max) return 0;
  return Math.max(0, Math.min(100, (Number(value || 0) / max) * 100));
}

function shortTime(value) {
  if (!value) return "-";
  const time = new Date(value);
  if (Number.isNaN(time.getTime())) return "-";
  return time.toLocaleString();
}

function formatDetail(detail) {
  if (!detail || !Object.keys(detail).length) return "{}";
  return JSON.stringify(detail);
}

function nodeCountByGroup(groupId) {
  return nodes.value.filter((node) => node.groupId === groupId).length;
}

function nodeCountByTag(tagId) {
  return nodes.value.filter((node) => (node.tags || []).some((tag) => tag.id === tagId)).length;
}

function flash(message) {
  toast.value = message;
  setTimeout(() => { toast.value = ""; }, 2200);
}
</script>
