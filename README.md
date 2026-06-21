# ms-im-message — IM 即时通讯系统

一款支持**单聊**与**群聊**的即时通讯产品，基于 Java + Spring Cloud 微服务架构，Netty 长连接网关。

## 核心特性

- 💬 **单聊 / 群聊** — 读写混合扩散（小群写扩散、大群读扩散）
- 📱 **多端漫游** — 消息云端永久存储，多设备全量 + 增量同步
- 🖼️ **富媒体消息** — 文本 / 图片 / 文件 / 语音
- ✅ **已读回执** — 消息已读/未读状态
- 🟢 **在线状态** — 在线 / 离线 / 输入中
- 🔔 **离线推送** — APNs / FCM / 厂商通道

## 技术栈

| 类别 | 选型 |
|---|---|
| 微服务框架 | Spring Boot 3 + Spring Cloud 2023 |
| 注册/配置中心 | Nacos |
| 长连接 | Netty（WebSocket over TLS） |
| 网关 | Spring Cloud Gateway |
| 存储 | MySQL 8（消息表分库分表 / ShardingSphere） |
| 缓存/状态 | Redis Cluster |
| 消息队列 | Kafka |
| 对象存储 | MinIO / OSS |
| 可观测 | SkyWalking + Prometheus + Grafana + ELK |

## 目标规模

十万 ~ 百万级在线用户。微服务化设计，分阶段落地。

## 文档导航

| 文档 | 内容 |
|---|---|
| [01-架构总览](docs/01-architecture.md) | 整体架构图、分层设计、设计原则 |
| [02-微服务划分](docs/02-services.md) | 各服务职责、边界、依赖 |
| [03-数据模型](docs/03-data-model.md) | MySQL（含消息分表）/ Redis 数据结构 |
| [04-消息流程](docs/04-message-flow.md) | 单聊/群聊/多端同步时序图，可靠性设计 |
| [05-落地路线](docs/05-roadmap.md) | P0~P3 分阶段计划、技术选型说明 |
| [06-P1设计与计划](docs/06-p1-design.md) | P1（单聊完整）可执行设计、数据模型、任务拆解 |

## 项目状态

🟢 **P0 已完成** — 单聊文本最小闭环（im-connect + im-user + im-message）可运行，并已补会话列表、历史拉取、未读、批量用户信息。

🟡 **P1 进行中（设计就绪）** — 离线/多端漫游、已读回执、富媒体、统一鉴权，详见 [P1 设计与计划](docs/06-p1-design.md)。

快速跑通见 [P0 运行指南](docs/P0-getting-started.md)：`docker compose up -d` 起中间件 → 构建 → 启动三个服务 → 打开 `test-client.html` 两端互发。
