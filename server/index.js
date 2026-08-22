'use strict';

/**
 * Nexio 课程表 - 使用量统计后端（Node.js 零依赖版本）
 * 从 Cloudflare Worker 改写，数据持久化到本地 JSON 文件。
 *
 * 运行：node index.js   默认端口 3000
 * 环境变量 PORT 可覆盖端口；STATS_FILE 可覆盖数据文件路径。
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.PORT, 10) || 3000;
const STATS_FILE = process.env.STATS_FILE || path.join(__dirname, 'stats.json');

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

const DEFAULT_STATS = () => ({
  total_downloads: 0,
  unique_devices: 0,
  devices: {},       // device_id -> 设备/用户画像（模型、系统版本、应用版本等）
  daily_active: {},  // YYYY-MM-DD -> 活跃数
  created_at: new Date().toISOString(),
});

/** 原子读取统计数据，损坏时回退为默认值 */
function readStats() {
  try {
    if (!fs.existsSync(STATS_FILE)) return DEFAULT_STATS();
    const raw = fs.readFileSync(STATS_FILE, 'utf8');
    const data = JSON.parse(raw);
    // 兼容旧数据补齐字段
    data.devices = data.devices || {};
    data.daily_active = data.daily_active || {};
    return data;
  } catch (_) {
    console.error('[stats] 数据文件损坏，已重置：', STATS_FILE);
    return DEFAULT_STATS();
  }
}

/** 原子写入统计数据（临时文件 + rename，避免写一半损坏） */
function writeStats(stats) {
  const tmp = `${STATS_FILE}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(stats, null, 2));
  fs.renameSync(tmp, STATS_FILE);
}

function json(res, status, body) {
  res.writeHead(status, Object.assign({ 'Content-Type': 'application/json; charset=utf-8' }, CORS_HEADERS));
  res.end(JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => {
      data += chunk;
      // 简单限制请求体大小，防止异常请求
      if (data.length > 1e6) reject(new Error('payload too large'));
    });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;
  const method = (req.method || '').toUpperCase();

  // CORS 预检
  if (method === 'OPTIONS') {
    res.writeHead(204, CORS_HEADERS);
    res.end();
    return;
  }

  // GET /api/stats —— 完整统计数据
  if (pathname === '/api/stats' && method === 'GET') {
    return json(res, 200, readStats());
  }

  // POST /api/stats/report —— 上报（install / active）
  if (pathname === '/api/stats/report' && method === 'POST') {
    try {
      const raw = await readBody(req);
      const body = JSON.parse(raw);
      const { device_id, event_type, timestamp } = body;

      const stats = readStats();

      if (event_type === 'install') {
        stats.total_downloads = (stats.total_downloads || 0) + 1;
      }

      // 设备/用户画像：记录该设备连续上报的设备与应用信息
      if (device_id) {
        const profile = stats.devices[device_id] || { first_seen: timestamp || Date.now() };
        ['app_version', 'device_model', 'android_version', 'sdk_level'].forEach((k) => {
          if (body[k] !== undefined) profile[k] = body[k];
        });
        profile.last_seen = timestamp || Date.now();
        profile.last_activity_at = new Date().toISOString();
        if (!stats.devices[device_id]) {
          stats.devices[device_id] = profile;
          stats.unique_devices = Object.keys(stats.devices).length;
        } else {
          stats.devices[device_id] = profile;
        }
      }

      const today = new Date().toISOString().slice(0, 10);
      stats.daily_active[today] = (stats.daily_active[today] || 0) + 1;

      stats.last_updated = new Date().toISOString();
      writeStats(stats);

      return json(res, 200, { ok: true });
    } catch (e) {
      console.error('[stats] 上报失败：', e.message);
      return json(res, 400, { error: 'Invalid request' });
    }
  }

  // GET /api/stats/dashboard —— 仪表盘聚合数据
  if (pathname === '/api/stats/dashboard' && method === 'GET') {
    const stats = readStats();
    const today = new Date().toISOString().slice(0, 10);
    const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);

    // 设备画像概览（各机型 / 各安卓版本 / 各应用版本分布）
    const devices = Object.values(stats.devices || {});
    const deviceModelStats = {};
    const androidStats = {};
    const appVersionStats = {};
    devices.forEach((d) => {
      if (d.device_model) deviceModelStats[d.device_model] = (deviceModelStats[d.device_model] || 0) + 1;
      if (d.android_version) androidStats[d.android_version] = (androidStats[d.android_version] || 0) + 1;
      if (d.app_version) appVersionStats[d.app_version] = (appVersionStats[d.app_version] || 0) + 1;
    });

    return json(res, 200, {
      total_downloads: stats.total_downloads || 0,
      unique_devices: stats.unique_devices || 0,
      today_active: (stats.daily_active || {})[today] || 0,
      yesterday_active: (stats.daily_active || {})[yesterday] || 0,
      last_updated: stats.last_updated,
      device_models: deviceModelStats,
      android_versions: androidStats,
      app_versions: appVersionStats,
    });
  }

  // GET /api/stats/devices —— 详细设备/用户列表
  if (pathname === '/api/stats/devices' && method === 'GET') {
    const stats = readStats();
    const devices = Object.values(stats.devices || {}).map((d) => Object.assign({}, d));
    return json(res, 200, { count: devices.length, devices });
  }

  return json(res, 404, { error: 'Not Found' });
});

server.listen(PORT, () => {
  console.log(`Nexio 统计服务已启动：http://0.0.0.0:${PORT}`);
  console.log(`数据文件：${STATS_FILE}`);
});