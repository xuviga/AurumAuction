const mineflayer = require('mineflayer');
const { Rcon } = require('rcon-client');

const SERVER = {
  host: '127.0.0.1', port: Number(process.env.MC_PORT || 25568),
  version: process.env.MC_VERSION || '26.1', auth: 'offline',
};
const RCON = { host: '127.0.0.1', port: Number(process.env.RCON_PORT || 25577), password: 'aurumtest' };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function waitChat(bot, re, timeout = 8000) {
  return new Promise((resolve, reject) => {
    const h = (m) => { if (re.test(m)) { clearTimeout(t); bot.removeListener('messagestr', h); resolve(m); } };
    const t = setTimeout(() => { bot.removeListener('messagestr', h); reject(new Error('timeout')); }, timeout);
    bot.on('messagestr', h);
  });
}

(async () => {
  const rcon = await Rcon.connect(RCON);
  const bot = await new Promise((resolve, reject) => {
    const b = mineflayer.createBot({ ...SERVER, username: 'SellerBot' });
    b.once('spawn', () => resolve(b));
    b.once('error', reject);
    setTimeout(() => reject(new Error('spawn timeout')), 30000);
  });
  await sleep(1500);
  await rcon.send('gamemode creative SellerBot');
  await rcon.send('clear SellerBot');
  await rcon.send('eco set SellerBot 10000');
  await rcon.send('give SellerBot diamond 1');
  await sleep(2500);
  const item = bot.inventory.items().find((i) => i.name === 'diamond');
  if (!item) throw new Error('no diamond');
  await bot.equip(item, 'hand');
  await sleep(500);
  const p = waitChat(bot, /выставлен/i);
  bot.chat('/ah sell 1000');
  const msg = await p;
  const id = Number((msg.match(/#(\d+)/) || [])[1]);
  console.log('LOT=' + id);
  bot.quit();
  rcon.end();
  process.exit(0);
})().catch((e) => { console.error('ERR ' + e.message); process.exit(1); });
