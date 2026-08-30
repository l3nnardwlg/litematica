'use strict';

const http = require('http');

const port = Number(process.env.PORT || 8787);
const token = process.env.SHARED_TOKEN || '';
const states = new Map();

function send(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload),
    'cache-control': 'no-store'
  });
  res.end(payload);
}

function authorized(req) {
  if (!token) return true;
  return req.headers.authorization === `Bearer ${token}`;
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.setEncoding('utf8');
    req.on('data', chunk => {
      raw += chunk;
      if (raw.length > 64 * 1024) {
        reject(new Error('payload too large'));
        req.destroy();
      }
    });
    req.on('end', () => {
      try {
        resolve(JSON.parse(raw || '{}'));
      } catch (error) {
        reject(error);
      }
    });
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  if (!authorized(req)) {
    send(res, 401, { error: 'unauthorized' });
    return;
  }

  if (req.method === 'GET' && req.url === '/health') {
    send(res, 200, { ok: true, placements: states.size });
    return;
  }

  if (req.method === 'GET' && req.url === '/api/states') {
    send(res, 200, Array.from(states.values()));
    return;
  }

  if (req.method === 'POST' && req.url === '/api/states') {
    try {
      const state = await readJson(req);
      if (!state || typeof state.id !== 'string' || !state.id) {
        send(res, 400, { error: 'missing id' });
        return;
      }

      const previous = states.get(state.id);
      const incomingRevision = Number.isFinite(Number(state.revision)) ? Number(state.revision) : 0;
      const previousRevision = previous ? Number(previous.revision) || 0 : 0;

      state.revision = Math.max(previousRevision + 1, incomingRevision);
      state.updatedAt = Date.now();
      states.set(state.id, state);

      send(res, 200, state);
    } catch (error) {
      send(res, 400, { error: 'invalid json' });
    }
    return;
  }

  send(res, 404, { error: 'not found' });
});

server.listen(port, '0.0.0.0', () => {
  console.log(`Litematica shared relay listening on :${port}`);
});
