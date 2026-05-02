BlockBridge
===========

项目简介
--------

BlockBridge 是一个面向 Minecraft Forge 1.20.1 的局域网联机穿透项目。它可以在单人世界“对局域网开放”后，通过公网中转服务把本地 LAN 端口发布到外网，让不同网络环境下的朋友直接连接 `公网IP:端口` 加入游戏。

项目包含客户端 mod、公网中转节点和统一管理后台，适合没有公网 IP、无法设置路由器端口映射，或希望快速组织多人联机的玩家使用。

联系方式
--------

QQ：2966918467

组成
----

1. Forge 客户端 mod：`src/main/java/com/lxd/lantunnel`
2. 公网中转节点：`relay-station`，Go 独立项目，负责 TCP 转发和节点 API
3. 统一管理后台：`relay-manager`，Node.js + Vue + SQLite 独立项目，用于管理多个中转节点、用户等级和 Key

工作方式
--------

主机玩家只需要能主动访问公网服务器，不需要在家用路由器上做端口映射。

1. 玩家在 Minecraft 中打开单人世界并启用“对局域网开放”。
2. mod 检测到本地 LAN 端口后，主动连接公网中转服务的控制端口。
3. 朋友连接公网中转服务暴露的端口。
4. 中转服务通知 mod 建立数据连接，并把朋友的 TCP 流量转发到本机 `127.0.0.1:<LAN端口>`。

构建
----

```powershell
.\gradlew build
```

mod jar 输出在：

```text
build/libs/lan_tunnel-0.1.0.jar
```

中转站二进制默认输出在：

```text
relay-station/bin/relay-station
```

公网服务器部署
--------------

公网中转节点是独立 Go 项目，不随 Gradle 构建。安装 Go 1.22+ 后：

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
```

云服务器安全组/防火墙需要放行控制端口和公网转发端口范围。

中转节点 API 默认监听：

```text
http://服务器IP:8080
```

账号密码在 `relay-station/config/station.json` 的 `api` 配置里。多节点统一管理请部署 Node.js 版 `relay-manager`，默认监听 `8090`，它会通过每个节点的 `8080` API 拉取状态并下发 Key/关闭隧道操作。公网部署时建议只允许管理服务器 IP 访问各节点的 8080。

管理后台部署：

```bash
cd relay-manager
npm install
npm run build
npm run start
```

mod 内配置
----------

在 Minecraft 的 Mods 列表中选择 BlockBridge，然后点击配置按钮。

配置项：

```text
启用中转穿透：是否启用
开启局域网后自动启动：Open to LAN 后自动连接中转
允许离线玩家加入：关闭正版登录验证，允许离线模式玩家加入本机局域网世界
中转服务器地址：公网 IP 或域名
控制端口：默认 25566
访问令牌：必须和 relay-station/config/station.json 的 tokens 中一项一致
公网端口：想暴露给朋友的端口；填 0 表示让中转服务自动分配
重连间隔秒：控制连接断开后的重连等待时间
```

启用后，在世界里点击“对局域网开放”，配置页会显示可分享地址。朋友在多人游戏中直接连接该地址即可。
