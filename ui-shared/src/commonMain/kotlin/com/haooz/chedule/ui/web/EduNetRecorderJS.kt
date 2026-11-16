/** AI 教务导入的页面网络记录器 */
package com.haooz.chedule.ui.web

/**
 * 注入到 WebView 的 document-start 脚本：记录页面自身的 fetch / XHR / 表单请求。
 *
 * 为什么必须 document-start 注入：教务系统的课表接口往往在页面首屏脚本里就发出了，
 * 等 `onPageFinished` 再挂钩子只能看到之后的请求。
 *
 * 记录内容会截断并脱敏，经 `window.AndroidBridge.netLog` 交给原生侧环形缓冲；
 * 桥还没就绪时先暂存，随后重试。只记录同源或 CORS 可读的响应，opaque 响应只留状态。
 */
val EDU_NET_RECORDER_JS: String = """
(function () {
  if (window.__nexioNetRec) return;
  var MAX_BODY = 32768;
  var MAX_PARSE = 524288;
  var MAX_PENDING = 200;
  var SENSITIVE = /password|passwd|pwd|token|ticket|cookie|secret|auth|session|sign/i;
  var STATIC_EXTS = ',js,css,png,jpg,jpeg,gif,svg,ico,woff,woff2,ttf,otf,map,mp4,mp3,webp,';
  var rec = { seq: 0, pending: [] };
  window.__nexioNetRec = rec;

  function now() { return Date.now(); }

  function clip(s, n) {
    var t = String(s == null ? '' : s);
    return t.length > n ? t.slice(0, n) + '\n（已截断，原文共 ' + t.length + ' 字）' : t;
  }

  function redactUrl(raw) {
    try {
      var u = new URL(raw, location.href);
      var keys = Array.from(u.searchParams.keys());
      for (var i = 0; i < keys.length; i++) {
        if (SENSITIVE.test(keys[i])) u.searchParams.set(keys[i], '***');
      }
      return u.href;
    } catch (e) {
      return String(raw == null ? '' : raw).slice(0, 500);
    }
  }

  function redactJson(value) {
    if (Array.isArray(value)) {
      var arr = [];
      for (var i = 0; i < value.length; i++) arr.push(redactJson(value[i]));
      return arr;
    }
    if (value && typeof value === 'object') {
      var out = {};
      for (var k in value) out[k] = SENSITIVE.test(k) ? '***' : redactJson(value[k]);
      return out;
    }
    return value;
  }

  function redactBody(text) {
    var s = String(text == null ? '' : text);
    if (!s) return '';
    try {
      return JSON.stringify(redactJson(JSON.parse(s)));
    } catch (e) {
      return s.replace(
        /(password|passwd|pwd|token|ticket|cookie|secret|session|sign)=([^&\s"']+)/gi,
        function (m, k) { return k + '=***'; }
      );
    }
  }

  function keysOf(text) {
    try {
      var v = JSON.parse(text);
      if (Array.isArray(v)) v = v[0];
      if (v && typeof v === 'object') return Object.keys(v).slice(0, 24);
    } catch (e) {}
    return [];
  }

  function hasStaticExt(url) {
    var path = String(url == null ? '' : url).split('?')[0].split('#')[0].toLowerCase();
    var dot = path.lastIndexOf('.');
    if (dot < 0) return false;
    var ext = path.substring(dot + 1);
    return ext.length > 0 && STATIC_EXTS.indexOf(',' + ext + ',') >= 0;
  }

  function isStatic(url, type) {
    if (hasStaticExt(url)) return true;
    var t = String(type == null ? '' : type).toLowerCase();
    return t.indexOf('image/') === 0 || t.indexOf('font/') === 0 ||
      t.indexOf('video/') === 0 || t.indexOf('audio/') === 0 ||
      t.indexOf('text/css') === 0 || t.indexOf('javascript') >= 0;
  }

  function isTop() {
    try { return window.top === window; } catch (e) { return false; }
  }

  function bridge() {
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.netLog === 'function') {
        return window.AndroidBridge;
      }
    } catch (e) {}
    return null;
  }

  function flush() {
    if (!rec.pending.length) return;
    var b = bridge();
    if (!b) return;
    var list = rec.pending;
    rec.pending = [];
    for (var i = 0; i < list.length; i++) {
      try { b.netLog(list[i]); } catch (e) {}
    }
  }

  function send(entry) {
    entry.id = 'n' + (++rec.seq);
    if (!entry.ts) entry.ts = now();
    entry.frame = location.href;
    entry.top = isTop();
    var json = JSON.stringify(entry);
    var b = bridge();
    if (b) {
      try { b.netLog(json); return; } catch (e) {}
    }
    // iOS 子 frame 没有伪造的 AndroidBridge，但 messageHandlers 在所有 frame 都可用。
    // handler 名与 IosWebHost 的 HANDLER_NAME 是契约。
    try {
      if (window.webkit && window.webkit.messageHandlers &&
          window.webkit.messageHandlers.NexioEduBridge) {
        window.webkit.messageHandlers.NexioEduBridge.postMessage({ m: 'netLog', a: [json] });
        return;
      }
    } catch (e) {}
    rec.pending.push(json);
    if (rec.pending.length > MAX_PENDING) rec.pending.shift();
  }

  function finish(kind, method, url, status, type, reqBody, respBody, error) {
    if (isStatic(url, type)) return;
    var raw = String(respBody == null ? '' : respBody);
    // 大响应先截断再脱敏：直接 parse 几 MB 的 JSON 会在页面线程上卡住
    var parse = raw.length <= MAX_PARSE;
    var body = raw ? (parse ? clip(redactBody(raw), MAX_BODY) : redactBody(clip(raw, MAX_BODY))) : '';
    var entry = {
      kind: kind,
      method: String(method == null ? 'GET' : method).toUpperCase(),
      url: redactUrl(url),
      status: Number(status) || 0,
      type: String(type == null ? '' : type),
      contentType: String(type == null ? '' : type),
      reqBody: reqBody ? clip(redactBody(reqBody), MAX_BODY) : '',
      respBody: body,
      json: false,
      keys: [],
      size: raw.length
    };
    if (raw && parse) {
      try { JSON.parse(raw); entry.json = true; entry.keys = keysOf(raw); } catch (e) {}
    }
    if (error) entry.error = clip(String(error), 300);
    send(entry);
  }

  // 文档标记：RELOAD_PAGE 靠它判断新文档已经起来了
  send({
    kind: 'document', method: 'GET', url: redactUrl(location.href),
    status: 0, type: '', contentType: '', reqBody: '', respBody: '',
    json: false, keys: [], size: 0
  });

  // ── fetch ──
  if (window.fetch) {
    var oldFetch = window.fetch;
    window.fetch = function (input, init) {
      // AI 自己的 REQUEST 工具已经拿到过这份响应，不必重复记录
      if (window.__nexioNetSkip) return oldFetch.apply(this, arguments);
      var url = '';
      var method = 'GET';
      var body = '';
      try {
        url = typeof input === 'string' ? input : (input && input.url) || String(input);
        method = (init && init.method) || (input && input.method) || 'GET';
        var b = init && init.body;
        if (typeof b === 'string') body = b;
        else if (b && typeof URLSearchParams !== 'undefined' && b instanceof URLSearchParams) body = b.toString();
        else if (b && typeof FormData !== 'undefined' && b instanceof FormData) {
          var pairs = [];
          b.forEach(function (v, k) { if (!SENSITIVE.test(k)) pairs.push(k + '=' + v); });
          body = pairs.join('&');
        } else if (b) body = '[binary]';
      } catch (e) {}

      var promise = oldFetch.apply(this, arguments);
      promise.then(function (res) {
        try {
          if (isStatic(url, res.headers.get('content-type'))) return;
          res.clone().text().then(function (text) {
            finish('fetch', method, url, res.status, res.headers.get('content-type') || '',
              body, text, null);
          }).catch(function (e) {
            finish('fetch', method, url, res.status, res.headers.get('content-type') || '',
              body, '', '响应读取失败：' + (e && e.message ? e.message : e));
          });
        } catch (e) {
          finish('fetch', method, url, res.status || 0, '', body, '', e && e.message);
        }
      }).catch(function (e) {
        finish('fetch', method, url, 0, '', body, '', '请求失败：' + (e && e.message ? e.message : e));
      });
      return promise;
    };
  }

  // ── XMLHttpRequest ──
  if (window.XMLHttpRequest) {
    var oldOpen = XMLHttpRequest.prototype.open;
    var oldSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__nexioNet = { method: method, url: String(url == null ? '' : url) };
      return oldOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function (body) {
      var meta = this.__nexioNet || { method: 'GET', url: '' };
      var reqBody = '';
      try {
        if (typeof body === 'string') reqBody = body;
        else if (body && typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) reqBody = body.toString();
        else if (body && typeof FormData !== 'undefined' && body instanceof FormData) {
          var pairs = [];
          body.forEach(function (v, k) { if (!SENSITIVE.test(k)) pairs.push(k + '=' + v); });
          reqBody = pairs.join('&');
        } else if (body) reqBody = '[binary]';
      } catch (e) {}
      var xhr = this;
      xhr.addEventListener('loadend', function () {
        try {
          var type = xhr.getResponseHeader('content-type') || '';
          var text = '';
          if (!xhr.responseType || xhr.responseType === 'text') text = xhr.responseText || '';
          else if (xhr.responseType === 'json') text = JSON.stringify(xhr.response || null);
          finish('xhr', meta.method, xhr.responseURL || meta.url, xhr.status, type, reqBody, text,
            xhr.status === 0 ? '请求未完成（网络或跨域）' : null);
        } catch (e) {
          finish('xhr', meta.method, meta.url, 0, '', reqBody, '', e && e.message);
        }
      });
      return oldSend.apply(this, arguments);
    };
  }

  // ── 表单提交（响应不可读，只记请求本身）──
  document.addEventListener('submit', function (event) {
    try {
      var form = event.target;
      if (!form || String(form.tagName).toLowerCase() !== 'form') return;
      var method = String(form.method || 'get').toUpperCase();
      if (method === 'GET') return;
      var pairs = [];
      new FormData(form).forEach(function (v, k) {
        if (!SENSITIVE.test(k)) pairs.push(k + '=' + v);
      });
      finish('form', method, form.action || location.href, 0, '', pairs.join('&'), '', null);
    } catch (e) {}
  }, true);

  setInterval(flush, 1000);
  window.addEventListener('load', flush);
})();
""".trimIndent()
