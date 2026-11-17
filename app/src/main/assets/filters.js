/* Page-only filters: no native bridge, analytics, remote code, or credential access. */
(() => {
  'use strict';
  if (window.SocialDarkFilters) return;
  let config = {};
  let queued = false;
  let routeBlocked = false;
  const style = document.createElement('style');
  style.id = 'socialdark-style';
  const root = document.documentElement;
  const selectors = {
    youtube: {
      shortVideo: [
        'ytd-reel-shelf-renderer', 'ytd-rich-shelf-renderer[is-shorts]',
        'ytm-reel-shelf-renderer', 'ytm-shorts-lockup-view-model',
        'yt-shorts-lockup-view-model', '[is-shorts]'
      ],
      recommendations: [
        'ytd-watch-next-secondary-results-renderer', 'ytm-watch-next-feed',
        '#related', 'ytm-related-video-renderer'
      ],
      comments: ['ytd-comments', 'ytm-comment-section-renderer', 'ytm-comments-entry-point-header-renderer', '#comments'],
      feed: ['ytd-browse[page-subtype="home"] #contents', 'ytm-browse[tab-identifier="FEwhat_to_watch"] .rich-grid-renderer-contents']
    },
    instagram: {
      stories: ['[aria-label="Stories"]', '[aria-label="스토리"]', '[data-testid="story-tray"]'],
      comments: ['[aria-label="Comments"]', '[aria-label="댓글"]', '[data-testid="comments"]']
    }
  };

  function isSupported() {
    const domain = config.site === 'instagram' ? 'instagram.com' : config.site === 'youtube' ? 'youtube.com' : '';
    return domain && location.protocol === 'https:' &&
      (location.hostname === domain || location.hostname.endsWith('.' + domain));
  }

  function blockedPath(path) {
    if (!config.enabled) return false;
    try { path = decodeURIComponent(path); } catch { return false; }
    if (config.site === 'youtube') return config.shortVideo && /^\/shorts(?:\/|$)/.test(path);
    return (config.shortVideo && /^\/reels?(?:\/|$)/.test(path)) ||
      (config.explore && /^\/explore(?:\/|$)/.test(path));
  }

  function schedule() {
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => { queued = false; refresh(); });
  }

  function refresh() {
    if (!document.body) return;
    const active = isSupported() && config.enabled;
    const rules = ['[data-socialdark-hidden="true"]{display:none!important}'];
    if (active) {
      for (const [key, list] of Object.entries(selectors[config.site] || {})) {
        if (config[key]) rules.push(list.join(',') + '{display:none!important}');
      }
      if (config.site === 'instagram' && config.feed && location.pathname === '/') {
        rules.push('main article,[role="main"] article{display:none!important}');
      }
      if (config.site === 'youtube' && config.feed && location.pathname === '/') {
        rules.push('ytm-browse ytm-rich-grid-renderer,ytm-browse .rich-grid-renderer-contents{display:none!important}');
      }
    }
    routeBlocked = !!(active && blockedPath(location.pathname));
    if (routeBlocked) rules.push('body>*:not(#socialdark-blocker){display:none!important}');
    const css = rules.join('\n');
    if (style.textContent !== css) style.textContent = css;
    if (!style.isConnected) (document.head || root).appendChild(style);

    // Remove only our own markers so switching filters off restores the original page.
    document.querySelectorAll('[data-socialdark-hidden]').forEach(node => node.removeAttribute('data-socialdark-hidden'));
    if (active) {
      for (const link of document.querySelectorAll('a[href]')) {
        let url;
        try { url = new URL(link.getAttribute('href'), location.href); } catch { continue; }
        if (url.origin !== location.origin) continue;
        const hidden = blockedPath(url.pathname) ||
          (config.site === 'instagram' && config.stories && /^\/stories(?:\/|$)/.test(url.pathname));
        if (hidden) {
          let target = link;
          if (config.site === 'youtube') {
            target = link.closest('ytm-rich-item-renderer,ytd-rich-item-renderer,ytd-video-renderer,ytm-video-with-context-renderer') || link;
          }
          target.setAttribute('data-socialdark-hidden', 'true');
        }
      }
    }
    let blocker = document.getElementById('socialdark-blocker');
    if (routeBlocked && !blocker) {
      blocker = document.createElement('section');
      blocker.id = 'socialdark-blocker';
      blocker.setAttribute('role', 'region');
      blocker.setAttribute('aria-label', '집중 필터');
      blocker.style.cssText = 'position:fixed;inset:0;z-index:2147483647;display:flex;flex-direction:column;align-items:center;justify-content:center;background:#101211;color:#edf1ed;font:16px system-ui;text-align:center;padding:28px;box-sizing:border-box;gap:16px';
      const icon = document.createElement('span');
      icon.textContent = '✳'; icon.style.cssText = 'font-size:54px;color:#b8f3ce';
      const title = document.createElement('h1');
      title.textContent = '잠깐, 쉬어가도 괜찮아요'; title.style.cssText = 'font-size:24px;margin:0';
      const note = document.createElement('p');
      note.textContent = '이 화면은 집중 필터로 숨겨져 있어요. 보고 싶은 것만 골라보세요.';
      note.style.cssText = 'color:#a5b0a7;line-height:1.7;max-width:320px';
      const home = document.createElement('a');
      home.textContent = '홈으로 돌아가기';
      home.href = config.site === 'instagram' ? 'https://www.instagram.com/' : 'https://m.youtube.com/';
      home.style.cssText = 'padding:16px 24px;border-radius:40px;background:#b8f3ce;color:#102318;text-decoration:none;font-weight:700';
      blocker.append(icon, title, note, home);
      document.body.appendChild(blocker);
    }
    if (!routeBlocked && blocker) blocker.remove();
    if (routeBlocked) document.querySelectorAll('video,audio').forEach(media => media.pause());
    if (active && config.autoplay && config.site === 'youtube') {
      // Use the site's control; removing autoplay attributes alone cannot disable its SPA queue.
      document.querySelectorAll('.ytp-autonav-toggle-button[aria-checked="true"],ytm-autonav-toggle-renderer [role="switch"][aria-checked="true"]').forEach(toggle => toggle.click());
    }
  }

  new MutationObserver(schedule).observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['href', 'aria-checked'] });
  for (const method of ['pushState', 'replaceState']) {
    const original = history[method];
    history[method] = function (...args) { const result = original.apply(this, args); schedule(); return result; };
  }
  addEventListener('popstate', schedule);
  addEventListener('yt-navigate-finish', schedule);
  document.addEventListener('play', event => {
    if (routeBlocked && event.target instanceof HTMLMediaElement) event.target.pause();
  }, true);
  document.addEventListener('click', event => {
    const link = event.target.closest?.('a[href]');
    if (!link || !isSupported()) return;
    let url;
    try { url = new URL(link.href, location.href); } catch { return; }
    if (url.origin === location.origin && blockedPath(url.pathname)) {
      event.preventDefault(); event.stopImmediatePropagation();
    }
  }, true);
  // Some website routers keep references to history methods from before injection.
  setInterval(schedule, 1200);
  window.SocialDarkFilters = { apply(next) { config = { ...next }; refresh(); } };
})();
