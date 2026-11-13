'use strict';

// Runs the shipped Merchant route in Chromium with isolated, read-only test data.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright-core');

const root = path.resolve(__dirname, '..');
const shop = '00000000-0000-4000-8000-000000000002';
const owner = '00000000-0000-4000-8000-000000000001';
const bill = '00000000-0000-4000-8000-000000000003';
const products = ['อเมริกาโน่เย็น', 'ลาเต้เย็น', 'มัทฉะลาเต้', 'สตรอว์เบอร์รีปั่น', 'เบอร์เกอร์', 'ข้าวไก่ทอด', 'สปาเกตตี', 'สลัดไก่ย่าง']
  .map((name, i) => ({
    id: '30000000-0000-4000-8000-' + String(i).padStart(12, '0'),
    name, price: 65 + i * 10, pos_available: true, available: true,
    category: i < 4 ? 'เครื่องดื่ม' : 'อาหาร'
  }));

async function main() {
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const file = path.resolve(root, '.' + url.pathname + (url.pathname.endsWith('/') ? 'index.html' : ''));
    if (!file.startsWith(root + path.sep)) return res.writeHead(403).end();
    try {
      const type = { '.css': 'text/css', '.js': 'text/javascript', '.html': 'text/html' }[path.extname(file)];
      res.setHeader('Content-Type', type || 'application/octet-stream');
      res.end(await fs.readFile(file));
    } catch {
      res.writeHead(404).end();
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({ executablePath: process.env.POS_CHROMIUM_PATH || undefined });
    const output = process.env.POS_SCREENSHOTS;
    if (output) await fs.mkdir(output, { recursive: true });
    for (const [width, height, layout] of [
      [1280, 800, 'desktop'], [1440, 900, 'desktop'], [1920, 1080, 'desktop'],
      [1024, 768, 'landscape'], [768, 1024, 'portrait'], [820, 1180, 'portrait'],
      [320, 568, 'mobile'], [360, 740, 'mobile'], [390, 844, 'mobile'], [430, 932, 'mobile']
    ]) {
      const page = await browser.newPage({ viewport: { width, height }, hasTouch: layout !== 'desktop' });
      const writes = [];
      await page.route('https://**/*', async route => {
        const url = new URL(route.request().url());
        if (url.hostname === 'cdn.jsdelivr.net' && url.pathname.includes('/supabase-js@')) {
          return route.fulfill({ contentType: 'text/javascript', body:
            'window.supabase = { createClient: () => ({ realtime: { setAuth: async () => {} }, channel: () => ({ on() { return this; }, subscribe() {} }), removeChannel() {} }) };' });
        }
        if (!url.hostname.endsWith('.supabase.co')) return route.abort();
        const endpoint = url.pathname.split('/').pop();
        const data = {
          claim_active_session: {},
          qg_call_incoming: [],
          pos_my_shop: shop,
          pos_is_owner: true,
          users: [{ id: owner, auth_user_id: owner, role: 'shop', status: 'active', name: 'QA' }],
          shop_profiles: [{ id: shop, user_id: owner, shop_name: 'ร้านทดสอบ', public_category: 'grocery' }],
          products,
          orders: [{ id: bill, order_number: 'QA-001', order_type: 'TAKEAWAY', status: 'pending', kitchen_status: 'NEW', payment_status: 'UNPAID', total_amount: 65 }],
          order_items: [{ id: 'line', order_id: bill, product_id: products[0].id, item_name: products[0].name, quantity: 1, unit_price: 65, total_price: 65 }]
        };
        if (route.request().method() === 'POST' && endpoint.startsWith('pos_') && !['pos_my_shop', 'pos_is_owner'].includes(endpoint)) writes.push(endpoint);
        const headers = {
          'access-control-allow-origin': '*',
          'access-control-allow-headers': route.request().headers()['access-control-request-headers'] || '*',
          'access-control-allow-methods': 'GET,POST,OPTIONS'
        };
        if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
        return route.fulfill({ json: data[endpoint] ?? [], headers });
      });
      await page.addInitScript(user => localStorage.setItem('qg_merchant_session_v1', JSON.stringify({
        role: 'shop', userId: user, authUserId: user, accessToken: 'isolated-token', expiresAt: Date.now() + 3600000
      })), owner);
      await page.goto('http://127.0.0.1:' + server.address().port + '/merchant/#pos', { waitUntil: 'domcontentloaded' });
      const cards = page.locator('.qg-pos-product-card:visible');
      await cards.last().waitFor();
      await page.locator('#qg-printer-btn').waitFor({ state: 'attached' });
      assert.equal(await cards.count(), 8);
      const geometryHandle = await page.waitForFunction(() => {
        const rect = selector => document.querySelector(selector).getBoundingClientRect();
        const cards = [...document.querySelectorAll('.qg-pos-product-card')];
        if (!cards.length || !document.querySelector('.qg-pos-cart')) return false;
        return {
          nav: rect('.qg-pos-tabs').x,
          catalog: rect('.qg-pos-catalog').x,
          cart: rect('.qg-pos-cart').x,
          columns: cards.filter(card => card.offsetTop === cards[0].offsetTop).length,
          overflow: document.documentElement.scrollWidth > innerWidth
        };
      });
      const geometry = await geometryHandle.jsonValue();
      assert.equal(geometry.overflow, false, 'horizontal overflow at ' + width);
      if (layout === 'desktop') {
        assert(geometry.nav < geometry.catalog);
        const navButtons = await page.locator('.qg-pos-tabs button').evaluateAll(buttons => buttons.map(button => button.getBoundingClientRect().x));
        assert.equal(new Set(navButtons).size, 1, 'desktop navigation stacks vertically');
        assert.equal(geometry.columns, 4);
      }
      if (['desktop', 'landscape'].includes(layout)) assert(geometry.cart > geometry.catalog);
      if (layout === 'mobile') {
        assert.equal(geometry.columns, 2);
        const headerHandle = await page.waitForFunction(() => {
          const el = document.querySelector('.qg-pos-header');
          if (!el?.getBoundingClientRect().height || !el.querySelector('.qg-pos-printer')) return false;
          return {
          height: el.getBoundingClientRect().height,
          buttons: [...el.querySelectorAll('button')].map(button => {
            const { width, height } = button.getBoundingClientRect();
            return { width, height };
          })
          };
        });
        const header = await headerHandle.jsonValue();
        assert(header.height <= 48, 'compact mobile header');
        assert(header.buttons.every(({ width, height }) => width >= 44 && height >= 44), 'header touch targets ' + JSON.stringify(header));
      }
      await page.locator('#qg-pos-search').fill('ลาเต้');
      assert.equal(await cards.count(), 2);
      await page.locator('#qg-pos-search').fill('');
      await page.getByRole('button', { name: 'เครื่องดื่ม', exact: true }).click();
      assert.equal(await cards.count(), 4);
      await page.getByRole('button', { name: 'ทั้งหมด', exact: true }).click();
      await page.getByRole('button', { name: /QA-001/ }).click();
      await page.evaluate(() => scrollTo(0, 0));
      const checkout = page.getByRole('button', { name: 'ชำระเงิน', exact: true });
      await checkout.waitFor();
      const compact = ['portrait', 'mobile'].includes(layout);
      const toggle = page.locator('.qg-pos-cart-toggle');
      if (compact) {
        assert.equal(await toggle.getAttribute('aria-expanded'), 'false');
        await toggle.click();
        assert.equal(await toggle.getAttribute('aria-expanded'), 'true');
        assert(await page.locator('.qg-pos-lines').isVisible());
        if (output && width === 390) await page.screenshot({ path: path.join(output, '390x844-cart-expanded.png') });
        await toggle.click();
        assert.equal(await page.locator('.qg-pos-lines').isVisible(), false);
      }
      await page.evaluate(() => scrollTo(0, 0));
      if (output) await page.screenshot({ path: path.join(output, width + 'x' + height + '.png') });
      const checkoutBox = await checkout.boundingBox();
      if (compact) assert(checkoutBox.y + checkoutBox.height <= height, 'checkout remains in the viewport');
      await checkout.click();
      await page.locator('#qg-pos-method').waitFor();
      assert.deepEqual(writes, [], 'opening checkout must never register a payment');
      await page.close();
      console.log('PASS', width, height, layout);
    }
  } finally {
    await browser?.close();
    await new Promise(resolve => server.close(resolve));
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
