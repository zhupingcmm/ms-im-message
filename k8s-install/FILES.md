# k8s-install — 文件清单与执行顺序

本文档列出 `k8s-install/` 下**每个 YAML 文件的作用**和**部署顺序**。

> 💡 **日常使用**：99% 情况下你不需要单独 apply 某个文件，直接跑
> `k8s-install/scripts/deploy.sh` 即可。本文件用于排查"某个资源为什么不工作"。

---

## 1. 文件总览

| 类别 | 数量 | 路径 |
|---|---|---|
| 顶层 README | 1 | `k8s-install/README.md` |
| 文件清单（本文件） | 1 | `k8s-install/FILES.md` |
| Kustomize 入口 | 2 | `base/kustomization.yaml`、`overlays/prod/kustomization.yaml` |
| Namespace | 1 | `base/namespace.yaml` |
| ConfigMap | 7 | `base/configmap-common.yaml` + 6 个 per-service |
| Secret 模板 | 2 | `base/secret-{middleware,jwt}.yaml` |
| RBAC | 3 | `base/{serviceaccount-im,role-im-deployer,rolebinding-im-deployer}.yaml` |
| Deployment / StatefulSet | 6 | `base/deployment-im-{gateway,user,message,connect,media,push}.yaml` |
| Service | 7 | `base/service-im-{gateway,user,message,connect,media,push}.yaml` + `service-im-connect-actuator.yaml` |
| Ingress | 2 | `base/ingress-im-{gateway,connect}.yaml` |
| HPA | 4 | `base/hpa-im-{user,message,media,push}.yaml` |
| PDB | 1 | `base/pdb-im-connect.yaml` |
| Overlay | 3 | `overlays/prod/{kustomization.yaml, README.md, .env.example}` |
| 脚本 | 3 | `scripts/{render-secrets.sh, deploy.sh, status.sh}` |

**总计 39 个文件**。

---

## 2. 执行顺序（首次部署）

> **前置条件**：
> - `kubectl` 已配置 K8s 集群访问权限
> - 集群 Node 能路由到 `8.153.38.116`（外部中间件）
> - 外部 Nacos Console 已有 `im-prod` namespace 及 3 个 dataId

### 第 1 步：基础设施（已外部，不在 K8s 部署）

```bash
# 跳过——MySQL/Redis/Nacos/Kafka/MinIO 已在 8.153.38.116 运行
```

### 第 2 步：集群前置（一次性）

