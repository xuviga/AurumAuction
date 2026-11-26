const mineflayer = require('mineflayer');
const { Rcon } = require('rcon-client');
const fs = require('fs');

const SERVER = {
  host: '127.0.0.1', port: Number(process.env.MC_PORT || 25568),
  version: process.env.MC_VERSION || '26.1', auth: 'offline',
};
const RCON = { host: '127.0.0.1', port: Number(process.env.RCON_PORT || 25577), password: 'aurumtest' };
const LOG_FILE = process.env.LOG_FILE
  || 'D:\\HDD\\MineCraft\\AurumAuctionServer-26.1\\plugins\\AurumAuction\\transactions.log';

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pass = (n, e) => { results.push({ n, ok: true }); console.log('PASS  ' + n + (e ? ' :: ' + e : '')); };
const fail = (n, e) => { results.push({ n, ok: false }); console.log('FAIL  ' + n + (e ? ' :: ' + e : '')); };
const info = (m) => console.log('      ' + m);

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
function click(bot, slot, button) {
  try { const p = bot.clickWindow(slot, button === undefined ? 0 : button, 0); if (p && p.catch) p.catch(() => {}); } catch (e) {}
}
function balanceOf(text) {
  const m = String(text).replace(/,/g, '').match(/\$\s*([0-9]+(?:\.[0-9]+)?)/);
  return m ? Number(m[1]) : NaN;
}
async function waitItem(bot, name, timeout = 8000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    const n = bot.inventory.items().filter((i) => i.name === name).reduce((s, i) => s + i.count, 0);
    if (n > 0) return n;
    await sleep(300);
  }
  return 0;
}

(async () => {
  const rcon = await Rcon.connect(RCON);
  const sellerName = 'P1Seller' + Math.floor(Math.random() * 100000);
  const seller = await spawnBot(sellerName);
  const buyer = await spawnBot('P1Buyer' + Math.floor(Math.random() * 100000));
  await sleep(1500);
  await rcon.send('gamemode creative ' + sellerName);
  await rcon.send('gamemode creative ' + buyer.username);
  await rcon.send('clear ' + sellerName);
  await rcon.send('clear ' + buyer.username);
  await rcon.send('eco set ' + sellerName + ' 0');
  await rcon.send('eco set ' + buyer.username + ' 100000');
  await rcon.send('give ' + sellerName + ' diamond 64');
  await waitItem(seller, 'diamond', 10000);
  info('bots ready');

  // 1. partial purchase
  let lotId = -1;
  try {
    await sleep(800);
    const item = seller.inventory.items().find((i) => i.name === 'diamond');
    await seller.equip(item, 'hand');
    await sleep(400);
    const p = waitChat(seller, /выставлен/i);
    seller.chat('/ah sell 6400');
    lotId = Number(((await p).match(/#(\d+)/) || [])[1]);
    pass('fixed lot for partial', '#' + lotId);
  } catch (e) { fail('fixed lot for partial', e.message); }

  try {
    await sleep(800);
    const p = waitChat(buyer, /Куплено/i);
    buyer.chat('/ah buy ' + lotId + ' 10');
    pass('partial buy', await p);
    const n = await waitItem(buyer, 'diamond', 8000);
    if (n === 10) pass('partial amount received'); else fail('partial amount received', 'got ' + n);
  } catch (e) { fail('partial buy', e.message); }

  try {
    const out = (await rcon.send('ah admin info ' + lotId)).replace(/\u00a7./g, '').trim();
    if (/x54/.test(out) && /5,?400/.test(out)) pass('remainder 54 / 5400', out);
    else fail('remainder 54 / 5400', out);
  } catch (e) { fail('remainder check', e.message); }

  // 2. sell menu
  let menuLot = -1;
  try {
    await sleep(800);
    await rcon.send('give ' + sellerName + ' diamond 1');
    await waitItem(seller, 'diamond', 8000);
    const wp = waitWindow(seller);
    seller.chat('/ah sellmenu 500');
    const win = await wp;
    await sleep(700);
    const item = seller.inventory.items().find((i) => i.name === 'diamond');
    if (!item) throw new Error('no diamond in inventory');
    const invSlot = item.slot;
    const winSlot = invSlot >= 36 ? 54 + (invSlot - 36) : 27 + (invSlot - 9);
    click(seller, winSlot, 0);
    await sleep(400);
    click(seller, 0, 0);
    await sleep(500);
    const p = waitChat(seller, /выставлен/i);
    click(seller, 22, 0);
    const msg = await p;
    menuLot = Number((msg.match(/#(\d+)/) || [])[1]);
    pass('sell menu listing', msg);
  } catch (e) { fail('sell menu listing', e.message); }

  try {
    await sleep(800);
    const p = waitChat(buyer, /Куплено/i);
    buyer.chat('/ah buy ' + menuLot);
    pass('sell menu lot bought', await p);
  } catch (e) { fail('sell menu lot bought', e.message); }

  // 3. balances + log
  try {
    const sb = balanceOf(await rcon.send('balance ' + sellerName));
    info('seller balance=' + sb);
    if (sb === 1425) pass('seller payout 950 + 475'); else fail('seller payout', 'balance=' + sb);
  } catch (e) { fail('seller payout', e.message); }

  try {
    const log = fs.readFileSync(LOG_FILE, 'utf8');
    if (/BUY_PART/.test(log) && /\| BUY \|/.test(log)) pass('transaction log BUY_PART + BUY');
    else fail('transaction log', 'markers missing');
  } catch (e) { fail('transaction log', e.message); }

  try { seller.quit(); buyer.quit(); } catch (e) {}
  rcon.end();
  await sleep(500);
  const bad = results.filter((r) => !r.ok);
  console.log('\nSUMMARY: ' + (results.length - bad.length) + '/' + results.length + ' passed');
  if (bad.length) console.log('FAILED: ' + bad.map((b) => b.n).join(', '));
  process.exit(bad.length ? 1 : 0);
})().catch((e) => { console.error('P1 E2E CRASH: ' + (e && e.stack ? e.stack : e)); process.exit(2); });
