# Ubuntu 部署 LAN Tunnel Relay Station

适用 Ubuntu 20.04/22.04/24.04 及更新版本。中转站不需要 Docker，部署后以 systemd 服务常驻运行。

## 一键安装

直接在 Ubuntu 服务器上执行：

```bash
curl -fsSL https://raw.githubusercontent.com/lxd2study/BlockBridge/main/relay-station/deploy/linux/one-click-install.sh | sudo bash
```

如果已经把 `relay-station` 目录上传到服务器，可直接执行：

```bash
cd relay-station
sudo bash deploy/linux/one-click-install.sh
```

脚本会自动安装依赖和 Go 1.22、构建、安装 systemd、生成节点 API 密码和默认 Token，并启动服务。

安装完成后可以直接使用快捷命令：

```bash
bb-relay status      # 查看服务状态
bb-relay logs        # 实时日志
bb-relay tail 100    # 最近 100 行日志
bb-relay restart     # 重启服务
bb-relay config      # 编辑配置并重启
bb-relay upgrade     # 升级到 main 最新版，保留配置
bb-relay info        # 查看安装路径
```

`bb-relay upgrade` 会先备份 `/etc/lan-tunnel-relay-station/station.json`，再重新执行一键安装流程；默认不会覆盖现有配置。

建议指定管理端服务器 IP，让脚本自动配置 UFW 仅允许管理端访问节点 API：

```bash
curl -fsSL https://raw.githubusercontent.com/lxd2study/BlockBridge/main/relay-station/deploy/linux/one-click-install.sh | sudo env MANAGER_IP=管理服务器公网IP OPEN_UFW=1 bash
```

常用参数：

```bash
curl -fsSL https://raw.githubusercontent.com/lxd2study/BlockBridge/main/relay-station/deploy/linux/one-click-install.sh | sudo env API_PORT=8080 CONTROL_PORT=25566 PUBLIC_MIN=25565 PUBLIC_MAX=25665 bash
curl -fsSL https://raw.githubusercontent.com/lxd2study/BlockBridge/main/relay-station/deploy/linux/one-click-install.sh | sudo env OVERWRITE_CONFIG=1 bash
curl -fsSL https://raw.githubusercontent.com/lxd2study/BlockBridge/main/relay-station/deploy/linux/one-click-install.sh | sudo env SHORTCUT_NAME=bb-station bash
curl -fsSL https://raw.githubusercontent.com/lxd2study/BlockBridge/main/relay-station/deploy/linux/one-click-install.sh | sudo env INSTALL_SHORTCUT=0 bash
```

安装完成后，终端会打印在 `relay-manager` 添加节点时要填写的 API 地址、账号、密码，以及 Mod 端访问令牌。

## 1. 安装基础工具

```bash
sudo apt update
sudo apt install -y ca-certificates curl tar
```

需要 Go 1.22 或更新版本：

```bash
go version
```

如果系统没有 Go，或版本低于 1.22，请安装新版 Go。可以用系统包源、第三方包源，或从 Go 官方下载 Linux amd64 tar 包后安装到 `/usr/local/go`。安装后确认：

```bash
go version
```

## 2. 上传项目

把本机的 `relay-station` 目录上传到服务器，例如：

```bash
scp -r relay-station user@your-server:/tmp/relay-station
```

登录服务器：

```bash
ssh user@your-server
cd /tmp/relay-station
```

## 3. 构建二进制

```bash
chmod +x scripts/build.sh
./scripts/build.sh
```

构建结果：

```text
bin/relay-station
```

## 4. 安装为 systemd 服务

```bash
sudo bash deploy/linux/install-systemd.sh
```

脚本会安装到：

```text
/opt/lan-tunnel-relay-station/relay-station
/etc/lan-tunnel-relay-station/station.json
/etc/systemd/system/lan-tunnel-relay-station.service
```

## 5. 修改配置

```bash
sudo nano /etc/lan-tunnel-relay-station/station.json
```

至少修改这些字段：

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
      "token": "换成很长的随机令牌",
      "enabled": true
    }
  ]
}
```

`tokens[0].token` 要填写到 Minecraft mod 配置页的“访问令牌”。

节点 ID、名称、区域、公网地址等信息不在 relay-station 配置，去 `relay-manager` 添加节点时填写。

## 6. 放行端口

云服务器安全组必须放行：

```text
TCP 25566
TCP 25565-25665
TCP 8080，仅允许 relay-manager 服务器访问
```

如果启用了 UFW：

```bash
sudo ufw allow 25566/tcp
sudo ufw allow 25565:25665/tcp
sudo ufw allow from 管理服务器公网IP to any port 8080 proto tcp
sudo ufw reload
```

如果之前已经开放了全部来源访问 8080，建议删除：

```bash
sudo ufw delete allow 8080/tcp
```

## 7. 启动服务

```bash
sudo systemctl start lan-tunnel-relay-station
sudo systemctl status lan-tunnel-relay-station
```

设置已由安装脚本自动启用开机自启。查看日志：

```bash
journalctl -u lan-tunnel-relay-station -f
```

## 8. 在 relay-manager 添加节点

relay-station 节点不再提供 Web 页面。去 `relay-manager` 管理后台添加节点：

```text
节点 API：http://节点公网IP:8080
账号：node-api
密码：station.json 里的 api.password
公网地址：节点公网IP或域名
```

## 9. Mod 端填写

Minecraft 的 LAN Tunnel 配置页填写：

```text
中转服务器地址：你的公网IP或域名
控制端口：25566
访问令牌：station.json 中配置的 token
公网端口：0 自动分配，或填写 25565-25665 内未占用且非控制/API 的端口
```

进入单人世界并“对局域网开放”后，`relay-manager` 管理后台会显示在线隧道，朋友连接管理后台显示的分享地址即可。

## 常用维护命令

```bash
sudo systemctl restart lan-tunnel-relay-station
sudo systemctl stop lan-tunnel-relay-station
sudo systemctl status lan-tunnel-relay-station
journalctl -u lan-tunnel-relay-station -n 100 --no-pager
```
