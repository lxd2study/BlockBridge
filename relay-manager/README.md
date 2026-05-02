# LAN Tunnel Relay Manager

`relay-manager` 是独立的 Node.js + Vue + SQLite 管理平台，用来统一管理多个 Go 版 `relay-station` 中转节点。它不处理 Minecraft TCP 转发流量，只通过节点 HTTP API 管理节点、用户、Key、监控和日志。

## 功能

- 公开首页、赞助订阅预留页、登录页、管理员后台、用户中心。
- 管理员和用户两类账号。
- 用户固定一级、二级、三级，等级名称、说明、Key 上限和 SVG 标徽颜色可配置。
- 节点支持最低可用等级、分组、标签和批量操作。
- 用户只能看到自己等级可用的节点，并按等级配额生成 Key。
- Key 保存在管理端 SQLite，创建/删除时同步到对应 `relay-station`。
- 每分钟采样节点状态，展示流量和连接趋势。
- 登录、用户、等级、节点、Key 等关键操作写入审计日志。

## 构建

需要 Node.js 20+。如果 SQLite 原生依赖安装失败，Ubuntu 先安装 `build-essential python3 make g++`。

```bash
cd relay-manager
npm install
npm run build
```

## 配置

编辑：

```text
config/manager.json
```

重要字段：

```json
{
  "bind": "0.0.0.0:8090",
  "databasePath": "data/manager.sqlite",
  "security": {
    "secret": "换成至少 24 位的随机密钥",
    "sessionDays": 7
  },
  "initialAdmin": {
    "username": "admin",
    "password": "换成管理后台强密码",
    "displayName": "系统管理员"
  }
}
```

首次启动时，如果数据库里没有管理员账号，会用 `initialAdmin` 创建一个管理员。

## 运行

```bash
npm run start
```

或使用绝对路径：

```bash
node /www/wwwroot/mc_lan/relay-manager/server/index.js --config /www/wwwroot/mc_lan/relay-manager/config/manager.json
```

访问：

```text
http://管理服务器IP:8090
```

## 和 relay-station 的关系

`relay-station` 继续使用 Go，不需要改节点 API。每台节点服务器只需要让 `relay-manager` 能访问它的节点 API，例如：

```text
http://节点公网IP:8080
```

生产环境建议防火墙只允许管理端服务器访问节点 API 端口。

## 宝塔 Linux 面板

见：

```text
deploy/baota/README.md
```
