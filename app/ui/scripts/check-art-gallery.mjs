import assert from 'node:assert/strict';
import {mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {chromium} from 'playwright';

const base = process.argv[2] ?? 'http://127.0.0.1:5173';
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/, 'Use a local dev or art-preview Spring host');
const output = resolve(process.env.CAPYBEE_ART_EVIDENCE ?? '../../docs/evidence/30.1');
await mkdir(output, {recursive: true});
const browser = await chromium.launch({headless: true, executablePath: process.env.CHROMIUM_EXECUTABLE ?? '/usr/bin/chromium', args: ['--no-sandbox']});
const context = await browser.newContext({viewport: {width: 1280, height: 950}, extraHTTPHeaders: {'X-Preparation-Fixture': 'fixture'}});
const page = await context.newPage(), errors = [], network = [];
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => {if (message.type() === 'error') errors.push(message.text());});
page.on('request', request => network.push({url: request.url(), method: request.method()}));
const screenshot = async (locator, name) => locator.screenshot({path: resolve(output, `${name}.png`)});
const noOverflow = async () => assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), 'Horizontal page overflow');
const luminance = hex => {
  const rgb = hex.match(/[0-9a-f]{2}/gi).map(v => parseInt(v, 16) / 255).map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4);
  return rgb.reduce((n, c, i) => n + c * [.2126, .7152, .0722][i], 0);
};
const contrast = (a, b) => {const x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);};
const ratios = {
  inkCream: contrast('#2E2413', '#FDF6E3'), mutedCream: contrast('#6C5A3A', '#FDF6E3'),
  inkPaper: contrast('#2E2413', '#FFFDF7'), mutedPaper: contrast('#6C5A3A', '#FFFDF7'),
  focusCream: contrast('#765321', '#FDF6E3'), inkChoice: contrast('#2E2413', '#F8D88D'),
  borderMeadow: contrast('#6C5A3A', '#DCE6CD'), borderWater: contrast('#6C5A3A', '#B5D5D8'),
};
try {
  await page.goto(`${base}/?preview=art`);
  await page.locator('.art-board').waitFor();
  assert.equal(await page.locator('.art-character-variants figure').count(), 31);
  assert.equal(await page.locator('.art-settings figure').count(), 8);
  assert.equal(await page.locator('.art-states button').count(), 11);
  assert.equal(await page.locator('svg image, svg script, svg foreignObject, svg animate, svg filter').count(), 0);
  assert.ok(await page.locator('svg').evaluateAll(nodes => nodes.every(n => n.getAttribute('aria-hidden') === 'true' || n.closest('[aria-hidden=true]'))));
  assert.ok(await page.locator('[id]').evaluateAll(nodes => new Set(nodes.map(n => n.id)).size === nodes.length), 'Duplicate DOM IDs');
  await noOverflow();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({path: resolve(output, 'desktop.png')});
  await screenshot(page.locator('[data-testid=board]'), 'board');
  await screenshot(page.locator('[data-testid=encounter]'), 'encounter');
  await screenshot(page.locator('[data-testid=party]'), 'party');
  for (const id of ['capy', 'mela-bee', 'borys-beaver', 'luna-moth', 'abstract-guide']) {
    await screenshot(page.locator(`[data-testid="character.${id}"]`), `character-${id}`);
  }
  await screenshot(page.locator('[data-testid="backdrop.lantern-grove"]'), 'lantern-grove');
  const firstMove = page.locator('.art-place').nth(1);
  await firstMove.focus(); await page.keyboard.press('Enter');
  assert.equal(await firstMove.getAttribute('aria-pressed'), 'true');
  assert.equal(await firstMove.evaluate(n => getComputedStyle(n).outlineWidth), '3px');
  await page.keyboard.press('Tab');
  assert.equal(await page.locator('.art-place').nth(2).evaluate(n => n === document.activeElement), true);
  await page.locator('select').selectOption('pl');
  await page.setViewportSize({width: 360, height: 800});
  await page.emulateMedia({reducedMotion: 'reduce'});
  for (const zoom of ['1', '2']) {
    await page.locator('main').evaluate((n, zoom) => n.style.zoom = zoom, zoom);
    await noOverflow();
    assert.ok(await page.locator('button,select').evaluateAll(nodes => nodes.every(n => {const r = n.getBoundingClientRect(); return r.width >= 44 && r.height >= 44;})), 'Small control target');
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({path: resolve(output, 'mobile-zoom.png')});
  await page.locator('main').evaluate(n => n.style.zoom = '1');
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({path: resolve(output, 'mobile.png')});
  await screenshot(page.locator('[data-testid=board]'), 'mobile-board');
  await screenshot(page.locator('[data-testid=encounter]'), 'mobile-encounter');
  await screenshot(page.locator('[data-testid=party]'), 'mobile-party');
  assert.ok(await page.locator('button').evaluateAll(nodes => nodes.every(n => getComputedStyle(n).animationName === 'none')));
  const beforeText = await page.locator('[data-testid=encounter]').innerText();
  await page.getByRole('button', {name: 'Tylko tekst', exact: true}).click();
  assert.equal(await page.locator('[data-testid=encounter]').innerText(), beforeText);
  await noOverflow();
  await screenshot(page.locator('[data-testid=encounter]'), 'text-only');
  await page.getByRole('button', {name: 'Tylko tekst', exact: true}).click();
  await page.emulateMedia({forcedColors: 'active'});
  await page.keyboard.press('Tab');
  await firstMove.focus();
  assert.notEqual(await firstMove.evaluate(n => getComputedStyle(n).outlineStyle), 'none');
  assert.ok(await page.locator('button,select').evaluateAll(nodes => nodes.every(n => parseFloat(getComputedStyle(n).borderTopWidth) >= 2)), 'Forced-colors control boundary missing');
  await screenshot(page.locator('.art-board-panel'), 'forced-colors');
  await page.emulateMedia({forcedColors: 'none'});
  for (const [key, value] of Object.entries(ratios)) assert.ok(value >= (key.startsWith('border') || key.startsWith('focus') ? 3 : 4.5), `${key} contrast`);
  let offline = false;
  if (process.env.CAPYBEE_ART_OFFLINE === 'true') {
    await page.waitForFunction(async () => !!(await navigator.serviceWorker.getRegistration())?.active);
    await page.reload(); await page.locator('.art-board').waitFor();
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    await context.setOffline(true);
    await page.reload(); await page.locator('.art-board').waitFor();
    assert.equal(await page.locator('.art-settings figure').count(), 8);
    await context.setOffline(false); offline = true;
  }
  assert.equal(network.filter(r => new URL(r.url).pathname.startsWith('/api/')).length, 0, 'Gallery contacted a game API');
  assert.equal(network.filter(r => !r.url.startsWith(base)).length, 0, 'External request');
  assert.deepEqual(errors, []);
  const report = {base, catalogVersion: 1, combinations: 31, backdrops: 8, states: 11, languages: ['en', 'pl'],
    viewport: [1280, 360], zoom: '200%', reducedMotion: true, forcedColors: true, keyboard: true, textParity: true,
    offlineReload: offline, apiRequests: 0, externalRequests: 0, contrast: ratios};
  await writeFile(resolve(output, 'browser-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report));
  console.log('PASS: fixed catalog, EN/PL, keyboard, mobile/zoom, text mode, forced colors, contrast and request isolation');
} finally {await browser.close();}
