# k8s-install —— K8s 部署清单

本目录是 `ms-im-message` 在 K8s 集群上的**应用部署方案**。
**中间件（MySQL/Redis/Nacos/Kafka/MinIO）已在外部物理机 `8.153.38.116` 运行，集群内不部署。**

## 目录结构

```
k8s-install/
├── base/                            # 6 个微服务的 Kustomize 基础清单
│   ├── kustomization.yaml           # 入口：列出所有 base 资源
│   ├── namespace.yaml               # namespace: im
│   ├── serviceaccount-im.yaml       # Pod 用的 SA
│   ├── role-im-deployer.yaml        # CI/CD SA 权限
│   ├── rolebinding-im-deployer.yaml
│   ├── configmap-common.yaml        # 共享非密配置（指向外部 8.153.38.116）
│   ├── configmap-per-service/       # 每服务专属 ConfigMap
│   │   ├── im-gateway-cm.yaml
│   │   ├── im-user-cm.yaml
│   │   ├── im-message-cm.yaml
│   │   ├── im-connect-cm.yaml
│   │   ├── im-media-cm.yaml
│   │   └── im-push-cm.yaml
│   ├── secret-middleware.yaml      # 外部中间件凭据（DB/MinIO/Redis，真值通过 render-secrets.sh）
│   ├── secret-jwt.yaml              # JWT 共享密钥（独立管理）
│   ├── deployment-im-{gateway,user,message,connect,media,push}.yaml
│   ├── service-im-{gateway,user,message,connect,media,push}.yaml
│   ├── service-im-connect-actuator.yaml
│   ├── ingress-im-gateway.yaml      # HTTP 入口
│   ├── ingress-im-connect.yaml      # WebSocket 入口（粘性会话）
│   ├── hpa-im-{user,message,media,push}.yaml
│   └── pdb-im-connect.yaml
│
├── overlays/
│   └── prod/                        # 生产环境覆盖
│       ├── kustomization.yaml       # 镜像 tag、域名、资源
│       ├── .env.example             # 环境变量清单
│       └── README.md
│
├── scripts/
│   └── render-secrets.sh            # Secret 一键生成
│
└── README.md (本文件)
```

## 关键设计点

### 1. Kustomize（不用 Helm）

- 部署只需 `kubectl apply -k` —— 零额外工具
- `base/` 是单一来源，`overlays/prod/` 覆盖环境相关字段
- CD 流水线自动 `kustomize edit set image` 更新镜像 tag

### 2. im-connect WebSocket 粘性会话

最棘手的部分。Netty 长连接不能跨 Pod 漂移，方案：

- **StatefulSet** 提供稳定网络标识（`im-connect-0.im-connect.im.svc.cluster.local`）
- **Headless Service** 让每个 Pod 有独立 DNS A 记录
- **Ingress 注解** `upstream-hash-by: $binary_remote_addr` 按客户端 IP 哈希
- **sessionAffinity: ClientIP** + 3 小时超时
- **不走 HPA**（避免扩缩容断连），改用 **PodDisruptionBudget** 保活

### 3. 配置外化

- **ConfigMap `im-config`**：外部中间件地址（`8.153.38.116:port`）
- **Secret `im-middleware-secret`**：外部中间件凭据（MySQL/MinIO/Redis）
- **Secret `im-jwt-secret`**：JWT 共享密钥（与应用算法相关，独立管理）
- **application.yml**：默认值 = 8.153.38.116（本地开发与 K8s 兼容）

### 4. 中间件 —— 全部外部已部署

MySQL / Redis / Nacos / Kafka / MinIO **已部署在外部物理机 `8.153.38.116`**。

- K8s ConfigMap `im-config` 配置外部地址
- K8s **不部署** 中间件
- 应用 Pod 通过集群 Node 网络路由到 `8.153.38.116`

前置检查：集群 Node 能 ping 通 `8.153.38.116`，防火墙放行 `3306/6379/8848/9848/9092/9000`。
详见 [docs/07-deployment.md §2.4](../docs/07-deployment.md)。

## 常用命令

### 一键脚本（推荐）

```bash
# 全量部署
./k8s-install/scripts/deploy.sh

# 查看状态
./k8s-install/scripts/status.sh

# 一键卸载
./k8s-install/scripts/undeploy.sh

# 创建 Secret（deploy.sh 内部已调用；可单独跑）
./k8s-install/scripts/render-secrets.sh | kubectl apply -f -
```

### 手动命令

```bash
# 预览 base 渲染结果
kubectl kustomize k8s-install/base

# 预览 prod 渲染结果
kubectl kustomize k8s-install/overlays/prod

# 部署
kubectl apply -k k8s-install/overlays/prod

# 删除
kubectl delete -k k8s-install/overlays/prod

# 看部署状态
kubectl -n im get deploy,svc,ing,pod

# 滚动重启某个服务
kubectl -n im rollout restart deploy/im-user
```

## 详细文档

- [FILES.md](FILES.md) — **每个 YAML 文件的作用 + 执行顺序清单**
- [docs/07-deployment.md](../docs/07-deployment.md) — 完整的部署与运维 Runbook
- [.github/workflows/README.md](../.github/workflows/README.md) — CI/CD 流水线说明
- [k8s-install/overlays/prod/README.md](overlays/prod/README.md) — 生产环境配置

## 脚本说明

| 脚本 | 用途 |
|---|---|
| `scripts/deploy.sh` | 一键全量部署（前置检查 → namespace → RBAC → ConfigMap → Secret → 应用 → rollout） |
| `scripts/undeploy.sh` | 一键卸载 |
| `scripts/status.sh` | 部署状态总览（Pods/Service/HPA/事件/中间件连通性） |
| `scripts/render-secrets.sh` | 生成 K8s Secret YAML（含交互/--no-input 两种模式） |
