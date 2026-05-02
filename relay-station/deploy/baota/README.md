# 宝塔 Linux 面板部署说明

这个中转站是一个常驻 Go 服务。宝塔 Go 项目里不要把 `scripts/build.sh` 填成运行文件；`build.sh` 只负责编译。

## 推荐目录

假设项目放在：

```text
/www/wwwroot/mc_lan
```

目录里应包含：

```text
/www/wwwroot/mc_lan/relay-station
```

## 1. 进入服务器终端构建

宝塔终端或 SSH 执行：

```bash
cd /www/wwwroot/mc_lan/relay-station
chmod +x scripts/build.sh
./scripts/build.sh
```

构建完成后会生成：

```text
/www/wwwroot/mc_lan/relay-station/bin/relay-station
```

## 2. 修改配置

```bash
nano /www/wwwroot/mc_lan/relay-station/config/station.json
```

至少修改：

```json
{
  "api": {
    "bind": "0.0.0.0:8080",
    "username": "node-api",
    "password": "换成节点API强密码"
  },
  "tokens": [
    {
      "name": "default",
      "token": "换成给mod填写的长随机令牌",
      "enabled": true
    }
  ]
}
```

节点名称、区域、公网地址在 `relay-manager` 添加节点时填写，不在 relay-station 里配置。

## 3. 宝塔 Go 项目填写

截图里的配置建议这样填：

```text
项目执行文件：
/www/wwwroot/mc_lan/relay-station/bin/relay-station

项目名称：
relay-station

项目端口：
8080

执行命令：
/www/wwwroot/mc_lan/relay-station/bin/relay-station --config /www/wwwroot/mc_lan/relay-station/config/station.json

运行用户：
www

开机启动：
开启
```

说明：

- `项目端口` 填节点 API 端口 `8080`。
- mod 连接用的是控制端口 `25566`，它不是宝塔 Go 项目的项目端口。
- 朋友进入游戏用的是公网端口范围 `25565-25665`。
- 如果你把 `api.bind` 改成 `0.0.0.0:7889`，那宝塔项目端口也要填 `7889`，并且在 `relay-manager` 添加节点时填写 `http://114.134.185.221:7889`。

## 4. 宝塔安全放行

宝塔面板和云服务器安全组都要放行：

```text
25566/tcp
25565-25665/tcp
8080/tcp
```

如果你继续用截图里的 `7889` 做节点 API，则放行：

```text
7889/tcp
```

## 5. 在 relay-manager 添加节点

relay-station 不再提供 Web 页面。默认配置下，在 `relay-manager` 中添加节点时填写：

```text
节点 API：http://114.134.185.221:8080
账号：node-api
密码：station.json 里的 api.password
公网地址：114.134.185.221
```

如果节点 API 端口改成 `7889`：

```text
http://114.134.185.221:7889
```

## 6. Mod 端填写

Minecraft mod 配置页：

```text
中转服务器地址：114.134.185.221
控制端口：25566
访问令牌：station.json 里的 token
公网端口：25565，或 0 自动分配
```

启动成功后，宝塔 Go 项目状态应为运行中；节点信息和在线隧道统一在 `relay-manager` 后台查看。
