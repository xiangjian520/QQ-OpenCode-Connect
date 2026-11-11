# QQ-OpenCode-Connect

通过 OneBot v11 将 QQ 私聊/群聊连接到 OpenCode v2，并提供一个樱花治愈系的 Web 管理台和独立网页聊天入口。

## 功能

- Java 25 + Spring Boot 4 + Maven
- NapCat 反向 WebSocket：`/onebot/v11/ws`
- 私聊按用户、群聊按群号维护 OpenCode 会话
- 群聊必须 @Bot，私聊和群聊使用双重白名单
- 消息去重、单会话串行任务队列、长回复自动分片
- 默认使用模型 `none` 思考强度，优先回复速度
- 管理员私聊可临时开启 `qq-code` Agent，30 分钟自动关闭
- OpenCode Provider 认证持久化在 Docker 命名卷
- MySQL 数据库、Flyway 数据库迁移
- 原生 HTML/CSS/JavaScript 管理台，无 Node 构建步骤
- 管理员密码登录、CSRF、SameSite Session Cookie
- 网页聊天使用独立会话，不混入 QQ 上下文
- Linux `start.sh` 与 Windows `start.ps1` 启动脚本

## 环境要求

- JDK 25
- Maven 3.9+，或项目 Maven Wrapper
- Docker / Docker Compose
- NapCatQQ 或其他兼容 OneBot v11 的 QQ 客户端

项目已使用 Java 的 MySQL Connector/J。附件中的 Node.js MySQL 连接器不适用于 Java 后端，不参与运行时。

## 快速启动

1. 创建配置：

```bash
cp .env.example .env
```

2. 修改 `.env`，至少设置：
   - `WEB_ADMIN_USERNAME`
   - `WEB_ADMIN_PASSWORD`
   - `DB_PASSWORD`
   - `MYSQL_ROOT_PASSWORD`
   - `OPENCODE_SERVER_PASSWORD`
   - `ONEBOT_TOKEN`
   - 私聊/群聊白名单和管理员 QQ

3. Linux：

```bash
./start.sh
```

Windows PowerShell：

```powershell
.\start.ps1
```

4. 浏览器访问 `http://127.0.0.1:8080`，使用 `.env` 中设置的管理员账号登录。

## Docker 命令

启动 OpenCode 和 MySQL：

```bash
docker compose up -d mysql opencode
```

当前系统 Docker 需要提权时，可以使用 KDE 授权窗口：

```bash
pkexec docker compose up -d mysql opencode
```

首次使用需要登录 OpenCode Provider：

```bash
docker compose exec opencode opencode auth login
docker compose restart opencode
```

也可以运行封装脚本：

```bash
./scripts/opencode-auth.sh
```

停止容器但保留数据：

```bash
docker compose stop mysql opencode
```

查看状态：

```bash
docker compose ps
docker compose logs --tail=100 opencode
```

OpenCode 固定在已联调过的 v2.0.29 镜像摘要。升级镜像后应重新验证 `/api/*` 接口兼容性。

## NapCat 配置

在 NapCat 的 OneBot v11 网络配置中添加反向 WebSocket：

- URL：`ws://127.0.0.1:8080/onebot/v11/ws`；NapCat 在其他容器时使用宿主机可达地址
- Token：与 `.env` 的 `ONEBOT_TOKEN` 一致
- 消息格式：array 或 string 均可解析
- 群聊事件必须包含 @Bot，且群号需要在 Web 设置或 `ONEBOT_ALLOWED_GROUP_IDS` 中

如果 NapCat 与 Bot 不在同一台机器，请勿直接把管理台暴露到公网；建议使用 SSH 隧道或反向代理并启用 HTTPS。

Linux 上 NapCat 在 Docker 中、Bot 在宿主机时，可以选择：

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

然后填写 `ws://host.docker.internal:8080/onebot/v11/ws`。也可以让 NapCat 使用宿主机网络，再填写 `ws://127.0.0.1:8080/onebot/v11/ws`。

## 中文命令

所有命令以 `/` 开头。群聊中必须先 @Bot。

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/帮助` | 白名单用户 | 显示命令说明 |
| `/状态` | 白名单用户 | QQ、OpenCode、模型、队列状态 |
| `/模型` | 白名单用户查看 | 显示可用模型；管理员可 `/模型 序号` 切换 |
| `/思考` | 白名单用户查看 | 管理员可设 `关闭/低/中/高` |
| `/新对话` | 私聊用户或群管理员 | 清空当前上下文 |
| `/停止` | 私聊用户或群管理员 | 中断回复并清空队列 |
| `/编码 开启` | 管理员私聊 | 开启 `qq-code` Agent 30 分钟 |
| `/编码 关闭` | 管理员私聊 | 立即关闭编码模式 |
| `/关于` | 白名单用户 | 项目与版本信息 |

## 关键配置

运行时可直接在管理台修改：

- 私聊白名单
- 群聊白名单
- 管理员 QQ
- 群聊是否必须 @Bot
- 默认思考强度
- 队列上限
- 回复分片长度和间隔
- 聊天记录保留天数

以下敏感配置只从 `.env` 读取：

- Web 管理员密码
- OneBot Token
- OpenCode Basic Auth 密码
- MySQL 密码
- 数据库连接地址

默认数据库监听 `127.0.0.1:3307`，避免与宿主机已有 MySQL 的 `3306` 冲突。使用外部 MySQL 时，将 `.env` 的 `MYSQL_MODE` 改为 `external` 并修改 `DB_URL`。

## 管理员编码模式

编码工作区默认挂载为：

```text
./agent-workspace -> /home/agent/workspace
```

可通过 `OPENCODE_WORKSPACE=/absolute/path` 指定其他目录。编码模式只允许管理员私聊开启，群聊和普通私聊始终使用禁用了文件/Shell 工具的 `qq-chat` Agent。

不要把宿主机敏感目录挂载给 OpenCode。Docker 容器并不等于绝对安全边界。

## 开发与测试

```bash
mvn test
mvn package
```

或：

```bash
./mvnw test
./mvnw package
```

测试覆盖中文命令、OneBot 消息解析、OpenCode API 解析、Basic Auth、长消息分片和设置解析。真实 QQ 联调需要 NapCat 与 QQ 账号。

## 目录

```text
src/main/java/com/xiangjian/qqbot/
  config/       Spring、Security、WebSocket 配置
  domain/       通用消息与会话模型
  onebot/       OneBot v11 反向 WebSocket 与动作发送
  opencode/     OpenCode v2 HTTP/SSE 客户端
  repository/   MySQL 数据访问
  service/      队列、命令、权限、拆分与编排
  web/          管理 API 与 SSE
src/main/resources/
  db/migration/ Flyway 迁移
  static/       管理台前端
```

## License

MIT
