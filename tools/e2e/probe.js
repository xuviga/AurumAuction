const mineflayer = require('mineflayer');

const versions = [false, '26.1', '1.21.11'];

(async () => {
  for (const v of versions) {
    await new Promise((resolve) => {
      const name = 'Probe' + Math.floor(Math.random() * 100000);
      const bot = mineflayer.createBot({
        host: '127.0.0.1',
        port: 25567,
        username: name,
        auth: 'offline',
        version: v,
      });
      let done = false;
      const finish = (ok, msg) => {
        if (done) return;
        done = true;
        console.log('version=' + v + ' => ' + (ok ? 'OK' : 'FAIL') + ' :: ' + msg);
        try { bot.quit(); } catch (e) {}
        setTimeout(resolve, 800);
      };
      bot.once('spawn', () => finish(true, 'spawned, protocol=' + bot.protocolVersion));
      bot.once('error', (e) => finish(false, e.message));
      bot.once('kicked', (k) => finish(false, 'kicked ' + JSON.stringify(k)));
      setTimeout(() => finish(false, 'timeout'), 12000);
    });
  }
  process.exit(0);
})();
