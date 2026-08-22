async function fetchStats() {
  try {
    const res = await fetch('https://api.github.com/repos/HaoZai000/NexioSchedule');
    const data = await res.json();
    document.getElementById('stars-count').textContent = data.stargazers_count || 0;

    const relRes = await fetch('https://api.github.com/repos/HaoZai000/NexioSchedule/releases');
    const releases = await relRes.json();
    let dl = 0;
    if (Array.isArray(releases)) {
      releases.forEach(r => (r.assets || []).forEach(a => dl += a.download_count || 0));
    }
    document.getElementById('downloads-count').textContent = dl;
  } catch (e) {}
}
fetchStats();

document.querySelectorAll('a[href^="#"]').forEach(a => {
  a.addEventListener('click', e => {
    e.preventDefault();
    const t = document.querySelector(a.getAttribute('href'));
    if (t) t.scrollIntoView({ behavior: 'smooth' });
  });
});

const navbar = document.querySelector('.navbar');
window.addEventListener('scroll', () => {
  navbar.style.borderBottomColor = window.scrollY > 50 ? 'rgba(255,255,255,.06)' : 'transparent';
});

const obs = new IntersectionObserver(entries => {
  entries.forEach(e => { if (e.isIntersecting) { e.target.classList.add('visible'); obs.unobserve(e.target); } });
}, { threshold: 0.1 });
document.querySelectorAll('.feature-card,.preview-item,.dl-card').forEach(el => {
  el.style.opacity = '0'; el.style.transform = 'translateY(24px)';
  el.style.transition = 'opacity .6s ease, transform .6s ease';
  obs.observe(el);
});
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.feature-card,.preview-item,.dl-card').forEach(el => {
    el.style.opacity = '0'; el.style.transform = 'translateY(24px)';
    el.style.transition = 'opacity .6s ease, transform .6s ease';
    obs.observe(el);
  });
});
const style = document.createElement('style');
style.textContent = '.visible{opacity:1!important;transform:translateY(0)!important}';
document.head.appendChild(style);
