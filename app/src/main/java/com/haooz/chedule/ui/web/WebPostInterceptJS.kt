package com.haooz.chedule.ui.web

/**
 * 注入到 WebView 的 JavaScript 脚本
 * 拦截 XHR、Fetch 和 Form 的 POST 请求
 * 将请求体注册到 Native 端，以便 WebViewRequestInterceptor 转发
 */
val JS_INTERCEPT_POST = """
(function() {
    var bridge = window.AndroidBridge;
    if (!bridge || window._postInterceptInjected) return;
    window._postInterceptInjected = true;

    var requestIdHeader = 'X-WebView-Post-Id';
    var requestIdParam = '_webview_post_id';

    function register(id, body, contentType) {
    if (window.WebPostService) {
        window.WebPostService.register(id, body, contentType || '');
    }
}

    // --- 1. XMLHttpRequest 拦截 ---
    var oldOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._method = method;
        this._url = url;
        this._headers = {};
        return oldOpen.apply(this, arguments);
    };

    var oldSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
        this._headers[header] = value;
        if (header.toLowerCase() === 'x-requested-with') return;
        return oldSetRequestHeader.apply(this, arguments);
    };

    var oldSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(body) {
        if (this._method && this._method.toUpperCase() !== 'GET' && body) {
            var id = 'xhr_' + Date.now() + '_' + Math.random().toString(36).substr(2);
            var contentType = this._headers['Content-Type'] || this._headers['content-type'];

            var bodyStr = '';
            if (typeof body === 'string') {
                bodyStr = body;
            } else if (body instanceof FormData) {
                var params = new URLSearchParams();
                for (var pair of body.entries()) {
                    params.append(pair[0], pair[1]);
                }
                bodyStr = params.toString();
                contentType = 'application/x-www-form-urlencoded';
            } else if (body instanceof URLSearchParams) {
                bodyStr = body.toString();
                contentType = 'application/x-www-form-urlencoded';
            }

            if (bodyStr) {
                register(id, bodyStr, contentType);
                this.setRequestHeader(requestIdHeader, id);
            }
        }
        return oldSend.apply(this, arguments);
    };

    // --- 2. Fetch API 拦截 ---
    if (window.fetch) {
        var oldFetch = window.fetch;
        window.fetch = function(input, init) {
            if (init && init.method && init.method.toUpperCase() !== 'GET' && init.body) {
                var id = 'fetch_' + Date.now() + '_' + Math.random().toString(36).substr(2);
                var contentType = '';

                if (init.headers) {
                    if (init.headers instanceof Headers) {
                        contentType = init.headers.get('Content-Type');
                    } else if (Array.isArray(init.headers)) {
                        var ct = init.headers.find(h => h[0].toLowerCase() === 'content-type');
                        if (ct) contentType = ct[1];
                    } else {
                        contentType = init.headers['Content-Type'] || init.headers['content-type'];
                    }
                }

                var bodyStr = '';
                if (typeof init.body === 'string') {
                    bodyStr = init.body;
                } else if (init.body instanceof URLSearchParams) {
                    bodyStr = init.body.toString();
                    if (!contentType) contentType = 'application/x-www-form-urlencoded';
                }

                if (bodyStr) {
                    register(id, bodyStr, contentType);

                    if (!init.headers) init.headers = {};
                    if (init.headers instanceof Headers) {
                        init.headers.set(requestIdHeader, id);
                    } else if (Array.isArray(init.headers)) {
                        init.headers.push([requestIdHeader, id]);
                    } else {
                        init.headers[requestIdHeader] = id;
                    }
                }
            }
            return oldFetch.apply(this, arguments);
        };
    }

    // --- 3. 表单提交拦截 ---
    document.addEventListener('submit', function(e) {
        var form = e.target;
        if (form.tagName.toLowerCase() !== 'form') return;
        if (form.method.toLowerCase() !== 'post') return;

        if (form.querySelector('input[type="file"]')) return;

        var id = 'form_' + Date.now() + '_' + Math.random().toString(36).substr(2);
        var formData = new FormData(form);

        var submitter = e.submitter || document.activeElement;
        if (submitter && submitter.form === form && submitter.name) {
            formData.append(submitter.name, submitter.value);
        }

        var params = new URLSearchParams(formData);
        register(id, params.toString(), 'application/x-www-form-urlencoded');

        var action = form.getAttribute('action') || window.location.href;
        var separator = action.indexOf('?') !== -1 ? '&' : '?';
        form.setAttribute('action', action + separator + requestIdParam + '=' + id);
    }, true);

    console.log('Unified X-Requested-With interceptor active');
})();
""".trimIndent()
