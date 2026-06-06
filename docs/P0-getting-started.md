# P0 运行指南

P0 实现单聊文本最小闭环：两个浏览器客户端登录后互发文本，实时收到。

## 架构（P0 实际链路）

```
浏览器A ──WS──► im-connect ──Feign──► im-message ──落库──► MySQL
                   ▲                      │
                   │                      └──Kafka(下行)──┐
                   └──────────推送────────────────────────┘
浏览器B ──WS──► im-connect ─(消费下行,推给B)
```

- **im-user** (8081)：登录、签发 JWT
- **im-message** (8082)：收发、conv_seq(Redis)、落库(MySQL/Flyway)、发 Kafka
- **im-connect** (8083 HTTP + 8090 WS)：Netty WebSocket 网关、连接路由、消费下行投递
- 中间件：MySQL / Redis / Nacos / Kafka

## 前置

- JDK 17+（已验证 JDK 21 编译到 17 字节码）
- Maven 3.9+
- Docker（起中间件）

## 步骤

### 1. 启动中间件

```bash
docker compose up -d
```

启动 MySQL(3306)、Redis(6379)、Nacos(8848)、Kafka(9092)。
MySQL 首次启动会自动创建 `im_user`、`im_message` 两个库（见 `deploy/mysql/init`）。

等待约 30~60s 让 Nacos / Kafka 就绪。可访问 Nacos 控制台 http://localhost:8848/nacos （账号密码 nacos/nacos）确认。

### 2. 构建

```bash
mvn clean package -DskipTests
```

### 3. 启动三个服务（各开一个终端）

```bash
mvn -pl im-user    spring-boot:run
mvn -pl im-message spring-boot:run
mvn -pl im-connect spring-boot:run
```

> 各服务首次启动时，Flyway 自动建表；im-user 会种入演示账号 **alice(1001) / bob(1002)**，密码均 `123456`。

### 4. 打开测试客户端

用浏览器打开两个 `test-client.html` 标签页：

- 标签页 1：用户名 `alice` 登录（userId=1001）
- 标签页 2：用户名 `bob` 登录（userId=1002）

互发：
- alice 发给 `1002`（bob）→ bob 标签页实时收到 `MESSAGE`
- bob 发给 `1001`（alice）→ alice 标签页实时收到

alice 自己看到 `SEND_ACK`（含 msgId/seq），bob 看到 `MESSAGE`。

## 验收标准

- [x] 两端登录成功（LOGIN_ACK）
- [x] 发送方收到 ACK（消息已落库，msgId/seq 返回）
- [x] 接收方实时收到消息
- [x] `im_message.message` 表可查到落库记录

## 验证落库

```bash
docker exec -it im-mysql mysql -uroot -pAdmin_1234 \
  -e "SELECT msg_id, conversation_id, seq, sender_id, content FROM im_message.message;"
```

## P0 不包含（后续阶段）

群聊、离线消息与推送、富媒体、已读回执、多端漫游、读扩散、分库分表。详见 [05-roadmap.md](docs/05-roadmap.md)。

## 端口一览

| 服务/中间件 | 端口 |
|---|---|
| im-user | 8081 |
| im-message | 8082 |
| im-connect (HTTP) | 8083 |
| im-connect (WebSocket) | 8090 |
| MySQL | 3306 |
| Redis | 6379 |
| Nacos | 8848 |
| Kafka | 9092 |
