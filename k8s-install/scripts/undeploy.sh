#!/usr/bin/env bash
# undeploy.sh —— 一键卸载（按反向顺序删除）
#
# 用法：
#   ./k8s-install/scripts/undeploy.sh              # 交互确认
#   ./k8s-install/scripts/undeploy.sh --yes        # 跳过确认
#   ./k8s-install/scripts/undeploy.sh --keep-ns    # 保留 namespace（只删里面资源）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OVERLAY_DIR="$SCRIPT_DIR/../overlays/prod"
NAMESPACE="im"

ASSUME_YES=false
KEEP_NS=false
for arg in "$@"; do
  case "$arg" in
    --yes) ASSUME_YES=true ;;
    --keep-ns) KEEP_NS=true ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
warn() { echo -e "${YELLOW}⚠ $*${NC}"; }
fail() { echo -e "${RED}✗ $*${NC}"; exit 1; }

if ! $ASSUME_YES; then
  warn "将删除 namespace '$NAMESPACE' 下所有资源（Deployment/Service/ConfigMap/Secret/...）"
  warn "外部 MySQL 数据不会被影响"
  read -p "确认卸载？[y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]] || { echo "已取消"; exit 0; }
fi

echo "▶ 删除 kustomize 资源..."
kubectl delete -k "$OVERLAY_DIR" --ignore-not-found 2>&1 | tail -20

if ! $KEEP_NS; then
  echo "▶ 删除 namespace..."
  kubectl delete namespace "$NAMESPACE" --ignore-not-found
  warn "namespace '$NAMESPACE' 已删除"
else
  warn "保留 namespace '$NAMESPACE'，手动清理：kubectl -n $NAMESPACE delete all,cm,secret,ing,hpa,pdb,sa,role,rolebinding --all"
fi

echo "✅ 卸载完成"
