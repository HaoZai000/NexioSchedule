/* ===================================================================
   Nexio 课程表 · 官网脚本（首页专用；法务页自带内联脚本，不引用本文件）
   =================================================================== */
(function () {
  'use strict';

  var root = document.documentElement;
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var fine = window.matchMedia('(pointer: fine)').matches;
  var $ = function (s, c) { return (c || document).querySelector(s); };
  var $$ = function (s, c) { return Array.prototype.slice.call((c || document).querySelectorAll(s)); };
  var raf = function (fn) { var q = false; return function () { if (q) return; q = true; requestAnimationFrame(function () { q = false; fn(); }); }; };
  var clamp = function (v, a, b) { return Math.min(b, Math.max(a, v)); };

  /* ==================== 主题 ==================== */
  var toggle = $('#theme-toggle');
  var meta = document.querySelector('meta[name="theme-color"]');

  function paint(t) {
    root.setAttribute('data-theme', t);
    try { localStorage.setItem('theme', t); } catch (e) {}
    if (meta) meta.setAttribute('content', t === 'dark' ? '#070c15' : '#e9eff8');
    if (toggle) {
      toggle.setAttribute('aria-label', t === 'dark' ? '切换为浅色主题' : '切换为深色主题');
      toggle.setAttribute('aria-pressed', String(t === 'dark'));
    }
  }

  function setTheme(t, ox, oy) {
    var cur = root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    if (cur === t) return;

    if (!document.startViewTransition || reduce) { paint(t); return; }

    var x = typeof ox === 'number' ? ox : window.innerWidth - 40;
    var y = typeof oy === 'number' ? oy : 40;
    var r = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y));
    root.style.setProperty('--vt-x', x + 'px');
    root.style.setProperty('--vt-y', y + 'px');
    root.style.setProperty('--vt-r', r + 'px');
    root.classList.add('vt');
    try {
      var vt = document.startViewTransition(function () { paint(t); });
      vt.finished.finally(function () { root.classList.remove('vt'); });
    } catch (e) {
      root.classList.remove('vt');
      paint(t);
    }
  }

  if (toggle) {
    paint(root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');
    toggle.addEventListener('click', function (e) {
      var r = toggle.getBoundingClientRect();
      var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      setTheme(next, r.left + r.width / 2, r.top + r.height / 2);
    });
  }

  /* ==================== GitHub 取数 ==================== */
  (function () {
    var starsEl = $('#stars-count');
    var dlEl = $('#downloads-count');
    var verEls = [$('#app-version'), $('#app-version-2')];
    var sizeEl = $('#apk-size');
    if (!starsEl && !dlEl && !verEls[0] && !verEls[1]) return;

    var KEY = 'gh_stats_v2';
    var TTL = 30 * 60 * 1000;

    function fmt(n) {
      if (n >= 10000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
      return n.toLocaleString('en-US');
    }

    function render(d) {
      var row = document.querySelector('.stats');
      if (row) { row.hidden = false; row.classList.add('show'); }
      $$('[data-stat]').forEach(function (el) { el.hidden = false; });
      if (starsEl) starsEl.textContent = fmt(d.stars);
      if (dlEl) dlEl.textContent = fmt(d.downloads);
      if (d.tag) verEls.forEach(function (el) { if (el) el.textContent = d.tag; });
      if (sizeEl && d.size) {
        sizeEl.textContent = ' · ' + (d.size / 1048576).toFixed(1) + ' MB';
        sizeEl.hidden = false;
      }
    }

    try {
      var cached = JSON.parse(sessionStorage.getItem(KEY));
      if (cached && Date.now() - cached.ts < TTL) { render(cached); return; }
    } catch (e) {}

    var ctrl = window.AbortController ? new AbortController() : null;
    var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, 6000) : null;

    fetch('https://api.github.com/repos/HaoZai000/NexioSchedule', ctrl ? { signal: ctrl.signal } : {})
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function (repo) {
        return fetch('https://api.github.com/repos/HaoZai000/NexioSchedule/releases', ctrl ? { signal: ctrl.signal } : {})
          .then(function (r) { return r.ok ? r.json() : []; })
          .then(function (rel) {
            var dl = 0;
            if (Array.isArray(rel)) rel.forEach(function (x) { (x.assets || []).forEach(function (a) { dl += a.download_count || 0; }); });
            var tag = Array.isArray(rel) && rel[0] && rel[0].tag_name ? rel[0].tag_name : '';
            var size = 0;
            if (Array.isArray(rel) && rel[0]) {
              (rel[0].assets || []).forEach(function (a) {
                if (/\.apk$/i.test(a.name || '') && (a.size || 0) > size) size = a.size;
              });
            }
            var data = { stars: repo.stargazers_count || 0, downloads: dl, tag: tag, size: size, ts: Date.now() };
            render(data);
            try { sessionStorage.setItem(KEY, JSON.stringify(data)); } catch (e) {}
          });
      })
      .catch(function () {
        // 取不到就只留静态数字（248 所高校 / 13+ 模块），别把「—」留在首屏
        $$('[data-stat]').forEach(function (el) { el.parentNode && el.parentNode.removeChild(el); });
        var row = document.querySelector('.stats');
        if (row) row.classList.add('show');
      })
      .finally(function () { if (timer) clearTimeout(timer); });
  })();

  /* ==================== 滚动状态 ==================== */
  var navbar = $('#navbar');
  var backTop = $('#back-to-top');
  var sections = $$('main section[id]');
  var navLinks = $$('[data-nav]');
  var dockLinks = $$('[data-dock]');

  var onScroll = raf(function () {
    var y = window.scrollY;
    if (navbar) navbar.classList.toggle('scrolled', y > 24);
    if (backTop) backTop.classList.toggle('visible', y > 620);

    var mid = window.innerHeight * 0.38;
    var active = '';
    sections.forEach(function (s) {
      var r = s.getBoundingClientRect();
      if (r.top <= mid && r.bottom > mid) active = s.id;
    });
    navLinks.forEach(function (a) { a.classList.toggle('on', a.getAttribute('data-nav') === active); });
    dockLinks.forEach(function (a) { a.classList.toggle('on', a.getAttribute('data-dock') === active); });
  });

  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  // 顶部哨兵：程序化滚动 / 回弹时也可靠地切换导航玻璃态
  if ('IntersectionObserver' in window && navbar) {
    var sentinel = document.createElement('div');
    sentinel.setAttribute('aria-hidden', 'true');
    sentinel.style.cssText = 'position:absolute;top:64px;left:0;width:1px;height:1px;pointer-events:none;opacity:0';
    document.body.appendChild(sentinel);
    new IntersectionObserver(function (es) {
      navbar.classList.toggle('scrolled', !es[0].isIntersecting);
    }, { threshold: 0 }).observe(sentinel);
  }

  if (backTop) {
    backTop.addEventListener('click', function () { window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' }); });
  }

  /* ==================== 逐段入场 ==================== */
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
      });
    }, { threshold: 0.1, rootMargin: '0px 0px -6% 0px' });
    $$('.reveal').forEach(function (el) { io.observe(el); });
  } else {
    $$('.reveal').forEach(function (el) { el.classList.add('in'); });
  }

  /* ==================== 指针：光斑 / 聚光 / 倾斜 / 磁吸 ==================== */
  if (fine && !reduce) {
    var cursor = $('#bg-cursor');
    var mx = window.innerWidth / 2, my = window.innerHeight * 0.4;
    var moveCursor = raf(function () {
      if (cursor) cursor.style.setProperty('--mx', mx + 'px'), cursor.style.setProperty('--my', my + 'px');
    });
    window.addEventListener('pointermove', function (e) {
      mx = e.clientX; my = e.clientY; moveCursor();
    }, { passive: true });

    // 卡片聚光
    document.addEventListener('pointermove', function (e) {
      var card = e.target.closest ? e.target.closest('[data-spot]') : null;
      if (!card) return;
      var r = card.getBoundingClientRect();
      card.style.setProperty('--sx', (e.clientX - r.left) + 'px');
      card.style.setProperty('--sy', (e.clientY - r.top) + 'px');
    }, { passive: true });

    // 首屏倾斜
    var stage = $('[data-tilt]');
    var inner = $('[data-tilt-inner]');
    var phone = $('.stage-phone');
    if (stage && inner) {
      var tx = 0, ty = 0, cx = 0, cy = 0, ticking = false;
      var loop = function () {
        cx += (tx - cx) * 0.12;
        cy += (ty - cy) * 0.12;
        inner.style.transform = 'rotateX(' + cx.toFixed(2) + 'deg) rotateY(' + cy.toFixed(2) + 'deg)';
        // 手机是平面层，用位移跟着指针浮起来（不参与 3D，避免穿模）
        if (phone) {
          phone.style.transform = 'translate3d(' + (cy * 1.6).toFixed(2) + 'px,' + (-cx * 1.3).toFixed(2) + 'px,0) rotate(2.2deg)';
        }
        if (Math.abs(tx - cx) > 0.02 || Math.abs(ty - cy) > 0.02) requestAnimationFrame(loop);
        else ticking = false;
      };
      stage.addEventListener('pointermove', function (e) {
        var r = stage.getBoundingClientRect();
        var px = (e.clientX - r.left) / r.width - 0.5;
        var py = (e.clientY - r.top) / r.height - 0.5;
        ty = px * 10;
        tx = -py * 8;
        if (!ticking) { ticking = true; requestAnimationFrame(loop); }
      });
      stage.addEventListener('pointerleave', function () {
        tx = 0; ty = 0;
        if (!ticking) { ticking = true; requestAnimationFrame(loop); }
      });
    }

    // 磁吸按钮
    $$('[data-magnet]').forEach(function (btn) {
      btn.addEventListener('pointermove', function (e) {
        var r = btn.getBoundingClientRect();
        var dx = (e.clientX - (r.left + r.width / 2)) / r.width;
        var dy = (e.clientY - (r.top + r.height / 2)) / r.height;
        btn.style.transform = 'translate(' + (dx * 6).toFixed(2) + 'px,' + (dy * 4 - 2).toFixed(2) + 'px)';
      });
      btn.addEventListener('pointerleave', function () { btn.style.transform = ''; });
    });
  }

  /* ==================== 课表盘：真实时间 ==================== */
  var PERIODS = [
    ['08:00', '08:45'], ['08:50', '09:35'], ['09:55', '10:40'], ['10:45', '11:30'],
    ['11:35', '12:20'], ['14:00', '14:45'], ['14:50', '15:35'], ['15:50', '16:35'],
    ['16:40', '17:25'], ['18:25', '19:10'], ['19:15', '20:00'], ['20:05', '20:50']
  ];
  var DAY_NAMES = ['一', '二', '三', '四', '五'];

  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function minutesOf(str) { var p = str.split(':'); return +p[0] * 60 + +p[1]; }

  function nowInfo() {
    var d = new Date();
    var dow = d.getDay() === 0 ? 7 : d.getDay(); // 周一=1 ... 周日=7
    var mins = d.getHours() * 60 + d.getMinutes();
    var period = 0, progress = 0;
    for (var i = 0; i < PERIODS.length; i++) {
      var s = minutesOf(PERIODS[i][0]), e = minutesOf(PERIODS[i][1]);
      if (mins >= s && mins < e) { period = i + 1; progress = (mins - s) / (e - s); break; }
    }
    return { dow: dow, mins: mins, period: period, progress: progress, date: d };
  }

  (function board() {
    var el = $('[data-board]');
    if (!el) return;
    var info = nowInfo();
    var monday = new Date(info.date.getFullYear(), info.date.getMonth(), info.date.getDate() - (info.dow - 1));
    var range = $('[data-week-range]', el);
    if (range) {
      var f = new Date(monday.getTime() + 4 * 864e5);
      range.textContent = pad(monday.getMonth() + 1) + '/' + pad(monday.getDate()) + ' – ' + pad(f.getMonth() + 1) + '/' + pad(f.getDate());
    }
    $$('[data-date]', el).forEach(function (s) {
      var i = +s.getAttribute('data-date');
      var d = new Date(monday.getTime() + (i - 1) * 864e5);
      s.textContent = pad(d.getDate());
    });

    var heads = $$('.bh', el);
    heads.forEach(function (h, i) {
      if (i > 0) h.classList.toggle('today', i === info.dow);
    });

    var line = $('[data-nowline]', el);
    var label = $('[data-now-label]', el);
    var todayChips = $$('.chip[data-day="' + info.dow + '"]', el);

    function paintLabel(txt) {
      if (!label) return;
      if (!txt) { label.textContent = ''; label.hidden = true; return; }
      label.textContent = txt;
      label.hidden = false;
    }

    if (info.period) {
      var chip = todayChips.filter(function (c) { return +c.getAttribute('data-period') === info.period; })[0];
      if (chip && line) {
        var rowVal = chip.style.getPropertyValue('--row') || '';
        var span = (rowVal.match(/span\s+(\d+)/) || [0, 1])[1];
        var top = chip.offsetTop + (chip.offsetHeight / span) * info.progress;
        line.style.top = top + 'px';
        line.classList.add('on');
        paintLabel('现在 · 第 ' + info.period + ' 节');
      } else {
        paintLabel(info.dow > 5 ? '周末愉快' : '课间休息');
      }
    } else {
      var next = todayChips.filter(function (c) { return +c.getAttribute('data-period') > 0; }).filter(function (c) {
        var p = +c.getAttribute('data-period');
        return minutesOf(PERIODS[p - 1][0]) > info.mins;
      })[0];
      if (next) paintLabel('下一节 ' + PERIODS[+next.getAttribute('data-period') - 1][0]);
      else paintLabel(info.dow > 5 ? '周末愉快' : '今天收工');
    }

    /* --- 移动端「今天」卡片 --- */
    var rail = $('[data-today-rail]');
    var count = $('[data-today-count]');
    if (!rail) return;

    var chips = $$('.chip', el);
    function chipData(c) {
      var p = +c.getAttribute('data-period');
      return {
        day: +c.getAttribute('data-day'),
        period: p,
        time: PERIODS[p - 1] ? PERIODS[p - 1][0] : '',
        name: (c.childNodes[0] && c.childNodes[0].textContent || '').trim(),
        room: (c.querySelector('small') && c.querySelector('small').textContent || '').trim(),
        color: (c.className.match(/c-[a-z]+/) || [''])[0]
      };
    }
    var all = chips.map(chipData);
    var todays = all.filter(function (c) { return c.day === info.dow; });

    function cardHTML(c, isNow) {
      return '<div class="tcard ' + c.color + (isNow ? ' now' : '') + '">' +
        '<div class="t-time">' + c.time + '</div>' +
        '<div class="t-name">' + c.name + '</div>' +
        '<div class="t-room">' + c.room + '</div></div>';
    }

    if (todays.length) {
      rail.innerHTML = todays.map(function (c) { return cardHTML(c, c.period === info.period); }).join('');
      if (count) count.textContent = todays.length + ' 节';
    } else {
      var nextDay = info.dow;
      var found = null;
      var d = info.dow;
      for (var k = 0; k < 7; k++) {
        d = d >= 5 ? 1 : d + 1;      // 周五/周六/周日之后都回到周一
        var hit = all.filter(function (c) { return c.day === d; })[0];
        if (hit) { nextDay = d; found = hit; break; }
      }
      if (found) {
        rail.innerHTML = '<div class="tcard c-sand rest"><div class="t-time">今天没课</div>' +
          '<div class="t-name">下一节 · 周' + DAY_NAMES[nextDay - 1] + ' ' + found.time + '</div>' +
          '<div class="t-room">' + found.name + ' · ' + found.room + '</div></div>';
      } else {
        rail.innerHTML = '<div class="tcard rest"><div class="t-time">今天没课</div><div class="t-name">好好休息</div></div>';
      }
      if (count) count.textContent = '0 节';
    }
  })();

  /* ==================== 截图卷轴 ==================== */
  (function reel() {
    var el = $('[data-reel]');
    if (!el) return;
    var shots = $$('.shot', el);
    var title = $('[data-reel-title]');
    var desc = $('[data-reel-desc]');
    var cap = $('[data-reel-cap]');
    var indexEl = $('[data-reel-index]');
    var dotsWrap = $('[data-reel-dots]');
    var current = -1;

    var dots = shots.map(function (_, i) {
      var b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('aria-label', '第 ' + (i + 1) + ' 张');
      b.addEventListener('click', function () { go(i); });
      if (dotsWrap) dotsWrap.appendChild(b);
      return b;
    });

    function centerOf(shot) {
      var r = shot.getBoundingClientRect();
      var rr = el.getBoundingClientRect();
      return r.left - rr.left + el.scrollLeft + r.width / 2;
    }

    function go(i, smooth) {
      i = clamp(i, 0, shots.length - 1);
      var target = centerOf(shots[i]) - el.clientWidth / 2;
      el.scrollTo({ left: target, behavior: smooth === false || reduce ? 'auto' : 'smooth' });
      setActive(i);
    }

    function setActive(i, silent) {
      if (i === current && silent) return;
      current = i;
      shots.forEach(function (s, k) { s.classList.toggle('on', k === i); });
      dots.forEach(function (d, k) { d.classList.toggle('on', k === i); });
      if (indexEl) indexEl.textContent = pad(i + 1);
      var s = shots[i];
      if (title && desc && cap) {
        cap.classList.add('swap');
        setTimeout(function () {
          title.textContent = s.getAttribute('data-title') || '';
          desc.textContent = s.getAttribute('data-desc') || '';
          cap.classList.remove('swap');
        }, reduce ? 0 : 160);
      }
    }

    var syncActive = raf(function () {
      var mid = el.scrollLeft + el.clientWidth / 2;
      var best = 0, dist = Infinity;
      shots.forEach(function (s, i) {
        var d = Math.abs(centerOf(s) - mid);
        if (d < dist) { dist = d; best = i; }
      });
      setActive(best, true);
    });

    el.addEventListener('scroll', syncActive, { passive: true });
    setActive(0, true);

    // 进入视口自动向右轮播；到两端折返，避免跳帧
    if (!reduce) {
      var dir = 1, inView = false, hovering = false, holdUntil = 0;
      window.setInterval(function () {
        if (!inView || hovering || down || document.hidden) return;
        if (Date.now() < holdUntil) return;
        var next = current + dir;
        if (next > shots.length - 1) { dir = -1; next = current - 1; }
        else if (next < 0) { dir = 1; next = current + 1; }
        go(next);
      }, 3600);

      if ('IntersectionObserver' in window) {
        new IntersectionObserver(function (es) {
          inView = es[0].intersectionRatio > 0.35;
        }, { threshold: [0, 0.35, 0.6] }).observe(el);
      } else {
        inView = true;
      }

      if (fine) {
        el.addEventListener('pointerenter', function () { hovering = true; });
        el.addEventListener('pointerleave', function () { hovering = false; });
      }
      el.addEventListener('pointerdown', function () { holdUntil = Date.now() + 7000; });
      el.addEventListener('focusin', function () { hovering = true; });
      el.addEventListener('focusout', function () { hovering = false; });
    }

    var prev = $('[data-reel-prev]');
    var next = $('[data-reel-next]');
    if (prev) prev.addEventListener('click', function () { go(current - 1); });
    if (next) next.addEventListener('click', function () { go(current + 1); });

    el.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowLeft') { e.preventDefault(); go(current - 1); }
      if (e.key === 'ArrowRight') { e.preventDefault(); go(current + 1); }
    });

    // 鼠标拖拽（触摸端用原生滚动）
    var down = false, startX = 0, startLeft = 0, moved = false;
    el.addEventListener('pointerdown', function (e) {
      if (e.pointerType !== 'mouse') return;
      down = true; moved = false;
      startX = e.clientX; startLeft = el.scrollLeft;
      el.classList.add('dragging');
      el.setPointerCapture && el.setPointerCapture(e.pointerId);
    });
    el.addEventListener('pointermove', function (e) {
      if (!down) return;
      var dx = e.clientX - startX;
      if (Math.abs(dx) > 4) moved = true;
      el.scrollLeft = startLeft - dx;
    });
    function endDrag(e) {
      if (!down) return;
      down = false;
      el.classList.remove('dragging');
      if (moved) {
        var mid = el.scrollLeft + el.clientWidth / 2;
        var best = 0, dist = Infinity;
        shots.forEach(function (s, i) {
          var d = Math.abs(centerOf(s) - mid);
          if (d < dist) { dist = d; best = i; }
        });
        go(best);
      }
    }
    el.addEventListener('pointerup', endDrag);
    el.addEventListener('pointercancel', endDrag);
    el.addEventListener('pointerleave', endDrag);
    el.addEventListener('dragstart', function (e) { e.preventDefault(); });

    var onResize = raf(function () { go(current, false); });
    window.addEventListener('resize', onResize);
  })();

  /* ==================== 移动端功能区手风琴 ==================== */
  (function accordion() {
    var cards = $$('[data-fcard], .fcard');
    if (!cards.length) return;
    var isMobile = function () { return window.matchMedia('(max-width: 860px)').matches; };

    $$('[data-fc-toggle]').forEach(function (btn) {
      btn.addEventListener('click', function (e) {
        e.preventDefault();
        var card = btn.closest('.fcard');
        if (!card) return;
        var open = card.classList.toggle('open');
        btn.setAttribute('aria-expanded', String(open));
      });
    });

    if (isMobile() && cards[0]) {
      cards[0].classList.add('open');
      var b = $('[data-fc-toggle]', cards[0]);
      if (b) b.setAttribute('aria-expanded', 'true');
    }
  })();

  /* ==================== 锚点：修正滚动偏移 ==================== */
  $$('a[href^="#"]').forEach(function (a) {
    a.addEventListener('click', function (e) {
      var id = a.getAttribute('href');
      if (!id || id === '#') return;
      var t = document.querySelector(id);
      if (!t) return;
      e.preventDefault();
      var top = t.getBoundingClientRect().top + window.scrollY - (id === '#top' ? 0 : 84);
      window.scrollTo({ top: Math.max(0, top), behavior: reduce ? 'auto' : 'smooth' });
      if (history.replaceState) history.replaceState(null, '', id);
    });
  });
})();

