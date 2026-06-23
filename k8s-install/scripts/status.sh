#!/usr/bin/env bash
# status.sh —— 部署状态总览
#
# 用法：
#   ./k8s-install/scripts/status.sh

set -euo pipefail

NAMESPACE="im"
SERVICES="im-gateway im-user im-message im-connect im-media im-push"

# 颜色
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  ms-im-message 部署状态 (namespace: $NAMESPACE)${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"

# 检查 namespace
if ! kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
  echo -e "${RED}namespace '$NAMESPACE' 不存在 — 未部署？${NC}"
  exit 1
fi

echo ""
echo -e "${BLUE}▶ Pods${NC}"
printf "  %-15s %-10s %-5s %-25s\n" "SERVICE" "READY" "AGE" "NODE"
printf "  %-15s %-10s %-5s %-25s\n" "───────────────" "──────" "───" "────"
for svc in $SERVICES; do
  kubectl -n "$NAMESPACE" get pod -l "app=$svc" -o custom-columns=\
"SERVICE:.metadata.labels.app,READY:.status.containerStatuses[*].ready,AGE:.metadata.creationTimestamp,NODE:.spec.nodeName" \
    --no-headers 2>/dev/null | head -1 | while read line; do
    printf "  %-15s %-10s %-5s %-25s\n" $line
  done
done

echo ""
echo -e "${BLUE}▶ 镜像版本${NC}"
kubectl -n "$NAMESPACE" get deploy,statefulset \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}{end}' \
  | column -t | sed 's/^/  /'

echo ""
echo -e "${BLUE}▶ Service & Ingress${NC}"
kubectl -n "$NAMESPACE" get svc,ing --no-headers 2>/dev/null | sed 's/^/  /'

echo ""
echo -e "${BLUE}▶ HPA / PDB${NC}"
kubectl -n "$NAMESPACE" get hpa,pdb --no-headers 2>/dev/null | sed 's/^/  /' || echo "  (无)"

echo ""
echo -e "${BLUE}▶ ConfigMap / Secret 数量${NC}"
printf "  ConfigMap: %s\n" "$(kubectl -n "$NAMESPACE" get cm --no-headers 2>/dev/null | wc -l)"
printf "  Secret:    %s\n" "$(kubectl -n "$NAMESPACE" get secret --no-headers 2>/dev/null | wc -l)"

echo ""
echo -e "${BLUE}▶ 最近事件（warning/error）${NC}"
kubectl -n "$NAMESPACE" get events --sort-by='.lastTimestamp' \
  --field-selector type!=Normal 2>/dev/null | tail -10 | sed 's/^/  /' || echo "  (无)"

echo ""
echo -e "${BLUE}▶ 外部中间件连通性 (8.153.38.116)${NC}"
for PORT_NAME in "8848 Nacos" "3306 MySQL" "6379 Redis" "9092 Kafka" "9000 MinIO"; do
  PORT="${PORT_NAME% *}"; NAME="${PORT_NAME#* }"
  if timeout 2 bash -c "</dev/tcp/8.153.38.116/$PORT" 2>/dev/null; then
    echo -e "  ${GREEN}✓${NC} 8.153.38.116:$PORT ($NAME)"
  else
    echo -e "  ${RED}✗${NC} 8.153.38.116:$PORT ($NAME)"
  fi
done

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
