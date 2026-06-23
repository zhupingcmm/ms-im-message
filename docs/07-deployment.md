# 07 — 部署与运维 Runbook

本文档介绍如何把 `ms-im-message` 部署到 K8s 集群，以及日常运维的常用操作。

## 1. 整体架构

```
GitHub Actions (CI)
   │  PR/push → mvn verify
   ▼
GitHub Actions (CD)
   │  tag v* / manual
   │ 1. maven build ──> 6 个 fat-jar
   │ 2. docker build ──> 推 ACR
   │ 3. kubectl apply -k
   ▼
K8s Cluster
   ├─ im namespace
   │   ├─ 6 个微服务（im-gateway / im-user / im-message / im-connect / im-media / im-push）
   │   └─ 2 个 Ingress（im-gateway HTTP, im-connect WebSocket）
   └─ ingress-nginx
        │
        ▼
外部物理机 8.153.38.116
   ├─ MySQL (3306)
   ├─ Redis (6379)
   ├─ Nacos (8848)
   ├─ Kafka (9092)
   └─ MinIO (9000)
```

## 2. 一次性环境准备

> **架构前提**：中间件（MySQL/Redis/Nacos/Kafka/MinIO）已外部部署在物理机 `8.153.38.116`，
> K8s 集群**只跑 6 个微服务**。所有 K8s 节点需能路由到该 IP。

### 2.1 集群前置依赖

| 组件 | 命令 |
|---|---|
| Calico CNI | `kubectl apply -f tigera-operator.yaml -f custom-resources.yaml` |
| nginx-ingress | `helm install ingress-nginx ingress-nginx/ingress-nginx -n ingress-nginx --create-namespace` |
| metrics-server（HPA 依赖） | `kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml` |

### 2.1.1 生成 Maven Wrapper

如果 `mvnw` / `mvnw.cmd` / `.mvn/wrapper/maven-wrapper.jar` 不在仓库里（首次 clone 后）：

```bash
# 需本地有 mvn 与 JDK 17
mvn -N wrapper:wrapper
git add mvnw mvnw.cmd .mvn/
git commit -m "build: add Maven Wrapper"
```

之后所有本地与 CI 操作统一用 `./mvnw`（Windows 用 `mvnw.cmd`）。

### 2.2 命名空间与 RBAC

```bash
kubectl apply -f k8s-install/base/namespace.yaml
kubectl apply -f k8s-install/base/role-im-deployer.yaml
kubectl apply -f k8s-install/base/rolebinding-im-deployer.yaml
```

### 2.3 创建 Secret（关键！）

```bash
# 交互式输入密码
./k8s-install/scripts/render-secrets.sh | kubectl apply -f -

# 或者用环境变量（CI/CD 用）
export DB_ROOT_PASSWORD=$(openssl rand -hex 16)
export DB_PASSWORD=$(openssl rand -hex 16)
export IM_JWT_SECRET=$(openssl rand -hex 32)
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=$(openssl rand -hex 16)
export REDIS_PASSWORD=$(openssl rand -hex 16)
./k8s-install/scripts/render-secrets.sh --no-input | kubectl apply -f -
```

### 2.4 中间件（已外部部署，跳过）

MySQL / Redis / Nacos / Kafka / MinIO **已部署在外部物理机 `8.153.38.116`**，
K8s 集群**不部署**这些中间件。ConfigMap `im-config` 已配置好外部地址。

**验证连通性**（必做）：
```bash
kubectl run netcheck --rm -it --image=busybox:1.36 -- \
  sh -c 'nc -zv 8.153.38.116 8848 && nc -zv 8.153.38.116 3306 \
      && nc -zv 8.153.38.116 6379 && nc -zv 8.153.38.116 9092 \
      && nc -zv 8.153.38.116 9000'
```

如果中间件服务有不同端口或额外鉴权（如 Nacos 鉴权、Redis 密码），
编辑 `k8s-install/base/configmap-common.yaml` 调整。

### 2.5 引导 Nacos 配置

通过 Nacos Console 访问**外部 Nacos**（浏览器打开 `http://8.153.38.116:8848/nacos`，
默认账号 `nacos/nacos`）创建：
- namespace: `im-prod`
- Data ID: `common.yaml`（粘贴 `deploy/nacos/common.yaml` 内容）
- Data ID: `im-user.yaml`（粘贴 `deploy/nacos/im-user.yaml`）
- Data ID: `im-message.yaml`（粘贴 `deploy/nacos/im-message.yaml`）

### 2.6 部署应用

```bash
kubectl apply -k k8s-install/overlays/prod
kubectl -n im get pods,svc,ing
```

