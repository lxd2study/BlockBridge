# BlockBridge

BlockBridge 是一个面向 Minecraft Forge 1.20.1 的局域网联机穿透项目。主机玩家在单人世界点击“对局域网开放”后，Mod 会通过公网中转节点发布本地 LAN 端口，让不同网络环境下的朋友直接连接 `公网地址:端口` 加入游戏。

项目保留内部 `mod_id=lan_tunnel` 以兼容旧配置，展示名使用 BlockBridge。

联系方式：QQ 2966918467

## 用户使用

1. 安装 Forge 1.20.1 和 BlockBridge Mod。
2. 在 Minecraft 的 Mods 列表中打开 BlockBridge 配置页。
3. 填写中转服务器地址、控制端口、访问令牌和公网端口。
4. 点击“测试”确认节点和 Token 可用。
5. 进入单人世界，点击“对局域网开放”。
6. 配置页会显示“分享地址”，也可以点击“复制地址”发给朋友。

配置项：

```text
启用中转穿透：是否启用 BlockBridge
开启局域网后自动启动：Open to LAN 后自动连接中转
允许离线玩家加入：关闭本地 LAN 世界正版验证，允许离线玩家加入
游戏内显示中转延迟：右上角显示中转延迟、活跃连接数和简短异常原因
自动选择低延迟节点：多节点配置时自动选择可连接且延迟最低的节点
中转服务器地址：自建或官方节点的公网 IP/域名
控制端口：默认 25566
访问令牌：必须和中转节点配置或管理后台分配的 Token 一致
公网端口：填 0 表示由节点自动分配
重连间隔秒：控制连接断开后的重连等待时间
测试超时秒：连接测试和节点探测超时
```

配置文件位于：

```text
.minecraft/config/lan_tunnel.properties
```

新增配置字段：

```properties
relayNodes=default,example.com,25566,false
autoSelectNode=true
connectionTestTimeoutSeconds=5
```

旧字段 `relayHost`、`relayControlPort`、`token`、`requestedPublicPort` 仍然可用。

## 服务端部署

公网中转节点在 `relay-station`，是独立 Go 项目。

Windows:

```powershell
cd relay-station
go build -o bin\relay-station.exe .
.\bin\relay-station.exe --config .\config\station.json
```

Linux:

```bash
cd relay-station
go build -o bin/relay-station .
./bin/relay-station --config config/station.json
```

默认端口：

```text
控制端口：25566
公网转发端口范围：25565-25665
节点 API：8080
```

云服务器安全组和系统防火墙需要放行控制端口和公网转发端口范围。生产环境建议只允许管理后台服务器访问 `8080` API。

关键中转限制配置：

```json
{
  "relay": {
    "connectTimeoutMillis": 15000,
    "pendingClientTimeoutMillis": 15000,
    "maxTunnelsPerToken": 1,
    "maxConcurrentStreamsPerToken": 32,
    "maxPendingClientsPerToken": 32
  }
}
```

管理后台在 `relay-manager`，用于统一管理节点、账号、Key、Token 使用情况、连接历史和节点健康：

```bash
cd relay-manager
npm install
npm run build
npm run start
```

## 开发构建

客户端 Mod：

```powershell
.\gradlew build
```

输出：

```text
build/libs/lan_tunnel-0.1.0.jar
```

中转站：

```bash
cd relay-station
go build ./...
```

管理后台：

```bash
cd relay-manager
npm run build
```

## 协议与接口

Relay 控制协议：

```text
HOST <token> <requestedPublicPort> <clientVersion>
TEST <token> <clientVersion>
DATA <token> <connectionId>
PING <timestamp>
PONG <timestamp>
ERR <code> <message>
```

错误码示例：

```text
TOKEN_REJECTED
PORT_BIND_FAILED
LIMIT_EXCEEDED
BAD_REQUEST
NODE_UNAVAILABLE
```

节点 API：

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

## 当前方向

BlockBridge 当前优先级是公开发布质量、联机稳定性和可诊断性。下一阶段会继续完善官方节点目录、更多节点自动选择策略、发布包和 Modrinth/GitHub Release 流程。
