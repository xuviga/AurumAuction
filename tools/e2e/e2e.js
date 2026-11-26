const mineflayer = require('mineflayer');
const { Rcon } = require('rcon-client');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const SERVER = {
  host: process.env.MC_HOST || '127.0.0.1',
  port: Number(process.env.MC_PORT || 25568),
  version: process.env.MC_VERSION || '26.1',
  auth: 'offline',
};
const RCON = {
  host: '127.0.0.1',
  port: Number(process.env.RCON_PORT || 25577),
  password: process.env.RCON_PASS || 'aurumtest',
};
const LOTS_FILE = process.env.LOTS_FILE
  || path.join('D:\\HDD\\MineCraft\\AurumAuctionServer-26.1', 'plugins', 'AurumAuction', 'lots.yml');

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pass = (n, e) => { results.push({ n, ok: true }); console.log('PASS  ' + n + (e ? ' :: ' + e : '')); };
const fail = (n, e) => { results.push({ n, ok: false }); console.log('FAIL  ' + n + (e ? ' :: ' + e : '')); };
const info = (m) => console.log('      ' + m);

// Inventory closes inside a click can drop the transaction ack on old clients.
function click(bot, slot, button) {
  try {
    const p = bot.clickWindow(slot, button === undefined ? 0 : button, 0);
    if (p && typeof p.catch === 'function') p.catch(() => {});
  } catch (e) {}
}

function spawnBot(name) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ ...SERVER, username: name });
    const t = setTimeout(() => reject(new Error('spawn timeout ' + name)), 30000);
    bot.once('spawn', () => { clearTimeout(t); resolve(bot); });
    bot.once('error', (e) => { clearTimeout(t); reject(e); });
    bot.once('kicked', (k) => { clearTimeout(t); reject(new Error('kicked ' + JSON.stringify(k))); });
  });
}

function waitChat(bot, re, timeout = 10000) {
  return new Promise((resolve, reject) => {
    const h = (m) => { if (re.test(m)) { clearTimeout(t); bot.removeListener('messagestr', h); resolve(m); } };
    const t = setTimeout(() => { bot.removeListener('messagestr', h); reject(new Error('chat timeout ' + re)); }, timeout);
    bot.on('messagestr', h);
  });
}

function waitWindow(bot, timeout = 10000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('window timeout')), timeout);
    bot.once('windowOpen', (w) => { clearTimeout(t); resolve(w); });
  });
}

function title(w) {
  try { return typeof w.title === 'string' ? w.title : JSON.stringify(w.title); } catch (e) { return '?'; }
}

async function countItem(bot, name) {
  return bot.inventory.items().filter((i) => i.name === name).reduce((s, i) => s + i.count, 0);
}

async function waitItem(bot, name, timeout = 8000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    const n = await countItem(bot, name);
    if (n > 0) return n;
    await sleep(300);
  }
  return 0;
}

async function equipItem(bot, name) {
  const item = bot.inventory.items().find((i) => i.name === name);
  if (!item) throw new Error('no ' + name);
  await bot.equip(item, 'hand');
  await sleep(500);
}

function balanceOf(text) {
  const m = String(text).replace(/,/g, '').match(/\$\s*([0-9]+(?:\.[0-9]+)?)/);
  if (m) return Number(m[1]);
  const all = String(text).match(/([0-9]+(?:\.[0-9]+)?)/g);
  return all && all.length ? Number(all[all.length - 1]) : NaN;
}