> 业务库（`im_user` / `im_message`）需要在外部 MySQL 上初始化：
> ```bash
> mysql -h8.153.38.116 -uroot -p < deploy/mysql/init/01-create-databases.sql
> ```

## 3. GitHub 仓库配置

### 3.1 Secrets

到 GitHub Repo → Settings → Secrets and variables → Actions 添加：

| Secret | 用途 |
|---|---|
| `ACR_USERNAME` | 阿里云 ACR 用户名 |
| `ACR_PASSWORD` | 阿里云 ACR 密码 |
| `KUBECONFIG_B64` | `cat ~/.kube/config \| base64 -w 0`（指向 `im-deployer` SA） |

### 3.2 触发部署

```bash
git tag v0.1.0
git push --tags
```

或手动：GitHub → Actions → CD → Run workflow

## 4. 日常运维

### 4.1 查看 Pod 状态

```bash
# 所有应用 Pod
kubectl -n im get pods -l app.kubernetes.io/part-of=ms-im-message

# 单服务详细
kubectl -n im describe pod -l app=im-user
```

### 4.2 查日志

```bash
# 单 Pod 实时
kubectl -n im logs -f -l app=im-user --tail=200

# 上一实例（崩溃后）
kubectl -n im logs -l app=im-user --previous

# 6 个服务一起看
for svc in im-gateway im-user im-message im-connect im-media im-push; do
  echo "===== $svc ====="
  kubectl -n im logs -l app=$svc --tail=20
done
```

### 4.3 进入容器

```bash
kubectl -n im exec -it deploy/im-user -- sh
# Distroless 镜像没有 shell，需用 nsenter：
kubectl -n im exec -it $(kubectl -n im get pod -l app=im-user -o name | head -1) -- \
  /bin/sh  # 通常不可用，建议用 debug sidecar 或 kubectl debug
```

### 4.4 端口转发调试

```bash
# im-user
kubectl -n im port-forward svc/im-user 8081:8081

# nacos
kubectl -n im port-forward svc/nacos 8848:8848
# 浏览器打开 http://localhost:8848/nacos  (nacos/nacos)
```

### 4.5 健康检查

```bash
# 直接 curl Pod IP
kubectl -n im exec deploy/im-user -- \
  wget -qO- http://localhost:8081/actuator/health
```

### 4.6 扩容

```bash
# 临时扩 im-message 到 5 副本
kubectl -n im scale deploy/im-message --replicas=5

# 或改 HPA 上限（Kustomize patch）
```

### 4.7 回滚

```bash
# 回滚到上一个版本
kubectl -n im rollout undo deploy/im-user

# 回滚到指定版本
kubectl -n im rollout history deploy/im-user
kubectl -n im rollout undo deploy/im-user --to-revision=2
```

### 4.8 固定镜像版本（紧急止血）

```bash
kubectl -n im set image deploy/im-user \
  im-user=registry.cn-hangzhou.aliyuncs.com/im-prod/im-user:sha-abc1234
```

## 5. 故障排查 Checklist

| 症状 | 检查点 |
|---|---|
| Pod Pending | `kubectl describe pod` → Events 看 PVC / 节点资源 |
| Pod CrashLoopBackOff | `kubectl logs --previous` 看启动日志（多半是 Nacos / DB 连不上） |
| 503 from gateway | `kubectl -n im get pods -l app=im-user` 看下游是否 ready |
| WebSocket 频繁断连 | `kubectl -n im logs -l app=im-connect` 看 Netty 心跳日志；检查 `upstream-hash-by` 注解 |
| Nacos 注册失败 | 应用日志看 "failed to register"；`kubectl -n im exec -it nacos-0 -- curl localhost:8848/nacos/v1/ns/operator/metrics` |
| Kafka 消费阻塞 | `kubectl -n im exec -it kafka-0 -- kafka-consumer-groups --bootstrap-server localhost:9092 --list` |

## 6. 备份与恢复

### 6.1 MySQL

```bash
# 手动备份
kubectl -n im exec -it mysql-0 -- \
  sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases' > backup.sql

# 建议加 CronJob 定时备份到 MinIO（参考 cronjobs/ 目录——尚未生成）
```

### 6.2 Redis

```bash
kubectl -n im exec -it redis-0 -- redis-cli BGSAVE
# RDB 文件在 /data/dump.rdb
```

## 7. 监控（最小集）

生产前应加：
- Prometheus + Grafana
- Loki / ELK（日志聚合）
- Alertmanager（关键告警：Pod 重启、内存 OOM、Lag 飙升）

## 8. 不在本次方案范围

以下为后续 P2/P3 阶段工作：

- 多环境（staging）
- 蓝绿/灰度发布
- 服务网格（Istio）
- GitOps（ArgoCD / Flux）
- cert-manager 自动证书
- 异地多活