```bash
# Calico CNI（已提供 raw manifest）
kubectl apply -f tigera-operator.yaml -f custom-resources.yaml

# nginx-ingress
helm install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace

# metrics-server
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

### 第 3 步：Namespace

| 文件 | 作用 |
|---|---|
| `base/namespace.yaml` | 创建 `im` namespace |

```bash
kubectl apply -f base/namespace.yaml
```

### 第 4 步：RBAC

| 文件 | 作用 |
|---|---|
| `base/role-im-deployer.yaml` | CI/CD SA 权限（限定 namespace im） |
| `base/rolebinding-im-deployer.yaml` | 把 SA 绑到 Role |
| `base/serviceaccount-im.yaml` | Pod 用的 SA（`im-app`） |

```bash
kubectl apply -f base/role-im-deployer.yaml
kubectl apply -f base/rolebinding-im-deployer.yaml
kubectl apply -f base/serviceaccount-im.yaml
```

> 注：`base/kustomization.yaml` 已经把上面 3 个文件汇总，也可以：
> `kubectl apply -f base/kustomization.yaml`

### 第 5 步：ConfigMap

| 文件 | 作用 |
|---|---|
| `base/configmap-common.yaml` | 共享配置（外部中间件地址、Netty、JVM 参数） |
| `base/configmap-per-service/im-gateway-cm.yaml` | 网关专属（端口、namespace） |
| `base/configmap-per-service/im-user-cm.yaml` | 用户服务 |
| `base/configmap-per-service/im-message-cm.yaml` | 消息服务 |
| `base/configmap-per-service/im-connect-cm.yaml` | 长连接（IM_GATEWAY_ID 默认值） |
| `base/configmap-per-service/im-media-cm.yaml` | 媒体服务 |
| `base/configmap-per-service/im-push-cm.yaml` | 推送服务 |

```bash
kubectl apply -f base/configmap-common.yaml
for f in base/configmap-per-service/*.yaml; do
  kubectl apply -f "$f"
done
```

### 第 6 步：Secret

> ⚠️ **不要直接 apply `secret-*.yaml`**，里面的值是 `REPLACE_ME`。
> 用 `scripts/render-secrets.sh` 注入真实密码。

```bash
# 交互式或环境变量注入
./scripts/render-secrets.sh | kubectl apply -f -
```

生成的 Secret（**2 个文件，集中管理**）：
- `im-middleware-secret`（`DB_PASSWORD` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `REDIS_PASSWORD` —— 外部中间件凭据，集中管理）
- `im-jwt-secret`（`IM_JWT_SECRET`，user/connect/gateway 共享 —— 与应用算法相关，独立管理）

### 第 7 步：业务部署

下面 6 个 YAML 由 Kustomize 统一管理（顺序由 `base/kustomization.yaml` 决定），**通常用 `kubectl apply -k` 而非单独 apply**：

| 文件 | 类型 | 副本 | 作用 |
|---|---|---|---|
| `base/deployment-im-gateway.yaml` | Deployment | 2 | Spring Cloud Gateway，HTTP 入口 |
| `base/deployment-im-user.yaml` | Deployment | 2 | 用户、登录、JWT |
| `base/deployment-im-message.yaml` | Deployment | 3 | 消息收发、落库、扩散 |
| `base/deployment-im-connect.yaml` | **StatefulSet** | 3 | Netty WebSocket 长连接（**不用 HPA**） |
| `base/deployment-im-media.yaml` | Deployment | 2 | MinIO 预签名直传 |
| `base/deployment-im-push.yaml` | Deployment | 2 | Kafka 离线推送消费者 |

对应的 Service / Ingress：

| 文件 | 作用 |
|---|---|
| `base/service-im-gateway.yaml` | 8080 ClusterIP |
| `base/service-im-user.yaml` | 8081 ClusterIP |
| `base/service-im-message.yaml` | 8082 ClusterIP |
| `base/service-im-connect.yaml` | **8091 Headless + sessionAffinity: ClientIP** |
| `base/service-im-connect-actuator.yaml` | 8083 ClusterIP（K8s 探针专用，不走 WS 路径） |
| `base/service-im-media.yaml` | 8084 ClusterIP |
| `base/service-im-push.yaml` | 8085 ClusterIP |
| `base/ingress-im-gateway.yaml` | 域名 `im.example.com` |
| `base/ingress-im-connect.yaml` | 域名 `ws.example.com` + WS 注解 |

弹性伸缩 / 保护：

| 文件 | 目标 | 类型 | 范围 |
|---|---|---|---|
| `base/hpa-im-user.yaml` | im-user | HPA | 2-8，CPU 60% |
| `base/hpa-im-message.yaml` | im-message | HPA | 3-12，CPU 60% |
| `base/hpa-im-media.yaml` | im-media | HPA | 2-6，CPU 60% |
| `base/hpa-im-push.yaml` | im-push | HPA | 2-6，CPU 60% |
| `base/pdb-im-connect.yaml` | im-connect | PDB | minAvailable: 2 |

```bash
# 一次性 apply 全部（推荐）
kubectl apply -k overlays/prod

# 单独 apply（排查用）
kubectl apply -f base/deployment-im-user.yaml
# ...
```

### 第 8 步：overlay 覆盖

`overlays/prod/kustomization.yaml` 提供：
- 镜像 tag 覆盖（指向 ACR）
- 域名覆盖（生产域名）
- 资源上调（生产压力更大）

```bash
# 部署 prod（= base + overlay）
kubectl apply -k overlays/prod
```

---

## 3. 一键部署脚本

```bash
./k8s-install/scripts/deploy.sh
```

自动按顺序执行：
1. 验证 kubectl 与 K8s 集群连通
2. 验证外部中间件网络（MySQL/Redis/Nacos/Kafka/MinIO）
3. 验证 Nacos 配置已存在
4. 创建 namespace
5. 创建 RBAC（SA + Role + RoleBinding）
6. 注入 ConfigMap（共享 + 6 个 per-service）
7. 注入 Secret（通过 render-secrets.sh）
8. 部署 6 个业务（Deployment/StatefulSet + Service + Ingress + HPA + PDB）
9. 等待所有 rollout 完成
10. 打印状态

---

## 4. 一键回滚脚本

```bash
./k8s-install/scripts/undeploy.sh
```

按反向顺序删除（Secret → ConfigMap → Deployment → Service → RBAC → Namespace）。

---

## 5. 状态查看脚本

```bash
./k8s-install/scripts/status.sh
```

打印：Pod 状态、Service/Ingress 状态、镜像版本、副本数、Probes、HPA 状态、滚动状态。

---

## 6. 单独 apply 某个文件的场景

| 场景 | 命令 |
|---|---|
| 改了 ConfigMap | `kubectl apply -f base/configmap-common.yaml` |
| 改了某个 Deployment 资源 | `kubectl apply -f base/deployment-im-user.yaml` |
| 重置 Secret | `./scripts/render-secrets.sh \| kubectl apply -f -` |
| 触发滚动重启 | `kubectl -n im rollout restart deploy/im-user` |

---

## 7. Kustomize 渲染预览

```bash
# 看 base 渲染结果
kubectl kustomize k8s-install/base

# 看 prod overlay 渲染结果（含所有 patch 后的最终 YAML）
kubectl kustomize k8s-install/overlays/prod

# 保存到文件审查
kubectl kustomize k8s-install/overlays/prod > /tmp/rendered.yaml
```

## 8. 文件依赖图

```
namespace.yaml
    └── serviceaccount-im.yaml
            └── deployment-*.yaml
                    └── service-*.yaml
                            └── ingress-*.yaml

role-im-deployer.yaml
    └── rolebinding-im-deployer.yaml
            (被 CI/CD SA 用)

configmap-common.yaml
    └── configmap-per-service/*.yaml
            └── deployment-*.yaml（envFrom）

secret-{middleware,jwt}.yaml
    └── deployment-*.yaml（envFrom）

ingress-*.yaml
    └── service-*.yaml（backend）

hpa-*.yaml / pdb-*.yaml
    └── deployment-*.yaml / statefulset（scaleTargetRef / selector）
```
