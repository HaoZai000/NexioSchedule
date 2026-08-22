export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;

    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    };

    if (method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    if (path === '/api/stats' && method === 'GET') {
      const stats = await env.STATS.get('app_stats', { type: 'json' }) || {
        total_downloads: 0,
        unique_devices: 0,
        daily_active: {},
        created_at: new Date().toISOString(),
      };
      return Response.json(stats, { headers: corsHeaders });
    }

    if (path === '/api/stats/report' && method === 'POST') {
      try {
        const body = await request.json();
        const { device_id, event_type, timestamp } = body;

        const stats = await env.STATS.get('app_stats', { type: 'json' }) || {
          total_downloads: 0,
          unique_devices: 0,
          daily_active: {},
          created_at: new Date().toISOString(),
        };

        if (event_type === 'install') {
          stats.total_downloads = (stats.total_downloads || 0) + 1;
        }

        if (device_id && !stats.devices) {
          stats.devices = {};
        }
        if (device_id && !stats.devices[device_id]) {
          stats.devices[device_id] = true;
          stats.unique_devices = Object.keys(stats.devices).length;
        }

        const today = new Date().toISOString().slice(0, 10);
        if (!stats.daily_active) stats.daily_active = {};
        if (!stats.daily_active[today]) stats.daily_active[today] = 0;
        stats.daily_active[today]++;

        stats.last_updated = new Date().toISOString();
        await env.STATS.put('app_stats', JSON.stringify(stats));

        return Response.json({ ok: true }, { headers: corsHeaders });
      } catch (e) {
        return Response.json({ error: 'Invalid request' }, { status: 400, headers: corsHeaders });
      }
    }

    if (path === '/api/stats/dashboard' && method === 'GET') {
      const stats = await env.STATS.get('app_stats', { type: 'json' }) || {};
      const today = new Date().toISOString().slice(0, 10);
      const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);

      return Response.json({
        total_downloads: stats.total_downloads || 0,
        unique_devices: stats.unique_devices || 0,
        today_active: (stats.daily_active || {})[today] || 0,
        yesterday_active: (stats.daily_active || {})[yesterday] || 0,
        last_updated: stats.last_updated,
      }, { headers: corsHeaders });
    }

    return Response.json({ error: 'Not Found' }, { status: 404, headers: corsHeaders });
  },
};
