#!/usr/bin/env bash
# deploy.sh —— 一键全量部署 ms-im-message 到 K8s
#
# 执行顺序（与 docs/07-deployment.md §2 一致）：
#   1. 前置检查：kubectl、连通 K8s、连通外部中间件
#   2. 创建 namespace
#   3. 创建 RBAC（SA + Role + RoleBinding）
#   4. 创建 ConfigMap
#   5. 注入 Secret（render-secrets.sh）
#   6. 部署应用（kustomize overlay/prod）
#   7. 等待 rollout
#   8. 打印状态
#
# 用法：
#   ./k8s-install/scripts/deploy.sh                # 交互式（推荐首次部署）
#   ./k8s-install/scripts/deploy.sh --skip-check   # 跳过外部中间件连通性检查
#   ./k8s-install/scripts/deploy.sh --recreate     # 删了重建（慎用）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE_DIR="$K8S_DIR/base"
OVERLAY_DIR="$K8S_DIR/overlays/prod"
NAMESPACE="im"

# ---------- 颜色 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${BLUE}▶ $*${NC}"; }
ok()    { echo -e "${GREEN}✓ $*${NC}"; }
warn()  { echo -e "${YELLOW}⚠ $*${NC}"; }
fail()  { echo -e "${RED}✗ $*${NC}"; exit 1; }

# ---------- 参数解析 ----------
SKIP_CHECK=false
RECREATE=false
for arg in "$@"; do
  case "$arg" in
    --skip-check) SKIP_CHECK=true ;;
    --recreate)   RECREATE=true ;;
    --help|-h)
      sed -n '2,18p' "$0"
      exit 0
      ;;
    *) fail "未知参数: $arg" ;;
  esac
done

# ---------- 步骤 1: 前置检查 ----------
info "步骤 1/8 — 前置检查"

command -v kubectl >/dev/null 2>&1 || fail "kubectl 未安装"
ok "kubectl 已安装: $(kubectl version --client -o yaml 2>/dev/null | grep gitVersion | head -1)"

kubectl cluster-info >/dev/null 2>&1 || fail "无法连接 K8s 集群，请检查 kubeconfig"
ok "K8s 集群可达"

# 验证外部中间件连通（不通过就警告但不退出，由用户决定）
if ! $SKIP_CHECK; then
  info "  验证外部中间件 (8.153.38.116)..."
  CHECK_FAILED=false
  for HOST_PORT in "8.153.38.116 8848" "8.153.38.116 3306" "8.153.38.116 6379" "8.153.38.116 9092" "8.153.38.116 9000"; do
    HOST="${HOST_PORT% *}"; PORT="${HOST_PORT#* }"
    if ! kubectl run netcheck --rm -it --restart=Never --image=busybox:1.36 --quiet -- \
        timeout 3 nc -zv "$HOST" "$PORT" >/dev/null 2>&1; then
      warn "  外部 $HOST:$PORT 连不通"
      CHECK_FAILED=true
    else
      ok "  $HOST:$PORT OK"
    fi
  done
  if $CHECK_FAILED; then
    warn "部分外部中间件不通，是否继续？[y/N]"
    read -r ans
    [[ "$ans" =~ ^[Yy]$ ]] || fail "用户中止"
  fi
else
  warn "跳过外部中间件连通性检查"
fi

# ---------- 步骤 2: Namespace ----------
info "步骤 2/8 — 创建 namespace ($NAMESPACE)"
kubectl apply -f "$BASE_DIR/namespace.yaml"
ok "namespace 就绪"

# ---------- 步骤 3: RBAC ----------
info "步骤 3/8 — 创建 RBAC"
kubectl apply -f "$BASE_DIR/serviceaccount-im.yaml"
kubectl apply -f "$BASE_DIR/role-im-deployer.yaml"
kubectl apply -f "$BASE_DIR/rolebinding-im-deployer.yaml"
ok "RBAC 就绪（SA: im-app, im-deployer）"

# ---------- 步骤 4: ConfigMap ----------
info "步骤 4/8 — 创建 ConfigMap"
kubectl apply -f "$BASE_DIR/configmap-common.yaml"
for f in "$BASE_DIR/configmap-per-service/"*.yaml; do
  kubectl apply -f "$f"
done
ok "ConfigMap 就绪（共享 + 6 个 per-service）"

# ---------- 步骤 5: Secret ----------
info "步骤 5/8 — 注入 Secret（render-secrets.sh）"
if kubectl -n "$NAMESPACE" get secret im-jwt-secret >/dev/null 2>&1 \
   && kubectl -n "$NAMESPACE" get secret im-middleware-secret >/dev/null 2>&1; then
  warn "Secret 已存在，跳过注入（如果需要重置：kubectl delete secret im-middleware-secret im-jwt-secret -n $NAMESPACE）"
else
  "$SCRIPT_DIR/render-secrets.sh" | kubectl apply -f -
  ok "Secret 注入完成（im-middleware-secret + im-jwt-secret）"
fi

# ---------- 步骤 6: 部署应用 ----------
info "步骤 6/8 — 部署应用（kustomize overlay/prod）"
if $RECREATE; then
  warn "--recreate: 先删除再 apply"
  kubectl delete -k "$OVERLAY_DIR" --ignore-not-found 2>/dev/null || true
  sleep 5
fi
kubectl apply -k "$OVERLAY_DIR"
ok "应用清单已 apply"

# ---------- 步骤 7: 等待 rollout ----------
info "步骤 7/8 — 等待 rollout 完成（超时 5min）"
SERVICES="im-gateway im-user im-message im-connect im-media im-push"
for svc in $SERVICES; do
  echo "  → $svc"
  if ! kubectl -n "$NAMESPACE" rollout status "$svc" --timeout=5m 2>/dev/null; then
    # Deployment/StatefulSet 都支持 rollout status
    if ! kubectl -n "$NAMESPACE" rollout status "deploy/$svc" --timeout=5m 2>/dev/null \
       && ! kubectl -n "$NAMESPACE" rollout status "statefulset/$svc" --timeout=5m 2>/dev/null; then
      fail "$svc rollout 失败，请 kubectl -n $NAMESPACE describe pod -l app=$svc"
    fi
  fi
done
ok "全部 rollout 完成"

# ---------- 步骤 8: 打印状态 ----------
info "步骤 8/8 — 部署状态"
echo ""
echo "────────────────────────────────────────────"
echo "  Pods"
echo "────────────────────────────────────────────"
kubectl -n "$NAMESPACE" get pods -o wide
echo ""
echo "────────────────────────────────────────────"
echo "  Service / Ingress"
echo "────────────────────────────────────────────"
kubectl -n "$NAMESPACE" get svc,ing
echo ""
echo "────────────────────────────────────────────"
echo "  HPA / PDB"
echo "────────────────────────────────────────────"
kubectl -n "$NAMESPACE" get hpa,pdb
echo ""
echo "────────────────────────────────────────────"
echo "  镜像版本"
echo "────────────────────────────────────────────"
kubectl -n "$NAMESPACE" get deploy,statefulset \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}{end}' | column -t

ok "✅ 部署完成"
echo ""
echo "💡 后续操作："
echo "  - 查看日志:   kubectl -n $NAMESPACE logs -l app=im-user --tail=200"
echo "  - 端口转发:   kubectl -n $NAMESPACE port-forward svc/im-gateway 8080:8080"
echo "  - 卸载:       $SCRIPT_DIR/undeploy.sh"
