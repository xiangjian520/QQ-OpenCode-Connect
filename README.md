# QQ-OpenCode-Connect

通过 OneBot v11 将 QQ 私聊/群聊连接到 OpenCode v2，并提供一个樱花治愈系的 Web 管理台和独立网页聊天入口。

## 功能

- Java 25 + Spring Boot 4 + Maven
- NapCat 反向 WebSocket：`/onebot/v11/ws`
- 私聊按用户、群聊按群号维护 OpenCode 会话
- 群聊必须 @Bot，私聊和群聊使用双重白名单
- OneBot Token、白名单、管理员和 @Bot 规则可直接在网页端维护
- OpenCode 默认模型与思考强度拥有独立设置页
- 可自定义 Provider/Model、显示名称和支持的思考强度，并提供给网页聊天与 QQ `/模型`
- 消息去重、单会话串行任务队列、长回复自动分片
- 默认使用模型 `none` 思考强度，优先回复速度
- 管理员私聊可临时开启 `qq-code` Agent，30 分钟自动关闭
- 32 项安全娱乐中心：运势、塔罗图鉴、抽签、小猪、今日老婆、星座、文字抽签、反应表情、Emoji 合成、井字棋、猜数字、签到、提醒、投票与 ACG 查询
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
   - `ONEBOT_TOKEN`（作为数据库尚未保存 Token 时的初始值）
   - 可选的私聊/群聊白名单和管理员 QQ 初始值

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
- Token：与网页端“OneBot”页面保存的 Token 一致；全新安装时先使用 `.env` 的 `ONEBOT_TOKEN`
- 消息格式：array 或 string 均可解析
- 群聊事件必须包含 @Bot，且群号需要在网页端 OneBot 设置中启用

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
| `/娱乐` | 白名单用户 | 查看安全娱乐命令 |
| `/关于` | 白名单用户 | 项目与版本信息 |

娱乐命令只在管理台“运行设置”启用安全娱乐时响应：

| 分类 | 命令 | 说明 |
| --- | --- | --- |
| 日常 | `/运势` | 每天一份轻松小运势 |
| 日常 | `/塔罗` | 抽取一张带独立图面的 22 张大阿卡那牌 |
| 日常 | `/抽签` | 抽一张轻松向赛博御神签 |
| 日常 | `/小猪` | 每日小猪与安全表情素材 |
| 日常 | `/今日老婆` | 安全的虚拟二次元人设签 |
| 日常 | `/星座 狮子` | 十二星座日签 |
| 文字 | `/笑话`、`/冷知识`、`/名言`、`/梗` | 内置安全短内容 |
| 文字 | `/夸夸 [对象]`、`/安慰` | 夸赞和温柔安慰 |
| 随机 | `/选择 A|B|C`、`/转盘 A|B|C` | 从候选项中做选择 |
| 随机 | `/硬币`、`/骰子 [面数]` | 硬币和 2-1000 面骰子 |
| 游戏 | `/井字棋 [1-9]`、`/猜数字 [1-100]` | 持久化小游戏 |
| 表情 | `/表情 [关键词]` | 从 23 张安全反应表情中选择 |
| 表情 | `/合成 😀 🐱` | 离线索引 Emoji Kitchen 组合并发送合成图 |
| ACG | `/天气 上海` | 查询城市天气 |
| ACG | `/番剧 关键词`、`/新番` | Bangumi 番剧搜索和今日放送 |
| ACG | `/B站热门`、`/Steam 关键词`、`/点歌 关键词` | B 站、Steam 与网易云搜索 |
| 社交 | `/词云` | 根据当前会话消息生成高频词云文本 |
| 社交 | `/签到`、`/我的积分`、`/积分榜` | 每日积分系统 |
| 社交 | `/提醒 10分钟 喝水` | 持久化定时提醒，重启不丢失 |
| 社交 | `/投票 发起 问题|选项1|选项2` | 创建投票；之后用 `/投票 编号` 参与 |

娱乐模块不包含 NSFW、成人图源、色情搜索或露骨内容。井字棋、猜数字、签到、提醒和投票状态保存在 MySQL，重启后可继续。外部 ACG 查询使用公开接口，接口限流时会返回可重试的中文提示。

## 关键配置

运行时可直接在管理台修改：

- OneBot WebSocket Token（界面只显示掩码，可一键重新生成）
- 私聊白名单
- 群聊白名单
- 管理员 QQ
- 群聊是否必须 @Bot
- OpenCode 默认模型
- 默认思考强度
- 队列上限
- 回复分片长度和间隔
- 聊天记录保留天数
- 安全娱乐开关

以下启动与基础设施配置从 `.env` 读取：

- Web 管理员密码
- OpenCode Basic Auth 密码
- MySQL 密码
- 数据库连接地址

OneBot Token 首次从 `.env` 的 `ONEBOT_TOKEN` 初始化；在网页端保存后以数据库值为准。修改 Token 会立即作用于新的 WebSocket 握手，NapCat 需要同步更新。

### 自定义模型

在“OpenCode 模型”页面填写 Provider ID、Model ID、显示名称和可选思考强度，即可把 OpenCode 模型目录中未自动列出的模型加入选择器。支持的自定义思考强度为 `none`、`minimal`、`low`、`medium`、`high`、`xhigh`、`max`。

自定义模型会被网页聊天、网页默认模型、对话模型切换和 QQ `/模型` 命令共同使用。项目只保存模型标识，不保存 Provider API Key；对应 Provider 仍需先在 OpenCode 容器中完成配置和认证。

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

测试覆盖中文命令、OneBot 消息解析、OpenCode API 解析、Basic Auth、长消息分片、动态设置、娱乐安全边界和游戏状态引擎。真实 QQ 联调需要 NapCat 与 QQ 账号。

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
  entertainment/ 离线 Emoji Kitchen 组合索引
  static/       管理台前端、塔罗牌面、小猪和反应表情素材
```

## License

项目代码使用 MIT License。`src/main/resources/static/entertainment/` 中的小猪、塔罗和反应表情素材，以及 `src/main/resources/entertainment/emoji-kitchen.tsv.gz` 组合索引来自 Eridanus，按 CC BY-NC-SA 4.0 仅限非商业使用，不属于 MIT 授权范围。详见 `THIRD_PARTY_NOTICES.md`、完整许可证和素材目录内的 `LICENSE.txt`。