(async () => {
  const rcon = await Rcon.connect(RCON);
  info('online: ' + (await rcon.send('list')).replace(/\u00a7./g, '').trim());

  const sellerName = 'FixSeller' + Math.floor(Math.random() * 100000);
  const seller = await spawnBot(sellerName);
  const buyer = await spawnBot('BuyerBot');
  await sleep(1500);
  info('bots spawned');

  await rcon.send('gamemode creative ' + sellerName);
  await rcon.send('gamemode creative BuyerBot');
  await rcon.send('clear ' + sellerName);
  await rcon.send('clear BuyerBot');
  info('eco: ' + (await rcon.send('eco set BuyerBot 100000')).replace(/\u00a7./g, ''));
  await rcon.send('eco set ' + sellerName + ' 0');
  info('give barrier: ' + (await rcon.send('give ' + sellerName + ' barrier 1')).replace(/\u00a7./g, ''));
  info('give diamond: ' + (await rcon.send('give ' + sellerName + ' diamond 64')).replace(/\u00a7./g, ''));
  const gotBarrier = await waitItem(seller, 'barrier', 10000);
  const gotDiamond = await waitItem(seller, 'diamond', 10000);
  info('seller items: barrier=' + gotBarrier + ' diamond=' + gotDiamond);

  // 1. blocked material
  try {
    await equipItem(seller, 'barrier');
    const p = waitChat(seller, /нельзя выставлять/i, 6000);
    seller.chat('/ah sell 100');
    pass('blocked item rejected', await p);
  } catch (e) { fail('blocked item rejected', e.message); }

  // 2. min price
  try {
    await equipItem(seller, 'diamond');
    const p = waitChat(seller, /Минимальная цена/i, 6000);
    seller.chat('/ah sell 0');
    pass('min price enforced', await p);
  } catch (e) { fail('min price enforced', e.message); }

  // 3. sell
  let lotId = -1;
  try {
    await sleep(900); // command cooldown after the min-price attempt
    const p = waitChat(seller, /выставлен/i, 8000);
    seller.chat('/ah sell 5000');
    const msg = await p;
    const match = msg.match(/#(\d+)/);
    if (match) lotId = Number(match[1]);
    pass('sell', msg);
  } catch (e) { fail('sell', e.message); }

  // 4. search opens GUI
  try {
    const wp = waitWindow(buyer, 8000);
    buyer.chat('/ah search diamond');
    const w = await wp;
    const n = w.slots.filter((s) => s).length;
    pass('search opens GUI', title(w) + ', items=' + n);
    try { buyer.closeWindow(w); } catch (e) {}
    await sleep(500);
  } catch (e) { fail('search opens GUI', e.message); }

  // 5. GUI search: button -> chat input -> results window
  try {
    const wp = waitWindow(buyer, 8000);
    buyer.chat('/ah');
    await wp;
    click(buyer, 47, 0);
    await sleep(600);
    const wp2 = waitWindow(buyer, 8000);
    buyer.chat('diamond');
    const res = await wp2;
    pass('GUI search', title(res).slice(0, 40) + ', items=' + res.slots.filter((s) => s).length);
    try { buyer.closeWindow(res); } catch (e) {}
    await sleep(400);
  } catch (e) { fail('GUI search', e.message); }

  // 6. category filter button just re-renders the list
  try {
    const wp = waitWindow(buyer, 8000);
    buyer.chat('/ah');
    await wp;
    const wpCat = waitWindow(buyer, 8000);
    click(buyer, 46, 0);
    const cat = await wpCat;
    pass('category filter', title(cat).slice(0, 40));
    try { buyer.closeWindow(cat); } catch (e) {}
    await sleep(400);
  } catch (e) { fail('category filter', e.message); }

  // 7. buy confirmation: cancel must not charge
  try {
    const wp = waitWindow(buyer, 8000);
    buyer.chat('/ah');
    const w = await wp;
    const slot = w.slots.findIndex((s) => s && s.name === 'diamond');
    if (slot < 0) throw new Error('diamond lot not found');
    const wpConfirm = waitWindow(buyer, 8000);
    click(buyer, slot, 0);
    const cw = await wpConfirm;
    const cancelSlot = cw.slots.findIndex((s) => s && s.name === 'red_concrete');
    if (cancelSlot < 0) throw new Error('cancel button not found');
    click(buyer, cancelSlot, 0);
    await sleep(900);
    pass('buy confirm: cancel works');
  } catch (e) { fail('buy confirm: cancel works', e.message); }

  // 8. buy via confirm
  try {
    const wp = waitWindow(buyer, 8000);
    buyer.chat('/ah');
    const w = await wp;
    const slot = w.slots.findIndex((s) => s && s.name === 'diamond');
    if (slot < 0) throw new Error('diamond lot not found');
    const wpConfirm = waitWindow(buyer, 8000);
    click(buyer, slot, 0);
    const cw = await wpConfirm;
    const confirmSlot = cw.slots.findIndex((s) => s && s.name === 'lime_concrete');
    if (confirmSlot < 0) throw new Error('confirm button not found');
    pass('confirm window opens', title(cw).slice(0, 40));
    await sleep(700);
    const p = waitChat(buyer, /Куплено/i, 10000);
    click(buyer, confirmSlot, 0);
    pass('buy', await p);
    const n = await waitItem(buyer, 'diamond', 8000);
    if (n === 64) pass('buyer received 64 diamonds'); else fail('buyer received 64 diamonds', 'got ' + n);
  } catch (e) { fail('buy', e.message); }

  // 9. admin commands over RCON
  try {
    const infoOut = (await rcon.send('ah admin info ' + lotId)).replace(/\u00a7./g, '').trim();
    if (/Продавец|Seller/i.test(infoOut)) pass('admin info', infoOut.slice(0, 90)); else fail('admin info', infoOut);
    const payoutsOut = (await rcon.send('ah admin payouts')).replace(/\u00a7./g, '').trim();
    if (/Ожидает|Pending/i.test(payoutsOut)) pass('admin payouts', payoutsOut); else fail('admin payouts', payoutsOut);
  } catch (e) { fail('admin commands', e.message); }

  // 6. balances
  try {
    const bb = balanceOf(await rcon.send('balance BuyerBot'));
    const sb = balanceOf(await rcon.send('balance ' + sellerName));
    info('buyer=' + bb + ' seller=' + sb);
    if (bb === 95000) pass('buyer charged 5000'); else fail('buyer charged 5000', 'balance=' + bb);
    if (sb === 4750) pass('seller paid 4750 after 5% tax'); else fail('seller paid 4750 after 5% tax', 'balance=' + sb);
  } catch (e) { fail('balances', e.message); }

  // 7. cancel own lot (creative slot: avoids client inventory desync after GUI re-render)
  try {
    const mcd = require('minecraft-data')(buyer.version);
    const Item = require('prismarine-item')(buyer.version);
    await buyer.creative.setInventorySlot(36, new Item(mcd.itemsByName.diamond.id, 64));
    buyer.setQuickBarSlot(0);
    await sleep(800);
    const l = waitChat(buyer, /выставлен/i, 8000);
    buyer.chat('/ah sell 1000');
    pass('buyer lists lot', await l);
    const wp = waitWindow(buyer, 8000);
    buyer.chat('/ah my');
    const w = await wp;
    const slot = w.slots.findIndex((s) => s && s.name === 'diamond');
    if (slot < 0) throw new Error('own lot not found');
    const c = waitChat(buyer, /снят/i, 8000);
    click(buyer, slot, 1);
    pass('cancel', await c);
    try { buyer.closeWindow(w); } catch (e) {}
    await sleep(800);
    const n = await countItem(buyer, 'diamond');
    info('buyer diamonds after cancel=' + n);
  } catch (e) { fail('cancel', e.message); }

  // 8. persistence: YAML autosave or SQLite database
  try {
    if (!fs.existsSync(LOTS_FILE)) {
      const db = LOTS_FILE.replace(/lots\.yml$/, 'lots.db');
      if (fs.existsSync(db)) pass('persistence: SQLite database present', 'size=' + fs.statSync(db).size);
      else fail('persistence', 'neither lots.yml nor lots.db found');
      seller.quit(); buyer.quit(); rcon.end();
      await sleep(500);
      const bad0 = results.filter((r) => !r.ok);
      console.log('\nSUMMARY: ' + (results.length - bad0.length) + '/' + results.length + ' passed');
      if (bad0.length) console.log('FAILED: ' + bad0.map((b) => b.n).join(', '));
      process.exit(bad0.length ? 1 : 0);
    }
    let yaml = null;
    const end = Date.now() + 90000;
    while (Date.now() < end) {
      await sleep(3000);
      if (fs.existsSync(LOTS_FILE)) {
        yaml = fs.readFileSync(LOTS_FILE, 'utf8');
        if (/status:\s*SOLD/.test(yaml) && /status:\s*CANCELLED/.test(yaml)) break;
      }
    }
    if (!yaml) throw new Error('lots.yml not written');
    const sold = /status:\s*SOLD/.test(yaml);
    const cancelled = /status:\s*CANCELLED/.test(yaml);
    if (sold && cancelled) pass('persistence: SOLD + CANCELLED in lots.yml', 'size=' + yaml.length);
    else fail('persistence', 'SOLD=' + sold + ' CANCELLED=' + cancelled);
  } catch (e) { fail('persistence', e.message); }

  try { seller.quit(); buyer.quit(); } catch (e) {}
  rcon.end();
  await sleep(500);
  const bad = results.filter((r) => !r.ok);
  console.log('\nSUMMARY: ' + (results.length - bad.length) + '/' + results.length + ' passed');
  if (bad.length) console.log('FAILED: ' + bad.map((b) => b.n).join(', '));
  process.exit(bad.length ? 1 : 0);
})().catch((e) => { console.error('E2E CRASH: ' + (e && e.stack ? e.stack : e)); process.exit(2); });
