# overlays/prod —— 生产环境

本目录是 K8s 清单的**生产环境**覆盖层。

## 包含什么

| 文件 | 作用 |
|---|---|
| `kustomization.yaml` | 引用 base + 覆盖镜像 tag、域名、资源 |
| `.env.example` | 列出所有环境变量名（不含真实值） |

## 镜像 Tag 是怎么更新的

CD 流水线（`.github/workflows/cd.yml`）每次发版时执行：

```bash
kustomize edit set image im-user=registry.cn-hangzhou.aliyuncs.com/im-prod/im-user:v0.1.0
```

这会**自动修改 `kustomization.yaml` 中的 `newTag`**，并 commit 回仓库。

所以你**不要手改** `kustomization.yaml` 里的 `newTag` —— 改了下次部署会被覆盖。但其他字段（域名、资源、副本数）可以手动改并 commit。

## 域名修改

编辑 `kustomization.yaml` 里的两个 patch：
- `im-gateway` 的 host → HTTP 入口
- `im-connect` 的 host → WebSocket 入口

改完直接 commit 即可（CD 流水线不会动这两个字段）。

## 首次部署

```bash
# 1. 创建 namespace + Secret
kubectl apply -f k8s-install/base/namespace.yaml
./k8s-install/scripts/render-secrets.sh | kubectl apply -f -

# 2. 部署应用
kubectl apply -k k8s-install/overlays/prod

# 3. 查看
kubectl -n im get all,ing
```

> **中间件不在 K8s 集群内部署**。MySQL/Redis/Nacos/Kafka/MinIO 已在外部物理机
> `8.153.38.116` 运行，K8s 集群只跑 6 个微服务。详见 [07-deployment.md §2.4](../../docs/07-deployment.md)。

## 后续发布

```bash
git tag v0.1.0
git push --tags
# 触发 cd.yml：build + push + deploy
```
