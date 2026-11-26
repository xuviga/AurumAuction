const mineflayer = require('mineflayer');
const { Rcon } = require('rcon-client');

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

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pass = (n, e) => { results.push({ n, ok: true }); console.log('PASS  ' + n + (e ? ' :: ' + e : '')); };
const fail = (n, e) => { results.push({ n, ok: false }); console.log('FAIL  ' + n + (e ? ' :: ' + e : '')); };
const info = (m) => console.log('      ' + m);

function spawnBot(name, onMessage) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ ...SERVER, username: name });
    const chat = [];
    bot.on('messagestr', (m) => { if (onMessage) onMessage(m); if (bot.__chat) bot.__chat.push(m); });
    bot.__chat = chat;
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

function click(bot, slot, button) {
  try {
    const p = bot.clickWindow(slot, button === undefined ? 0 : button, 0);
    if (p && typeof p.catch === 'function') p.catch(() => {});
  } catch (e) {}
}

function title(w) {
  try { return typeof w.title === 'string' ? w.title : JSON.stringify(w.title); } catch (e) { return '?'; }
}

function balanceOf(text) {
  const m = String(text).replace(/,/g, '').match(/\$\s*([0-9]+(?:\.[0-9]+)?)/);
  if (m) return Number(m[1]);
  const all = String(text).match(/([0-9]+(?:\.[0-9]+)?)/g);
  return all && all.length ? Number(all[all.length - 1]) : NaN;
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

(async () => {
  const rcon = await Rcon.connect(RCON);
  const sellerName = 'BidSeller' + Math.floor(Math.random() * 100000);
  const seller = await spawnBot(sellerName);
  let b1 = await spawnBot('BidderOne');
  const b2 = await spawnBot('BidderTwo');
  await sleep(1500);
  await rcon.send('gamemode creative ' + sellerName);
  await rcon.send('gamemode creative BidderOne');
  await rcon.send('gamemode creative BidderTwo');
  await rcon.send('clear ' + sellerName);
  await rcon.send('clear BidderOne');
  await rcon.send('clear BidderTwo');
  await rcon.send('eco set ' + sellerName + ' 0');
  await rcon.send('eco set BidderOne 100000');
  await rcon.send('eco set BidderTwo 100000');
  await rcon.send('give ' + sellerName + ' diamond 64');
  await waitItem(seller, 'diamond', 10000);
  info('bots ready');

  // 1. auction with buyout
  let lot1 = -1;
  try {
    await sleep(700);
    const item = seller.inventory.items().find((i) => i.name === 'diamond');
    await seller.equip(item, 'hand');
    await sleep(400);
    const p = waitChat(seller, /Аукцион|Auction/i, 8000);
    seller.chat('/ah auction 1000 1 5000');
    const msg = await p;
    lot1 = Number((msg.match(/#(\d+)/) || [])[1]);
    pass('auction created', msg);
  } catch (e) { fail('auction created', e.message); }

  try {
    await sleep(700);
    const p = waitChat(b1, /Ставка|Bid/i, 8000);
    b1.chat('/ah bid ' + lot1 + ' 1200');
    pass('first bid', await p);
  } catch (e) { fail('first bid', e.message); }

  try {
    await sleep(700);
    const outbid = waitChat(b1, /перебили|outbid/i, 8000);
    const p = waitChat(b2, /Ставка|Bid/i, 8000);
    b2.chat('/ah bid ' + lot1 + ' 1500');
    pass('second bid', await p);
    pass('previous bidder notified', await outbid);
  } catch (e) { fail('second bid / outbid notice', e.message); }

  try {
    await sleep(700);
    const p = waitChat(b1, /Куплено|Bought/i, 10000);
    b1.chat('/ah bid ' + lot1 + ' 5000');
    pass('buyout', await p);
    const n = await waitItem(b1, 'diamond', 8000);
    if (n === 64) pass('winner received item'); else fail('winner received item', 'got ' + n);
  } catch (e) { fail('buyout', e.message); }

  // 2. auction settled by admin end while the winner is offline -> claim
  let lot2 = -1;
  try {
    await sleep(700);
    await rcon.send('give ' + sellerName + ' diamond 64');
    await waitItem(seller, 'diamond', 10000);
    const item = seller.inventory.items().find((i) => i.name === 'diamond');
    await seller.equip(item, 'hand');
    await sleep(400);
    const p = waitChat(seller, /Аукцион|Auction/i, 8000);
    seller.chat('/ah auction 2000 1');
    lot2 = Number(((await p).match(/#(\d+)/) || [])[1]);
    pass('second auction created', 'lot #' + lot2);
  } catch (e) { fail('second auction created', e.message); }

  try {
    await sleep(700);
    const p = waitChat(b1, /Ставка|Bid/i, 8000);
    b1.chat('/ah bid ' + lot2 + ' 2500');
    pass('bid on second auction', await p);
    b1.quit();
    await sleep(1500);
    rcon.send('ah admin end ' + lot2).then((out) => info('admin end: ' + out.replace(/\u00a7./g, '').trim())).catch(() => {});
    await sleep(1500);
  } catch (e) { fail('admin end flow', e.message); }

  try {
    const rejoin = [];
    const b1b = await spawnBot('BidderOne', (m) => rejoin.push(m));
    await sleep(2500);
    const claimed = await countItem(b1b, 'diamond');
    if (claimed >= 64) pass('offline winner auto-claimed item', 'diamonds=' + claimed);
    else fail('offline winner auto-claimed item', 'diamonds=' + claimed);
    if (rejoin.some((m) => /забрано|claimed/i.test(m))) pass('claim notification delivered');
    else fail('claim notification delivered', rejoin.slice(-3).join(' | '));
    b1 = b1b;
  } catch (e) { fail('offline claim', e.message); }

  // 3. seller offline notification
  let lot3 = -1;
  try {
    await sleep(700);
    await rcon.send('give ' + sellerName + ' diamond 1');
    await waitItem(seller, 'diamond', 8000);
    const item = seller.inventory.items().find((i) => i.name === 'diamond');
    await seller.equip(item, 'hand');
    await sleep(400);
    const p = waitChat(seller, /выставлен|listed/i, 8000);
    seller.chat('/ah sell 3000');
    lot3 = Number(((await p).match(/#(\d+)/) || [])[1]);
    pass('fixed lot for offline test', 'lot #' + lot3);
    seller.quit();
    await sleep(1500);
  } catch (e) { fail('fixed lot for offline test', e.message); }

  try {
    await sleep(700);
    const wp = waitWindow(b2, 8000);
    b2.chat('/ah');
    const w = await wp;
    const slot = w.slots.findIndex((s) => s && s.name === 'diamond');
    if (slot < 0) throw new Error('lot not found in GUI');
    const wp2 = waitWindow(b2, 8000);
    click(b2, slot, 0);
    const cw = await wp2;
    const confirmSlot = cw.slots.findIndex((s) => s && s.name === 'lime_concrete');
    if (confirmSlot < 0) throw new Error('confirm button missing');
    await sleep(600);
    const p = waitChat(b2, /Куплено|Bought/i, 8000);
    click(b2, confirmSlot, 0);
    pass('offline seller purchase', await p);
  } catch (e) { fail('offline seller purchase', e.message); }

  try {
    const rejoin = [];
    const seller2 = await spawnBot(sellerName, (m) => rejoin.push(m));
    await sleep(2500);
    if (rejoin.some((m) => /продан|sold/i.test(m))) pass('offline seller notified');
    else fail('offline seller notified', rejoin.slice(-3).join(' | '));
    seller2.quit();
  } catch (e) { fail('offline seller notified', e.message); }

  // 4. balances
  try {
    const b1Bal = balanceOf(await rcon.send('balance BidderOne'));
    const b2Bal = balanceOf(await rcon.send('balance BidderTwo'));
    const sBal = balanceOf(await rcon.send('balance ' + sellerName));
    info('bidder1=' + b1Bal + ' bidder2=' + b2Bal + ' seller=' + sBal);
    if (b1Bal === 92500) pass('bidder1 charged 5000 + 2500'); else fail('bidder1 charged 5000 + 2500', 'balance=' + b1Bal);
    if (b2Bal === 97000) pass('bidder2 charged 3000 (outbid refund ok)'); else fail('bidder2 charged 3000', 'balance=' + b2Bal);
    if (sBal === 9975) pass('seller received 4750 + 2375 + 2850'); else fail('seller payout', 'balance=' + sBal);
  } catch (e) { fail('balances', e.message); }

  try { b2.quit(); } catch (e) {}
  try { seller.quit(); } catch (e) {}
  rcon.end();
  await sleep(500);
  const bad = results.filter((r) => !r.ok);
  console.log('\nSUMMARY: ' + (results.length - bad.length) + '/' + results.length + ' passed');
  if (bad.length) console.log('FAILED: ' + bad.map((b) => b.n).join(', '));
  process.exit(bad.length ? 1 : 0);
})().catch((e) => { console.error('BID E2E CRASH: ' + (e && e.stack ? e.stack : e)); process.exit(2); });
