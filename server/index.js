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
// 仪表盘访问口令。未设置时查看接口不鉴权；设置后需通过 ?token= 或 Authorization: Bearer 提交
const DASH_TOKEN = process.env.DASH_TOKEN || '';

/** 校验查看类接口的访问口令（report 上报接口不在此列，App 直连无需口令） */
function isAuthorized(url, headers) {
  if (!DASH_TOKEN) return true;
  const queryToken = url.searchParams.get('token');
  if (queryToken === DASH_TOKEN) return true;
  const auth = headers['authorization'] || '';
  if (auth.startsWith('Bearer ') && auth.slice(7) === DASH_TOKEN) return true;
  return false;
}

function denied(res) {
  return json(res, 401, { error: 'Unauthorized: 需要访问口令' });
}

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

/** 网页版统计仪表盘（同源访问，无需跨域） */
const DASHBOARD_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Nexio 统计仪表盘</title>
<style>
  :root { --bg:#0f1115; --card:#181b21; --line:#262b33; --text:#e8eaed; --sub:#9aa0a6; --acc:#4f8cff; --good:#34b37d; }
  * { box-sizing:border-box; margin:0; padding:0; }
  body { background:var(--bg); color:var(--text); font-family:-apple-system,"PingFang SC","Microsoft YaHei",Segoe UI,Roboto,sans-serif; padding:24px; }
  h1 { font-size:20px; margin-bottom:4px; }
  .sub { color:var(--sub); font-size:12px; margin-bottom:20px; }
  .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:12px; margin-bottom:20px; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:16px; }
  .card .num { font-size:30px; font-weight:700; margin-top:6px; }
  .card .lbl { color:var(--sub); font-size:12px; }
  .panel { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:16px; margin-bottom:20px; }
  .panel h2 { font-size:14px; margin-bottom:12px; color:var(--text); }
  .row { display:flex; align-items:center; gap:10px; margin-bottom:8px; font-size:13px; }
  .row .name { width:150px; color:var(--sub); flex:none; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .row .bar-bg { flex:1; height:14px; background:#0a0c10; border-radius:7px; overflow:hidden; }
  .row .bar { height:100%; background:linear-gradient(90deg,#4f8cff,#8a5fff); border-radius:7px; min-width:2px; transition:width .5s; }
  .row .val { width:40px; text-align:right; color:var(--text); flex:none; }
  table { width:100%; border-collapse:collapse; font-size:12px; }
  th,td { text-align:left; padding:8px 10px; border-bottom:1px solid var(--line); white-space:nowrap; }
  th { color:var(--sub); font-weight:500; }
  .err { color:#ff6b6b; }
  .tick { color:var(--good); }
  .mask { position:fixed; inset:0; background:var(--bg); display:flex; align-items:center; justify-content:center; z-index:9; }
  .login { background:var(--card); border:1px solid var(--line); border-radius:14px; padding:28px; width:min(320px,90vw); }
  .login h2 { font-size:16px; margin-bottom:6px; }
  .login p { color:var(--sub); font-size:12px; margin-bottom:16px; }
  .login input { width:100%; padding:11px 12px; border-radius:9px; border:1px solid var(--line); background:#0a0c10; color:var(--text); font-size:14px; box-sizing:border-box; }
  .login button { width:100%; margin-top:12px; padding:11px; border:none; border-radius:9px; background:var(--acc); color:#fff; font-size:14px; cursor:pointer; }
  .login .err { font-size:12px; margin-top:10px; min-height:16px; }
</style>
</head>
<body>
  <div class="mask" id="mask">
    <div class="login">
      <h2>访问受限</h2>
      <p>请输入仪表盘访问口令</p>
      <input type="password" id="tok" placeholder="访问口令" autocomplete="current-password">
      <button onclick="submitLogin()">进入</button>
      <div class="err" id="l-err"></div>
    </div>
  </div>
  <h1>Nexio 统计仪表盘</h1>
  <div class="sub" id="status">加载中…</div>
  <div class="grid">
    <div class="card"><div class="lbl">总下载</div><div class="num" id="c-downloads">-</div></div>
    <div class="card"><div class="lbl">设备/用户数</div><div class="num" id="c-devices">-</div></div>
    <div class="card"><div class="lbl">今日活跃</div><div class="num" id="c-today">-</div></div>
    <div class="card"><div class="lbl">昨日活跃</div><div class="num" id="c-yesterday">-</div></div>
  </div>
  <div class="panel"><h2>机型分布</h2><div id="p-models"><div class="sub">暂无数据</div></div></div>
  <div class="panel"><h2>安卓版本分布</h2><div id="p-android"><div class="sub">暂无数据</div></div></div>
  <div class="panel"><h2>App 版本分布</h2><div id="p-versions"><div class="sub">暂无数据</div></div></div>
  <div class="panel"><h2>设备 / 用户明细</h2><div id="p-devices"><div class="sub">暂无数据</div></div></div>
<script>
function el(id){ return document.getElementById(id); }
var TOKEN = '';
try { TOKEN = sessionStorage.getItem('dash_token') || ''; } catch(e){}
function hideLogin(){ if(el('mask')) el('mask').style.display='none'; }
function showLogin(){ if(el('mask')) el('mask').style.display='flex'; }
function submitLogin(){
  var t = (el('tok').value||'').trim();
  if(!t){ el('l-err').textContent='请输入口令'; return; }
  TOKEN = t;
  load(true);
}
function api(path){
  return fetch(path + (TOKEN ? (path.indexOf('?')<0?'?':'&')+'token='+encodeURIComponent(TOKEN) : ''), { cache:'no-store' })
    .then(function(r){ if(r.status===401){ if(TOKEN){ TOKEN=''; try{sessionStorage.removeItem('dash_token')}catch(e){} showLogin(); } throw new Error('口令无效或未授权'); } return r.json(); });
}
function barRows(obj){
  var keys = Object.keys(obj || {});
  if(!keys.length) return '<div class="sub">暂无数据</div>';
  var max = Math.max.apply(null, keys.map(function(k){return obj[k];}));
  keys.sort(function(a,b){return obj[b]-obj[a];});
  var html = '';
  keys.forEach(function(k){
    html += '<div class="row"><span class="name" title="'+k+'">'+k+'</span>' +
            '<div class="bar-bg"><div class="bar" style="width:'+((obj[k]/max)*100).toFixed(1)+'%"></div></div>' +
            '<span class="val">'+obj[k]+'</span></div>';
  });
  return html;
}
function fmtTime(t){
  if(!t) return '未知';
  try{ var d=new Date(t); return d.toLocaleString('zh-CN',{hour12:false}); }catch(e){ return t; }
}
function esc(s){ return String(s==null?'' : s).replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];}); }
function devicesTable(list){
  if(!list || !list.length) return '<div class="sub">暂无数据</div>';
  var html='<table><thead><tr><th>机型</th><th>安卓版本</th><th>App 版本</th><th>最后活跃</th></tr></thead><tbody>';
  list.forEach(function(d){
    html+='<tr><td>'+esc(d.device_model||'未知')+'</td><td>'+esc(d.android_version||'-')+'</td><td>'+esc(d.app_version||'-')+'</td><td>'+fmtTime(d.last_activity_at)+'</td></tr>';
  });
  html+='</tbody></table>';
  return html;
}
function load(fromLogin){
  var st = el('status');
  if(!TOKEN){ showLogin(); return; }
  if(fromLogin){ try{ sessionStorage.setItem('dash_token', TOKEN); }catch(e){} hideLogin(); }
  Promise.all([
    api('/api/stats/dashboard'),
    api('/api/stats/devices')
  ]).then(function(rs){
    var d=rs[0], dev=rs[1];
    el('c-downloads').textContent=d.total_downloads;
    el('c-devices').textContent=d.unique_devices;
    el('c-today').textContent=d.today_active;
    el('c-yesterday').textContent=d.yesterday_active;
    el('p-models').innerHTML=barRows(d.device_models);
    el('p-android').innerHTML=barRows(d.android_versions);
    el('p-versions').innerHTML=barRows(d.app_versions);
    el('p-devices').innerHTML=devicesTable(dev.devices);
    st.innerHTML='最近更新：'+fmtTime(d.last_updated)+' <span class="tick">&#10003;</span>';
  }).catch(function(e){
    st.innerHTML='<span class="err">加载失败：'+esc(e.message)+'</span>';
  });
}
load();
setInterval(load, 30000);
</script>
</body>
</html>`;

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
    if (!isAuthorized(url, req.headers)) return denied(res);
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
    if (!isAuthorized(url, req.headers)) return denied(res);
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
    if (!isAuthorized(url, req.headers)) return denied(res);
    const stats = readStats();
    const devices = Object.values(stats.devices || {}).map((d) => Object.assign({}, d));
    return json(res, 200, { count: devices.length, devices });
  }

  // GET / 或 /dashboard —— 网页版统计仪表盘
  if ((pathname === '/' || pathname === '/dashboard') && method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(DASHBOARD_HTML);
    return;
  }

  return json(res, 404, { error: 'Not Found' });
});

server.listen(PORT, () => {
  console.log(`Nexio 统计服务已启动：http://0.0.0.0:${PORT}`);
  console.log(`数据文件：${STATS_FILE}`);
});