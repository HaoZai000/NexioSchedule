// ==================== 工具函数 ====================
function debounce(fn, ms) {
  let t;
  return function () { clearTimeout(t); t = setTimeout(() => fn.apply(this, arguments), ms); };
}

// ==================== GitHub 数据统计（带 sessionStorage 缓存） ====================
async function fetchStats() {
  var cacheKey = 'gh_stats';
  var cacheTTL = 30 * 60 * 1000; // 30 分钟
  try {
    var cached = JSON.parse(sessionStorage.getItem(cacheKey));
    if (cached && Date.now() - cached.ts < cacheTTL) {
      document.getElementById('stars-count').textContent = cached.stars;
      document.getElementById('downloads-count').textContent = cached.downloads;
      return;
    }
  } catch (e) {}

  try {
    var res = await fetch('https://api.github.com/repos/HaoZai000/NexioSchedule');
    if (!res.ok) throw new Error(res.status);
    var data = await res.json();
    var stars = data.stargazers_count || 0;

    var relRes = await fetch('https://api.github.com/repos/HaoZai000/NexioSchedule/releases');
    var releases = await relRes.json();
    var dl = 0;
    if (Array.isArray(releases)) {
      releases.forEach(function (r) { (r.assets || []).forEach(function (a) { dl += a.download_count || 0; }); });
    }

    document.getElementById('stars-count').textContent = stars;
    document.getElementById('downloads-count').textContent = dl;

    var latestTag = releases[0] && releases[0].tag_name;
    var verEl = document.getElementById('app-version');
    if (verEl && latestTag) verEl.textContent = latestTag;

    try {
      sessionStorage.setItem(cacheKey, JSON.stringify({ stars: stars, downloads: dl, ts: Date.now() }));
    } catch (e) {}
  } catch (e) {
    document.getElementById('stars-count').textContent = '—';
    document.getElementById('downloads-count').textContent = '—';  }
}
fetchStats();

// ==================== 页内锚点平滑滚动 ====================
document.querySelectorAll('a[href^="#"]').forEach(function (a) {
  a.addEventListener('click', function (e) {
    var t = document.querySelector(a.getAttribute('href'));
    if (!t) return;
    e.preventDefault();
    t.scrollIntoView({ behavior: 'smooth' });
    // 关闭移动菜单
    closeMobileMenu();
  });
});

// ==================== 滚动时导航栏背景 ====================
var navbar = document.querySelector('.navbar');
var onScroll = function () {
  navbar.classList.toggle('scrolled', window.scrollY > 20);
};
window.addEventListener('scroll', onScroll, { passive: true });
onScroll();

// ==================== 入场动效 ====================
var io = new IntersectionObserver(function (entries) {
  entries.forEach(function (e) {
    if (e.isIntersecting) {
      e.target.classList.add('visible');
      io.unobserve(e.target);
    }
  });
}, { threshold: 0.12, rootMargin: '0px 0px -8% 0px' });
document.querySelectorAll('.reveal').forEach(function (el) { io.observe(el); });

// ==================== 主题切换 ====================
var themeToggle = document.getElementById('theme-toggle');
function setTheme(t) {
  document.documentElement.setAttribute('data-theme', t);
  try { localStorage.setItem('theme', t); } catch (e) {}
  themeToggle.setAttribute('aria-label', t === 'dark' ? '切换为浅色主题' : '切换为深色主题');
  themeToggle.setAttribute('aria-pressed', String(t === 'dark'));
}
themeToggle.addEventListener('click', function () {
  var cur = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
  setTheme(cur === 'dark' ? 'light' : 'dark');
});
setTheme(document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');

// ==================== 功能卡片光标聚光（节流） ====================
var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
if (!reduceMotion && window.matchMedia('(pointer: fine)').matches) {
  document.querySelectorAll('.feature-card').forEach(function (card) {
    var ticking = false;
    card.addEventListener('mousemove', function (e) {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(function () {
        var r = card.getBoundingClientRect();
        card.style.setProperty('--gx', (e.clientX - r.left) + 'px');
        card.style.setProperty('--gy', (e.clientY - r.top) + 'px');
        ticking = false;
      });
    });
  });
}

// ==================== 汉堡菜单 ====================
var hamburger = document.getElementById('hamburger');
var mobileMenu = document.getElementById('mobile-menu');
var menuBackdrop = null;

function createBackdrop() {
  if (menuBackdrop) return;
  menuBackdrop = document.createElement('div');
  menuBackdrop.style.cssText = 'position:fixed;inset:0;z-index:999;background:rgba(0,0,0,.18);opacity:0;transition:opacity .25s ease;pointer-events:none';
  document.body.appendChild(menuBackdrop);
  requestAnimationFrame(function () { menuBackdrop.style.opacity = '1'; });
}

function removeBackdrop() {
  if (!menuBackdrop) return;
  menuBackdrop.style.opacity = '0';
  var el = menuBackdrop;
  setTimeout(function () { el.remove(); }, 260);
  menuBackdrop = null;
}

function closeMobileMenu() {
  hamburger.setAttribute('aria-expanded', 'false');
  hamburger.setAttribute('aria-label', '打开菜单');
  mobileMenu.classList.remove('open');
  mobileMenu.setAttribute('aria-hidden', 'true');
  removeBackdrop();
}

hamburger.addEventListener('click', function () {
  var open = hamburger.getAttribute('aria-expanded') === 'true';
  if (open) {
    closeMobileMenu();
  } else {
    hamburger.setAttribute('aria-expanded', 'true');
    hamburger.setAttribute('aria-label', '关闭菜单');
    mobileMenu.classList.add('open');
    mobileMenu.setAttribute('aria-hidden', 'false');
    createBackdrop();
  }
});

// 点击遮罩关闭
document.addEventListener('click', function (e) {
  if (mobileMenu.classList.contains('open') && !mobileMenu.contains(e.target) && !hamburger.contains(e.target)) {
    closeMobileMenu();
  }
});

// ESC 关闭
document.addEventListener('keydown', function (e) {
  if (e.key === 'Escape' && mobileMenu.classList.contains('open')) {
    closeMobileMenu();
    hamburger.focus();
  }
});

// ==================== 回到顶部 ====================
var backToTop = document.getElementById('back-to-top');
var onScrollTop = function () {
  backToTop.classList.toggle('visible', window.scrollY > 500);
};
window.addEventListener('scroll', onScrollTop, { passive: true });
backToTop.addEventListener('click', function () {
  window.scrollTo({ top: 0, behavior: 'smooth' });
});

// ==================== 预览展示台缩略图切换 ====================
var previewImg = document.getElementById('preview-img');
var previewTitle = document.getElementById('preview-title');
var previewDesc = document.getElementById('preview-desc');
var thumbs = document.querySelectorAll('.thumb');
if (previewImg && thumbs.length) {
  thumbs.forEach(function (btn) {
    btn.addEventListener('click', function () {
      thumbs.forEach(function (t) {
        t.classList.remove('active');
        t.setAttribute('aria-selected', 'false');
      });
      btn.classList.add('active');
      btn.setAttribute('aria-selected', 'true');
      previewImg.classList.add('swap-out');
      setTimeout(function () {
        previewImg.src = btn.dataset.src;
        previewImg.alt = btn.dataset.title;
        if (previewTitle) previewTitle.textContent = btn.dataset.title;
        if (previewDesc) previewDesc.textContent = btn.dataset.desc;
        previewImg.classList.remove('swap-out');
      }, 180);
    });
  });
}
