# CI/CD 流水线说明

本目录包含 ms-im-message 的 GitHub Actions 流水线。

## 工作流

| 文件 | 触发器 | 用途 |
|---|---|---|
| [`ci.yml`](./ci.yml) | PR / push 到 main、develop | 编译 + 单元测试（不部署） |
| [`cd.yml`](./cd.yml) | push tag `v*` / `workflow_dispatch` | 构建 6 个镜像 → 推 ACR → `kubectl apply` |

## 触发方式

### 持续集成（自动）
- 任何 PR 合入 main / develop → 跑 `mvn verify`
- merge 到 main / develop → 再跑一次（作为基线）

### 持续部署

**发版**：
```bash
git tag v0.1.0
git push --tags
```
- 自动构建 6 个镜像、tag 为 `v0.1.0` + `sha-xxxxxxx`
- 推送到阿里云 ACR `registry.cn-hangzhou.aliyuncs.com/im-prod/`
- 部署到 K8s `im` 命名空间

**手动发布**（测试或回滚到某次 commit）：
1. GitHub → Actions → CD → Run workflow
2. 可选填 `tag_suffix`，最终 tag 形如 `dispatch-20260623-153045`

## 必需的 GitHub Secrets

| Secret | 用途 |
|---|---|
| `ACR_USERNAME` | 阿里云 ACR 用户名 |
| `ACR_PASSWORD` | 阿里云 ACR 密码 |
| `KUBECONFIG_B64` | base64 编码的 kubeconfig（指向 namespace-scoped SA `im-deployer`，**不要 cluster-admin**） |

## 镜像命名规范

```
registry.cn-hangzhou.aliyuncs.com/im-prod/im-<service>:<tag>
```

例：`registry.cn-hangzhou.aliyuncs.com/im-prod/im-user:v0.1.0`

## Tag 方案

| 触发 | Tag |
|---|---|
| `v0.1.0` | `v0.1.0` + `sha-<7位>` |
| `workflow_dispatch` | `dispatch-<时间戳>` + `sha-<7位>` |

## 调整配置

- **修改服务列表**：编辑 [cd.yml:17](cd.yml#L17) 的 `SERVICES` env
- **修改镜像前缀**：[cd.yml:14](cd.yml#L14) `IMAGE_PREFIX`
- **修改 ACR 区域**：[cd.yml:13](cd.yml#L13) `REGISTRY`
- **修改 K8s namespace**：[cd.yml:16](cd.yml#L16) `K8S_NAMESPACE`
