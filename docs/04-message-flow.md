# 04 · 消息流程与时序

## 1. 单聊发送（写扩散 + 在线/离线分流）

```mermaid
sequenceDiagram
    participant A as 发送方
    participant CA as im-connect(A)
    participant M as im-message
    participant K as Kafka
    participant CW as 扩散消费者
    participant R as Redis(路由/在线)
    participant CB as im-connect(B)
    participant P as im-push
    participant B as 接收方

    A->>CA: 发送消息(clientMsgId)
    CA->>M: 投递
    M->>M: 幂等校验(clientMsgId)
    M->>M: 生成 msgId + conv_seq
    M->>M: 持久化消息正文(MySQL·message分表)
    M-->>CA: ACK(msgId,seq) 已存储
    CA-->>A: 发送成功回执
    M->>K: 发布扩散事件
    K->>CW: 消费
    CW->>CW: 写双方 inbox(分配 user_seq)
    CW->>CW: 更新 conversation/未读
    CW->>R: 查 B 的在线状态+路由
    alt B 在线
        CW->>CB: 路由投递
        CB->>B: 实时下发
    else B 离线
        CW->>P: 触发离线推送
        P->>B: APNs/FCM 通知
    end
```

**要点**

- 服务端**落库后才回 ACK**，保证"发送成功"语义可靠。
- 扩散与推送全在 ACK 之后异步进行，发送 RT 不受影响。
- 接收方在线则实时投递；离线则转推送，待其上线再走增量同步补齐。

---

## 2. 群聊发送（读写混合扩散）

```mermaid
sequenceDiagram
    participant S as 发送方
    participant M as im-message
    participant G as im-group
    participant K as Kafka
    participant CW as 扩散消费者

    S->>M: 发送群消息
    M->>M: 生成 msgId + conv_seq，存群消息(MySQL·message分表)
    M->>G: 查群类型/成员
    alt 小群(≤500) 写扩散
        M->>K: 扩散事件
        K->>CW: 逐成员写 inbox + 在线投递/离线推送
    else 大群(>500) 读扩散
        M->>K: 仅发"会话更新"轻事件
        K->>CW: 在线成员推"新消息提醒"<br/>不写每人 inbox
        Note over CW: 成员按需从 message 表(该会话)<br/>增量拉取 + 记录已读 seq
    end
```

### 扩散模型取舍

| | 写扩散（小群） | 读扩散（大群） |
|---|---|---|
| 消息存储 | message 表 + 每人 inbox 各一份 | 仅 message 表一份 |
| 读取 | inbox 直接读，快 | 按 conv_seq 拉 message 表 |
| 写放大 | N 倍（N=成员数） | 无 |
| 适用 | ≤500 人，体验优先 | >500 人，成本优先 |
| 多端漫游 | 天然按 user_seq 同步 | 按群 conv_seq + last_read_seq 同步 |

> 阈值默认 500，可配置；由 `group_info.type` 标记，`im-message` 据此选择路径。
> 小群迁移成大群时可异步转换扩散方式。

---

## 3. 多端漫游 / 增量同步

```mermaid
sequenceDiagram
    participant D as 设备(任一端)
    participant CN as im-connect
    participant M as im-message

    D->>CN: 上线，上报本地 max(user_seq)
    CN->>M: 同步请求(since_seq)
    M->>M: 查 inbox where user_seq > since_seq
    M-->>D: 增量消息批量下发(分页)
    D->>D: 更新本地 max(user_seq)
    Note over D,M: 单聊/小群走 inbox(user_seq) 增量同步<br/>大群走各群 conv_seq 增量拉取
```

- 设备只需记住本地最大 `user_seq`，上线一次性补齐离线期间所有单聊/小群消息。
- 大群消息不在 inbox，单独按各群 `conv_seq` 与 `last_read_seq` 拉取。
- 同一账号多设备各自维护本地 seq，互不干扰，最终一致。

---

## 4. 已读回执

```mermaid
sequenceDiagram
    participant B as 接收方
    participant M as im-message
    participant A as 发送方

    B->>M: 上报已读(convId, last_read_seq)
    M->>M: 更新 conversation.last_read_seq
    M->>M: 清未读计数
    alt A 在线
        M->>A: 推送已读通知
    else A 离线
        Note over M,A: 随 A 下次同步会话状态带回
    end
```

- 接收方阅读后上报 `last_read_seq`，更新会话已读位点并清未读。
- 反向通知发送方（在线即时推、离线随下次同步），驱动发送方 UI 的"已读"标记。

---

## 5. 在线状态与输入中

```mermaid
sequenceDiagram
    participant U as 用户
    participant CN as im-connect
    participant PR as im-presence
    participant Sub as 订阅方(好友/群友)

    U->>CN: 建立连接
    CN->>PR: 上线事件
    PR->>PR: 写 presence:{userId}=online
    PR->>Sub: 推送在线变化(在线者)

    U->>CN: 正在输入
    CN->>PR: typing(convId)
    PR->>PR: 写 typing:{convId} (短TTL)
    PR->>Sub: 推送"对方正在输入"

    U--xCN: 断线/心跳超时
    CN->>PR: 下线事件
    PR->>PR: presence=offline
```

---

## 6. 消息可靠性保证

| 问题 | 机制 |
|---|---|
| **不丢** | 落库后才回 ACK；接收端 ACK + 漏收靠 seq 增量补偿 |
| **不重** | 发送侧 `clientMsgId` 幂等；接收侧按 `conv_seq` 去重 |
| **有序** | `conv_seq` 单会话严格递增；客户端按 seq 排序、检测空洞补拉 |
| **多端一致** | `user_seq` 增量同步，任意设备最终收敛到同一状态 |
| **离线可达** | 离线转推送；上线增量同步补齐 |
