# sb-panel

一个只面向管理员的小型 sing-box Docker 容器控制面板。它不改动原 `sing-box-main` 项目，只通过 DockerJava 创建、启动、停止和读取容器状态。

## 功能

- Spring Boot + Thymeleaf + SQLite。
- 简单账号密码登录，订阅接口 `/s/{token}/...` 单独放行。
- 每个用户一个 Docker 容器，随机探测可用端口块并映射 TCP/UDP。
- 默认 Argo 临时隧道模式：`ARGO_DOMAIN` 和 `ARGO_AUTH` 为空时沿用原镜像 quick tunnel 行为。
- 系统配置可修改创建容器时的默认参数，包括镜像、端口范围、协议、CDN、Server IP、Argo、额外环境变量和默认额度。
- 默认开启 `fscarmen/sb` 兼容修补：当前上游镜像可能生成 sing-box 1.14 配置但下载 1.13 核心，面板会在容器启动前移除不兼容字段；上游修复后可在系统配置里关闭。
- 每个容器可单独修改流量上限、到期时间和订阅开关，不需要重启。
- 使用 Docker stats 统计容器网络流量，到期或超流量后停止容器并保留。
- 审计日志记录创建、启动、停止、重启、删除、配置变更、到期、限流和监控异常。

## 构建

```bash
mvn clean package
```

生成文件：

```text
target/sb-panel-0.1.0.jar
```

## 运行

面板需要和 Docker 在同一台 VPS 上运行。生产环境建议把 `PANEL_ADMIN_PASSWORD`、`PANEL_PUBLIC_BASE_URL` 和 `PANEL_DB_PATH` 显式设置好。Linux 上默认会自动探测 `/var/run/docker.sock`，也可以用 `PANEL_DOCKER_HOST` 显式指定。

```bash
export PANEL_PORT=8080
export PANEL_ADMIN_USERNAME=admin
export PANEL_ADMIN_PASSWORD='change-this-password'
export PANEL_PUBLIC_BASE_URL='http://YOUR_PUBLIC_IP:8080'
export PANEL_DB_PATH='/opt/sb-panel/sb-panel.db'
export PANEL_LOG_FILE='/opt/sb-panel/logs/sb-panel.log'
export PANEL_DOCKER_HOST='unix:///var/run/docker.sock'

java -jar /opt/sb-panel/sb-panel-0.1.0.jar
```

默认登录地址：

```text
http://YOUR_PUBLIC_IP:8080/login
```

## VPS 初始化脚本

`scripts/init-vps.sh` 用于空 Debian/Ubuntu VPS：安装 Docker、OpenJDK 17、开启 BBR、关闭 Docker `userland-proxy`、下载面板 jar、写入 systemd 服务并启动。

推荐把 jar 放到 GitHub Releases，使用 release asset 直链：

```bash
curl -fsSLO https://raw.githubusercontent.com/YOUR_NAME/YOUR_REPO/main/scripts/init-vps.sh
chmod +x init-vps.sh

PANEL_JAR_URL='https://github.com/YOUR_NAME/YOUR_REPO/releases/latest/download/sb-panel-0.1.0.jar' \
PANEL_ADMIN_PASSWORD='change-this-password' \
PANEL_PORT=18080 \
./init-vps.sh
```

如果不传 `PANEL_ADMIN_PASSWORD`，脚本会自动生成一个并保存到 `/opt/sb-panel/admin-password.txt`。

## systemd 示例

```ini
[Unit]
Description=sb-panel
After=network-online.target docker.service
Wants=network-online.target docker.service

[Service]
User=root
WorkingDirectory=/opt/sb-panel
Environment=PANEL_PORT=8080
Environment=PANEL_ADMIN_USERNAME=admin
Environment=PANEL_ADMIN_PASSWORD=change-this-password
Environment=PANEL_PUBLIC_BASE_URL=http://YOUR_PUBLIC_IP:8080
Environment=PANEL_DB_PATH=/opt/sb-panel/sb-panel.db
Environment=PANEL_LOG_FILE=/opt/sb-panel/logs/sb-panel.log
Environment=PANEL_DOCKER_HOST=unix:///var/run/docker.sock
ExecStart=/usr/bin/java -jar /opt/sb-panel/sb-panel-0.1.0.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启用：

```bash
sudo mkdir -p /opt/sb-panel/logs
sudo cp target/sb-panel-0.1.0.jar /opt/sb-panel/
sudo systemctl daemon-reload
sudo systemctl enable --now sb-panel
```

## 使用备注

- 首次进入“系统配置”后先修改 `Server IP`、端口范围和管理员密码对应的环境变量。
- “系统配置”只影响新建容器；到期时间、流量上限、订阅开关属于数据库字段，可在容器详情页单独修改。
- 面板订阅链接是稳定链接，格式为 `/s/{token}/auto`，会根据客户端 UA 映射到原容器生成的订阅文件。
- Docker stats 是轻量统计方式，适合少量朋友使用，不是运营级计费系统。
