#!/usr/bin/env bash
# render-secrets.sh —— 一键创建 K8s Secret（不入库真值）
#
# 输出两个 Secret 的 YAML：
#   1. im-middleware-secret —— 外部中间件凭据（MySQL/MinIO/Redis，集中管理）
#   2. im-jwt-secret        —— JWT 共享密钥（与应用算法相关，独立管理）
#
# 用法：
#   ./k8s-install/scripts/render-secrets.sh             # 交互式输入
#   ./k8s-install/scripts/render-secrets.sh --no-input  # 用环境变量注入
#
# 必填环境变量（--no-input 模式）：
#   DB_PASSWORD           MySQL im_app 业务账号密码（应用连接用）
#   IM_JWT_SECRET         JWT 共享密钥（≥32 字节，openssl rand -hex 32）
#   MINIO_ACCESS_KEY      MinIO access key
#   MINIO_SECRET_KEY      MinIO secret key
#   REDIS_PASSWORD        Redis 密码（生产必填；无密码留空字符串）

set -euo pipefail

NO_INPUT=false
for arg in "$@"; do
  case "$arg" in
    --no-input) NO_INPUT=true ;;
  esac
done

prompt() {
  local var_name="$1"
  local prompt_text="$2"
  local default_val="${3:-}"
  if [[ -n "${!var_name:-}" ]]; then
    echo "  $var_name = (from env)" >&2
  else
    if $NO_INPUT; then
      echo "ERROR: env $var_name is required in --no-input mode" >&2
      exit 1
    fi
    if [[ -n "$default_val" ]]; then
      read -r -p "$prompt_text [$default_val]: " val
      val="${val:-$default_val}"
    else
      read -r -s -p "$prompt_text: " val
      echo "" >&2
    fi
    eval "$var_name=\$val"
    export "$var_name"
  fi
}

echo "==> Collecting secret values..." >&2
prompt DB_PASSWORD           "MySQL im_app password (app)"
prompt IM_JWT_SECRET         "JWT shared secret (>=32 bytes, hex)"
prompt MINIO_ACCESS_KEY      "MinIO access key"      "minioadmin"
prompt MINIO_SECRET_KEY      "MinIO secret key"      "minioadmin"
prompt REDIS_PASSWORD        "Redis password (prod required, leave empty for dev)"

# 验证 JWT 长度
if (( ${#IM_JWT_SECRET} < 32 )); then
  echo "ERROR: IM_JWT_SECRET must be at least 32 bytes (HS256 requirement)" >&2
  exit 1
fi

cat <<EOF
---
apiVersion: v1
kind: Secret
metadata:
  name: im-middleware-secret
  namespace: im
type: Opaque
stringData:
  # MySQL (8.153.38.116:3306) —— im-user / im-message 引用
  DB_PASSWORD: "${DB_PASSWORD}"
  # MinIO (8.153.38.116:9000) —— im-media / im-message / im-push 引用
  MINIO_ACCESS_KEY: "${MINIO_ACCESS_KEY}"
  MINIO_SECRET_KEY: "${MINIO_SECRET_KEY}"
  # Redis (8.153.38.116:6379) —— im-connect / im-message 引用（无密码留空）
  REDIS_PASSWORD: "${REDIS_PASSWORD}"
---
apiVersion: v1
kind: Secret
metadata:
  name: im-jwt-secret
  namespace: im
type: Opaque
stringData:
  # JWT 共享密钥：im-user / im-connect / im-gateway 三者必须用同一值
  IM_JWT_SECRET: "${IM_JWT_SECRET}"
EOF
