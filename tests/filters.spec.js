const { test, expect } = require('@playwright/test');
const path = require('node:path');
const script = path.resolve(__dirname, '../app/src/main/assets/filters.js');
const base = { site: 'youtube', enabled: true, shortVideo: true };
async function load(page, url, html, config = base) {
  await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<!doctype html><html><head></head><body>' + html + '</body></html>' }));
  await page.goto(url); await page.addScriptTag({ path: script });
  await page.evaluate(config => window.SocialDarkFilters.apply(config), config);
}
test('mobile Shorts shelves and links disappear; normal videos remain and filters can be undone', async ({ page }) => {
  await load(page, 'https://m.youtube.com/', '<ytm-reel-shelf-renderer>shorts</ytm-reel-shelf-renderer><a href="/shorts/abc">Short</a><a href="/watch?v=abc">Normal</a>');
  await expect(page.locator('ytm-reel-shelf-renderer')).toBeHidden();
  await expect(page.getByText('Short', { exact: true })).toBeHidden();
  await expect(page.getByText('Normal')).toBeVisible();
  await page.evaluate(() => window.SocialDarkFilters.apply({ site: 'youtube', enabled: false }));
  await expect(page.locator('ytm-reel-shelf-renderer')).toBeVisible();
  await expect(page.getByText('Short', { exact: true })).toBeVisible();
});
test('SPA navigation and encoded Shorts routes cannot bypass the filter', async ({ page }) => {
  await load(page, 'https://m.youtube.com/watch?v=abc', '<main>Video</main><video></video>');
  await page.evaluate(() => history.pushState({}, '', '/%73horts/abc'));
  await expect(page.getByRole('region', { name: '집중 필터' })).toBeVisible();
  await expect(page.locator('main')).toBeHidden();
  await page.evaluate(() => history.pushState({}, '', '/watch?v=abc'));
  await expect(page.getByRole('region', { name: '집중 필터' })).toHaveCount(0);
  await expect(page.locator('main')).toBeVisible();
});
test('new content from infinite scrolling is filtered without a reload', async ({ page }) => {
  await load(page, 'https://m.youtube.com/', '<main>Search</main>');
  await page.evaluate(() => document.body.insertAdjacentHTML('beforeend', '<ytm-reel-shelf-renderer id="new">New Shorts</ytm-reel-shelf-renderer><a id="new-short" href="/shorts/2">New link</a>'));
  await expect(page.locator('#new')).toBeHidden(); await expect(page.locator('#new-short')).toBeHidden();
});
test('comments and recommendations can be hidden independently', async ({ page }) => {
  await load(page, 'https://m.youtube.com/watch?v=abc', '<div id="comments">Comments</div><div id="related">Related</div><video controls></video>', { ...base, comments: true, recommendations: false });
  await expect(page.locator('#comments')).toBeHidden(); await expect(page.locator('#related')).toBeVisible();
  await expect(page.locator('video')).toBeVisible();
});
test('Instagram filters preserve direct messages and restore stories when disabled', async ({ page }) => {
  await load(page, 'https://www.instagram.com/direct/inbox/', '<main><p>Inbox</p></main><nav><a href="/reels/">Reels</a><a href="/explore/">Explore</a><a href="/direct/inbox/">Messages</a><a href="/stories/friend/123/">Story</a></nav>', { site: 'instagram', enabled: true, shortVideo: true, explore: true, stories: true, feed: true });
  await expect(page.getByText('Messages')).toBeVisible(); await expect(page.getByText('Inbox')).toBeVisible();
  await expect(page.getByText('Reels', { exact: true })).toBeHidden(); await expect(page.getByText('Explore')).toBeHidden(); await expect(page.getByText('Story')).toBeHidden();
  await page.evaluate(() => window.SocialDarkFilters.apply({ site: 'instagram', enabled: true, shortVideo: false, explore: false, stories: false }));
  await expect(page.getByText('Reels', { exact: true })).toBeVisible(); await expect(page.getByText('Story')).toBeVisible();
});
test('Instagram direct Reel and Explore links show the break screen', async ({ page }) => {
  await load(page, 'https://www.instagram.com/reel/123/', '<main>Reel player</main>', { site: 'instagram', enabled: true, shortVideo: true, explore: true });
  await expect(page.getByRole('link', { name: '홈으로 돌아가기' })).toHaveAttribute('href', 'https://www.instagram.com/');
  await page.evaluate(() => history.replaceState({}, '', '/explore/'));
  await expect(page.getByRole('region', { name: '집중 필터' })).toBeVisible();
});
test('filter engine never modifies unsupported origins', async ({ page }) => {
  await load(page, 'https://youtube.com.evil.test/shorts/a', '<main>External</main><div id="comments">External comments</div>', { ...base, comments: true });
  await expect(page.locator('#comments')).toBeVisible(); await expect(page.locator('main')).toBeVisible();
  await expect(page.locator('#socialdark-blocker')).toHaveCount(0);
});
test('autoplay uses the website switch and leaves current video intact', async ({ page }) => {
  await load(page, 'https://m.youtube.com/watch?v=abc', '<button class="ytp-autonav-toggle-button" role="switch" aria-checked="true" onclick="this.setAttribute(\'aria-checked\',\'false\')">Autoplay</button><video controls></video>', { ...base, autoplay: true });
  await expect(page.getByRole('switch')).toHaveAttribute('aria-checked', 'false');
  await expect(page.locator('video')).toBeVisible();
});