/* ==================== 支持 / 赞赏名单（分页） ==================== */
(function () {
  var list = document.getElementById('sp-items');
  if (!list) return;

  var prev = document.getElementById('sp-prev');
  var next = document.getElementById('sp-next');
  var pager = document.getElementById('sp-pager');
  var pageno = document.getElementById('sp-pageno');
  var pagetotal = document.getElementById('sp-pagetotal');
  var stateEl = document.getElementById('sp-state');
  var emptyEl = document.getElementById('sp-empty');

  // tc.nexioschedule.icu 是 EdgeOne Pages 静态托管，没有 /api 反代，走主站绝对地址（后端已带 CORS: *）
  var API_BASE = location.hostname === 'tc.nexioschedule.icu' ? 'https://nexioschedule.icu' : '';

  var PAGE = 6;
  var page = 1;
  var total = 0;
  var cache = {};
  var loading = false;

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function itemHtml(it) {
    // 后端把匿名记成 "[匿名]"，显示时去掉方括号，头像取首字
    var name = String(it.nickname || '').trim().replace(/^\[|\]$/g, '') || '匿名';
    var initial = name.slice(0, 1).toUpperCase();
    var time = String(it.time || '').slice(5);
    var remark = String(it.remark || '').trim();
    return '<li class="sp-item">' +
      '<span class="sp-avatar" aria-hidden="true">' + esc(initial) + '</span>' +
      '<div class="sp-main">' +
      '<div class="sp-row"><span class="sp-name">' + esc(name) + '</span>' +
      '<span class="sp-amount">' + esc(it.amount || '') + '</span></div>' +
      (remark ? '<p class="sp-remark">' + esc(remark) + '</p>' : '') +
      '</div>' +
      '<time class="sp-time">' + esc(time) + '</time>' +
      '</li>';
  }

  function paint() {
    var pages = Math.max(1, Math.ceil(total / PAGE));
    if (pageno) pageno.textContent = String(page);
    if (pagetotal) pagetotal.textContent = String(pages);
    if (pager) pager.hidden = total <= PAGE;
    if (prev) prev.disabled = page <= 1;
    if (next) next.disabled = page >= pages;
    if (stateEl) stateEl.textContent = total ? total + ' 条' : '';
  }

  function render(items) {
    list.innerHTML = items.map(itemHtml).join('');
    if (emptyEl) emptyEl.hidden = items.length > 0;
  }

  function go(p) {
    if (loading || p < 1) return;
    var pages = Math.max(1, Math.ceil(total / PAGE));
    if (total && p > pages) return;

    if (cache[p]) {
      page = p;
      render(cache[p]);
      paint();
      return;
    }

    loading = true;
    if (stateEl) stateEl.textContent = '加载中…';

    fetch(API_BASE + '/api/appreciations?limit=' + PAGE + '&offset=' + (p - 1) * PAGE, { cache: 'no-store' })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (data) {
        var items = (data && data.records) || [];
        total = (data && typeof data.total === 'number')
          ? data.total
          : (p - 1) * PAGE + items.length;
        cache[p] = items;
        page = p;
        render(items);
        paint();
      })
      .catch(function () {
        if (stateEl) stateEl.textContent = '加载失败，稍后再试';
        if (!total && emptyEl) emptyEl.hidden = false;
      })
      .then(function () {
        loading = false;
      });
  }

  if (location.protocol === 'file:') {
    if (emptyEl) {
      emptyEl.hidden = false;
      emptyEl.textContent = '本地直接打开文件时读不到名单，用 http 方式访问才会加载。';
    }
    if (stateEl) stateEl.textContent = '';
    return;
  }

  if (prev) prev.addEventListener('click', function () { go(page - 1); });
  if (next) next.addEventListener('click', function () { go(page + 1); });

  // 按需加载：区块进入视口才请求第一页，首屏不白拉一次接口
  var section = list.closest('section') || list;
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      if (entries[0].isIntersecting) {
        io.disconnect();
        go(1);
      }
    }, { rootMargin: '240px 0px' });
    io.observe(section);
  } else {
    go(1);
  }
})();
