# LAN Tunnel Relay Station

这是独立的 Go 版公网中转节点。它和 Forge mod 工程解耦，一个二进制文件只负责节点侧能力：

- Minecraft LAN TCP 中转
- 节点 HTTP 管理 API
- 访问令牌和在线隧道 API，供 `relay-manager` 统一管理

mod 端协议保持兼容，不需要改客户端 mod。

节点侧不再内置 Web 页面。所有节点信息查看、令牌配置、隧道关闭、节点添加/删除，都在 `../relay-manager` 中统一完成。`relay-manager` 会通过每个节点的 `api.bind` 地址调用节点 API。

## 构建

需要 Go 1.22 或更新版本。

```bash
cd relay-station
go build -o bin/relay-station .
```

Windows:

```powershell
cd relay-station
go build -o bin\relay-station.exe .
```

## 配置

复制并修改：

```text
config/station.json
```

关键字段：

```json
{
  "relay": {
    "bind": "0.0.0.0",
    "controlPort": 25566,
    "publicMin": 25565,
    "publicMax": 25665,
    "connectTimeoutMillis": 15000,
    "pendingClientTimeoutMillis": 15000,
    "maxTunnelsPerToken": 1,
    "maxConcurrentStreamsPerToken": 32,
    "maxPendingClientsPerToken": 32
  },
  "api": {
    "bind": "0.0.0.0:8080",
    "username": "node-api",
    "password": "change-this-node-api-password"
  }
}
```

节点 ID、名称、区域、公网地址等节点信息都在 `relay-manager` 中配置。`tokens` 是节点初始令牌；后续令牌也可以在 `relay-manager` 中统一添加和删除。

连接限制说明：

- `maxTunnelsPerToken`：单个 Token 同时可发布的隧道数，当前建议保持 1。
- `maxConcurrentStreamsPerToken`：单个 Token 的最大实时玩家连接数。
- `maxPendingClientsPerToken`：等待 Mod 建立数据连接的最大排队数。
- `pendingClientTimeoutMillis`：公网玩家连接等待数据通道的超时时间。

## 运行

```bash
./bin/relay-station --config config/station.json
```

Windows:

```powershell
.\bin\relay-station.exe --config .\config\station.json
```

节点 API 默认监听 `0.0.0.0:8080`，仅供 `relay-manager` 调用，不提供本地 Web 页面。账号密码来自 `api.username` 和 `api.password`。

## 端口

云服务器安全组和系统防火墙需要放行：

```text
TCP 25566
TCP 25565-25665
TCP 8080，只允许 relay-manager 服务器访问
```

生产环境建议只允许 `relay-manager` 服务器 IP 访问 `8080`。

## API

所有 API 都使用 Basic Auth，账号密码来自 `api.username` 和 `api.password`。

```text
GET  /api/health
GET  /api/status
GET  /api/tunnels
GET  /api/tokens
POST /api/tokens
GET  /api/tokens/:name/usage
DELETE /api/tokens/:name
POST /api/tunnels/:name/close
```

Mod 协议支持：

```text
HOST <token> <requestedPublicPort> <clientVersion>
TEST <token> <clientVersion>
DATA <token> <connectionId>
PING <timestamp>
PONG <timestamp>
ERR <code> <message>
```

## Linux systemd

构建后把 `relay-station` 目录上传到服务器，再执行：

```bash
sudo bash deploy/linux/install-systemd.sh
sudo nano /etc/lan-tunnel-relay-station/station.json
sudo systemctl start lan-tunnel-relay-station
sudo systemctl status lan-tunnel-relay-station
```

日志：

```bash
journalctl -u lan-tunnel-relay-station -f
```

Ubuntu 的完整部署步骤见：

```text
deploy/ubuntu/README.md
```

宝塔 Linux 面板部署步骤见：

```text
deploy/baota/README.md
```
