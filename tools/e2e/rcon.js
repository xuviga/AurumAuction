const { Rcon } = require('rcon-client');

(async () => {
  const port = Number(process.env.RCON_PORT || 25576);
  const r = await Rcon.connect({ host: '127.0.0.1', port, password: 'aurumtest' });
  const out = await r.send(process.argv[2] || 'list');
  console.log(out);
  r.end();
})().catch((e) => { console.error('RCON ERROR: ' + e.message); process.exit(1); });
