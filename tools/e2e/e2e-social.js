const mineflayer = require('mineflayer');
const { Rcon } = require('rcon-client');

const SERVER = {
  host: '127.0.0.1', port: Number(process.env.MC_PORT || 25568),
  version: process.env.MC_VERSION || '26.1', auth: 'offline',
};
const RCON = { host: '127.0.0.1', port: Number(process.env.RCON_PORT || 25577), password: 'aurumtest' };

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
function title(w) { try { return typeof w.title === 'string' ? w.title : JSON.stringify(w.title); } catch (e) { return '?'; } }
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
  const sellerName = 'Seller' + Math.floor(Math.random() * 100000);
  const seller = await spawnBot(sellerName);
  const buyer = await spawnBot('BuyerBot');
  await sleep(1500);
  await rcon.send('gamemode creative ' + sellerName);
  await rcon.send('gamemode creative BuyerBot');
  await rcon.send('clear ' + sellerName);
  await rcon.send('clear BuyerBot');
  await rcon.send('eco set BuyerBot 100000');
  await rcon.send('give ' + sellerName + ' diamond 64');
  await waitItem(seller, 'diamond', 10000);

  // 1. seed one completed sale
  let lotId = -1;
  try {
    await sleep(700);
    const item = seller.inventory.items().find((i) => i.name === 'diamond');
    await seller.equip(item, 'hand');
    await sleep(400);
    const p = waitChat(seller, /выставлен/i);
    seller.chat('/ah sell 1000');
    lotId = Number(((await p).match(/#(\d+)/) || [])[1]);
    pass('seed lot', '#' + lotId);
  } catch (e) { fail('seed lot', e.message); }

  try {
    await sleep(700);
    const wp = waitWindow(buyer);
    buyer.chat('/ah');
    const w = await wp;
    const slot = w.slots.findIndex((s) => s && s.name === 'diamond');
    const wp2 = waitWindow(buyer);
    click(buyer, slot, 0);
    const cw = await wp2;
    const confirm = cw.slots.findIndex((s) => s && s.name === 'lime_concrete');
    await sleep(600);
    const p = waitChat(buyer, /Куплено/i);
    click(buyer, confirm, 0);
    pass('seed purchase', await p);
  } catch (e) { fail('seed purchase', e.message); }

  // 2. history GUI via main menu button (slot 52)
  try {
    await sleep(700);
    const wp = waitWindow(buyer);
    buyer.chat('/ah');
    const main = await wp;
    await sleep(900);
    const histSlot = main.slots.findIndex((s) => s && s.name === 'written_book');
    if (histSlot < 0) throw new Error('history button not found: '
      + main.slots.slice(45, 54).map((s, i) => (45 + i) + ':' + (s ? s.name : '-')).join(', '));
    const wp2 = waitWindow(buyer);
    click(buyer, histSlot, 0);
    const w = await wp2;
    const items = w.slots.filter((s) => s).length;
    pass('history GUI opens', title(w).slice(0, 50) + ', items=' + items);
    try { buyer.closeWindow(w); } catch (e) {}
  } catch (e) { fail('history GUI opens', e.message); }

  // 3. /ah history command
  try {
    await sleep(500);
    const wp = waitWindow(buyer);
    buyer.chat('/ah history');
    const w = await wp;
    pass('history command', title(w).slice(0, 50));
    try { buyer.closeWindow(w); } catch (e) {}
  } catch (e) { fail('history command', e.message); }

  // 4. /ah top
  try {
    await sleep(500);
    const header = waitChat(buyer, /Топ продавцов/i);
    const line = waitChat(buyer, new RegExp(sellerName, 'i'));
    buyer.chat('/ah top');
    pass('top header', await header);
    pass('top line', await line);
  } catch (e) { fail('top', e.message); }

  // 5. watchlist add + new lot notification
  try {
    await sleep(500);
    const added = waitChat(buyer, /Подписка добавлена/i);
    buyer.chat('/ah watch diamond');
    pass('watch add', await added);
  } catch (e) { fail('watch add', e.message); }

  try {
    await sleep(500);
    await rcon.send('give ' + sellerName + ' diamond 1');
    await waitItem(seller, 'diamond', 8000);
    const item = seller.inventory.items().find((i) => i.name === 'diamond');
    await seller.equip(item, 'hand');
    await sleep(400);
    const notified = waitChat(buyer, /Новый лот по подписке/i, 10000);
    const listed = waitChat(seller, /выставлен/i);
    seller.chat('/ah sell 500');
    pass('watch lot listed', await listed);
    pass('watch notification', await notified);
  } catch (e) { fail('watch notification', e.message); }

  // 6. watch list + remove
  try {
    await sleep(500);
    const header = waitChat(buyer, /Ваши подписки/i);
    const line = waitChat(buyer, /diamond/i);
    buyer.chat('/ah watch list');
    pass('watch list header', await header);
    pass('watch list line', await line);
  } catch (e) { fail('watch list', e.message); }

  try {
    await sleep(500);
    const p = waitChat(buyer, /Подписка удалена/i);
    buyer.chat('/ah watch remove diamond');
    pass('watch remove', await p);
  } catch (e) { fail('watch remove', e.message); }

  try { seller.quit(); buyer.quit(); } catch (e) {}
  rcon.end();
  await sleep(500);
  const bad = results.filter((r) => !r.ok);
  console.log('\nSUMMARY: ' + (results.length - bad.length) + '/' + results.length + ' passed');
  if (bad.length) console.log('FAILED: ' + bad.map((b) => b.n).join(', '));
  process.exit(bad.length ? 1 : 0);
})().catch((e) => { console.error('SOCIAL E2E CRASH: ' + (e && e.stack ? e.stack : e)); process.exit(2); });
