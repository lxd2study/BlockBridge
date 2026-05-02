# 宝塔 Linux 面板部署 relay-manager

`relay-manager` 现在是 Node.js + Vue + SQLite 单服务项目。宝塔只需要启动一个 Node 项目，Vue 页面由 Node 服务托管。

## 1. 安装 Node.js

需要 Node.js 20 或更新版本：

```bash
node -v
npm -v
```

如果没有 Node，请在宝塔的软件商店或运行环境里安装 Node.js 20+。

如果 `npm install` 编译 SQLite 依赖失败，先安装构建工具：

```bash
sudo apt update
sudo apt install -y build-essential python3 make g++
```

## 2. 构建

```bash
cd /www/wwwroot/mc_lan/relay-manager
chmod +x scripts/build.sh
./scripts/build.sh
```

构建完成后会生成：

```text
/www/wwwroot/mc_lan/relay-manager/public/index.html
```

## 3. 修改配置

```bash
nano /www/wwwroot/mc_lan/relay-manager/config/manager.json
```

至少修改：

```json
{
  "bind": "0.0.0.0:8090",
  "security": {
    "secret": "换成至少24位随机密钥",
    "sessionDays": 7
  },
  "initialAdmin": {
    "username": "admin",
    "password": "换成管理后台强密码",
    "displayName": "系统管理员"
  }
}
```

## 4. 宝塔 Node 项目填写

```text
项目名称：
relay-manager

项目目录：
/www/wwwroot/mc_lan/relay-manager

启动文件：
server/index.js

项目端口：
8090

执行命令：
node /www/wwwroot/mc_lan/relay-manager/server/index.js --config /www/wwwroot/mc_lan/relay-manager/config/manager.json

运行用户：
www

开机启动：
开启
```

不要再填写 Go 二进制，也不要把 `scripts/build.sh` 当成常驻启动命令。

## 5. 放行端口

管理端服务器放行：

```text
8090/tcp
```

每台 `relay-station` 节点需要让管理端访问节点 API 端口，默认：

```text
8080/tcp，仅允许管理端服务器 IP 访问
```

## 6. 首次登录

打开：

```text
http://管理服务器IP:8090
```

使用 `config/manager.json` 中的 `initialAdmin` 登录。登录后可创建用户、配置等级 Key 上限、添加节点并设置最低可用等级。
