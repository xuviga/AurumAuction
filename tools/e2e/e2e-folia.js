const mineflayer = require('mineflayer');
const { Rcon } = require('rcon-client');

const SERVER = {
  host: '127.0.0.1', port: 25571,
  version: '26.1', auth: 'offline',
};
const RCON = { host: '127.0.0.1', port: 25580, password: 'aurumtest' };
const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pass = (n, e) => { results.push({ n, ok: true }); console.log('PASS  ' + n + (e ? ' :: ' + e : '')); };
const fail = (n, e) => { results.push({ n, ok: false }); console.log('FAIL  ' + n + (e ? ' :: ' + e : '')); };

function waitChat(bot, re, timeout = 10000) {
  return new Promise((resolve, reject) => {
    const h = (m) => { if (re.test(m)) { clearTimeout(t); bot.removeListener('messagestr', h); resolve(m); } };
    const t = setTimeout(() => { bot.removeListener('messagestr', h); reject(new Error('chat timeout ' + re)); }, timeout);
    bot.on('messagestr', h);
  });
}
function waitWindow(bot, timeout = 12000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('window timeout')), timeout);
    bot.once('windowOpen', (w) => { clearTimeout(t); resolve(w); });
  });
}
function click(bot, slot) {
  try { const p = bot.clickWindow(slot, 0, 0); if (p && p.catch) p.catch(() => {}); } catch (e) {}
}
function title(w) { try { return typeof w.title === 'string' ? w.title : JSON.stringify(w.title); } catch (e) { return '?'; } }

(async () => {
  const rcon = await Rcon.connect(RCON);
  const bot = await new Promise((resolve, reject) => {
    const b = mineflayer.createBot({ ...SERVER, username: 'FoliaBot' });
    b.once('spawn', () => resolve(b));
    b.once('error', reject);
    setTimeout(() => reject(new Error('spawn timeout')), 30000);
  });
  await sleep(1500);

  try {
    const wp = waitWindow(bot);
    bot.chat('/ah');
    const w = await wp;
    pass('GUI opens on Folia', title(w).slice(0, 40));
    await sleep(900);
    const search = w.slots.findIndex((s) => s && s.name === 'compass');
    if (search < 0) throw new Error('search button missing');
    const prompt = waitChat(bot, /Введите в чат|Type the item/i);
    click(bot, search);
    pass('search prompt', await prompt);
    const none = waitChat(bot, /ничего не найдено|Nothing found/i);
    bot.chat('diamond');
    pass('chat input on Folia', await none);
  } catch (e) { fail('GUI/chat on Folia', e.message); }

  try {
    const wp = waitWindow(bot);
    bot.chat('/ah history');
    pass('history opens', title(await wp).slice(0, 40));
  } catch (e) { fail('history opens', e.message); }

  try {
    const query = 'diamond' + Math.floor(Math.random() * 10000);
    const p = waitChat(bot, /Подписка добавлена|Subscription added/i);
    bot.chat('/ah watch ' + query);
    pass('watch on Folia', await p);
  } catch (e) { fail('watch on Folia', e.message); }

  try {
    const p = waitChat(bot, /нет завершённых сделок|No completed trades/i);
    bot.chat('/ah top');
    pass('top on Folia', await p);
  } catch (e) { fail('top on Folia', e.message); }

  try {
    const p = waitChat(bot, /нет выигранных|no won lots/i);
    bot.chat('/ah claims');
    pass('claims on Folia', await p);
  } catch (e) { fail('claims on Folia', e.message); }

  try {
    const p = waitChat(bot, /Экономика недоступна|Economy is unavailable/i);
    bot.chat('/ah sell 100');
    pass('economy guard', await p);
  } catch (e) { fail('economy guard', e.message); }

  try { bot.quit(); } catch (e) {}
  rcon.end();
  await sleep(500);
  const bad = results.filter((r) => !r.ok);
  console.log('\nSUMMARY: ' + (results.length - bad.length) + '/' + results.length + ' passed');
  if (bad.length) console.log('FAILED: ' + bad.map((b) => b.n).join(', '));
  process.exit(bad.length ? 1 : 0);
})().catch((e) => { console.error('FOLIA E2E CRASH: ' + (e && e.stack ? e.stack : e)); process.exit(2); });
