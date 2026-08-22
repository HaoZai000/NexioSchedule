# Nexio Stats Worker

Cloudflare Worker 后端统计 API，用于追踪 App 安装和活跃数据。

## 部署步骤

### 1. 安装 Wrangler CLI
```bash
npm install -g wrangler
```

### 2. 登录 Cloudflare
```bash
wrangler login
```

### 3. 创建 KV 命名空间
```bash
wrangler kv namespace create STATS
```

### 4. 更新 wrangler.toml
将输出的 namespace ID 填入 `wrangler.toml`:
```toml
[kv_namespaces]
binding = "STATS"
id = "这里填入你的 namespace ID"
```

### 5. 部署 Worker
```bash
cd worker
wrangler deploy
```

### 6. 更新 App 中的 API_URL
部署成功后会得到一个 URL，类似：
`https://nexio-stats.your-subdomain.workers.dev`

将 `StatsReporter.kt` 中的 `API_URL` 更新为这个地址。

## API 接口

### POST /api/stats/report
上报统计数据。

**请求体：**
```json
{
  "device_id": "uuid",
  "event_type": "install|active",
  "timestamp": 1234567890,
  "app_version": "1.4.1",
  "device_model": "Xiaomi 14",
  "android_version": "15",
  "sdk_level": 35
}
```

### GET /api/stats
获取完整统计数据。

### GET /api/stats/dashboard
获取仪表盘数据（今日活跃、昨日活跃、总下载、总设备数）。

## 数据结构

```json
{
  "total_downloads": 1234,
  "unique_devices": 567,
  "daily_active": {
    "2026-08-22": 89,
    "2026-08-21": 76
  },
  "last_updated": "2026-08-22T10:00:00Z"
}
```

## 在 App 中集成

1. 在 `Application.onCreate()` 中调用 `StatsReporter.init(this)`
2. 首次安装时调用 `StatsReporter.reportInstall(this)`
3. 每次打开 App 时调用 `StatsReporter.reportActive(this)`
