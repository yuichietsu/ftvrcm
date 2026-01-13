import http from 'node:http';
import { spawn } from 'node:child_process';

const PORT = parseInt(process.env.PORT ?? '8787', 10);
const HOST = process.env.HOST ?? '0.0.0.0';

function hasFlag(name, shortName) {
  const argv = process.argv.slice(2);
  if (argv.includes(name)) return true;
  if (shortName && argv.includes(shortName)) return true;
  return false;
}

function envBool(name) {
  const v = process.env[name];
  if (v == null) return false;
  return v === '1' || v.toLowerCase() === 'true' || v.toLowerCase() === 'yes' || v.toLowerCase() === 'on';
}

const DEBUG = hasFlag('--debug', '-d') || envBool('PROXY_DEBUG');
const LOG_BODY = hasFlag('--log-body') || envBool('PROXY_LOG_BODY');
const LOG_ADB = hasFlag('--log-adb') || envBool('PROXY_LOG_ADB');

// Optional shared secret. If set, clients must send `X-Auth-Token: <token>`.
const PROXY_TOKEN = process.env.PROXY_TOKEN ?? '';

// Default target device serial. Example: "192.168.11.12:5555".
// You can also pass `serial` per-request in JSON body.
const DEFAULT_SERIAL = process.env.FIRETV_SERIAL ?? '';

function json(res, statusCode, body) {
  const payload = JSON.stringify(body);
  res.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => {
      data += chunk;
      // 1MB limit
      if (data.length > 1024 * 1024) {
        reject(new Error('payload too large'));
        req.destroy();
      }
    });
    req.on('end', () => {
      if (!data) return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch (e) {
        reject(new Error('invalid json'));
      }
    });
    req.on('error', reject);
  });
}

function requireAuth(req) {
  if (!PROXY_TOKEN) return true;
  const token = req.headers['x-auth-token'];
  return typeof token === 'string' && token === PROXY_TOKEN;
}

function nowIso() {
  return new Date().toISOString();
}

function safeHeadersForLog(req) {
  const headers = { ...req.headers };
  if (headers['x-auth-token']) headers['x-auth-token'] = '<redacted>';
  return headers;
}

function log(...args) {
  // eslint-disable-next-line no-console
  console.log(...args);
}

function runAdb(serial, args) {
  return new Promise((resolve) => {
    const adbArgs = [];
    if (serial) adbArgs.push('-s', serial);
    adbArgs.push(...args);

    const child = spawn('adb', adbArgs, { stdio: ['ignore', 'pipe', 'pipe'] });

    let stdout = '';
    let stderr = '';

    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');

    child.stdout.on('data', (d) => (stdout += d));
    child.stderr.on('data', (d) => (stderr += d));

    child.on('close', (code) => {
      resolve({
        ok: code === 0,
        exitCode: code ?? -1,
        stdout: stdout.trim(),
        stderr: stderr.trim(),
        command: ['adb', ...adbArgs].join(' '),
      });
    });

    child.on('error', (e) => {
      resolve({
        ok: false,
        exitCode: -1,
        stdout: '',
        stderr: String(e?.message ?? e),
        command: ['adb', ...adbArgs].join(' '),
      });
    });
  });
}

function getSerial(body) {
  const serial = typeof body?.serial === 'string' ? body.serial : '';
  return serial || DEFAULT_SERIAL;
}

const server = http.createServer(async (req, res) => {
  const requestId = Math.random().toString(16).slice(2, 10);
  const startedAt = Date.now();
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);

  try {
    if (!requireAuth(req)) {
      if (DEBUG) {
        log(`${nowIso()} [${requestId}] 401 unauthorized ${req.method} ${url.pathname} from=${req.socket.remoteAddress}`);
      }
      return json(res, 401, { ok: false, error: 'unauthorized' });
    }

    const respond = (statusCode, body) => {
      if (DEBUG) {
        const ms = Date.now() - startedAt;
        const extra =
          body && typeof body === 'object' && 'exitCode' in body
            ? ` exitCode=${body.exitCode} ok=${body.ok}`
            : '';
        log(`${nowIso()} [${requestId}] ${statusCode} ${req.method} ${url.pathname} ${ms}ms from=${req.socket.remoteAddress}${extra}`);
      }
      return json(res, statusCode, body);
    };

    if (req.method === 'GET' && url.pathname === '/health') {
      if (DEBUG) {
        log(`${nowIso()} [${requestId}] health from=${req.socket.remoteAddress}`);
      }
      return respond(200, { ok: true });
    }

    if (req.method !== 'POST') {
      return respond(405, { ok: false, error: 'method not allowed' });
    }

    const body = await readJson(req);
    if (DEBUG) {
      log(`${nowIso()} [${requestId}] request headers=${JSON.stringify(safeHeadersForLog(req))}`);
      if (LOG_BODY) {
        log(`${nowIso()} [${requestId}] request body=${JSON.stringify(body)}`);
      }
    }

    const serial = getSerial(body);
    if (!serial) {
      return respond(400, { ok: false, error: 'missing serial (set FIRETV_SERIAL or pass serial in body)' });
    }

    if (url.pathname === '/tap') {
      const x = Number(body.x);
      const y = Number(body.y);
      if (!Number.isFinite(x) || !Number.isFinite(y)) {
        return respond(400, { ok: false, error: 'invalid x/y' });
      }
      const result = await runAdb(serial, ['shell', 'input', 'tap', String(Math.round(x)), String(Math.round(y))]);
      if (DEBUG && LOG_ADB) {
        log(`${nowIso()} [${requestId}] adb ${result.command}`);
        if (result.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(result.stderr)}`);
        if (result.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(result.stdout)}`);
      }
      return respond(result.ok ? 200 : 500, result);
    }

    if (url.pathname === '/swipe') {
      const x1 = Number(body.x1);
      const y1 = Number(body.y1);
      const x2 = Number(body.x2);
      const y2 = Number(body.y2);
      const durationMs = Number.isFinite(Number(body.durationMs)) ? Math.max(0, Math.round(Number(body.durationMs))) : 200;

      if (![x1, y1, x2, y2].every(Number.isFinite)) {
        return respond(400, { ok: false, error: 'invalid x1/y1/x2/y2' });
      }

      const result = await runAdb(serial, [
        'shell',
        'input',
        'swipe',
        String(Math.round(x1)),
        String(Math.round(y1)),
        String(Math.round(x2)),
        String(Math.round(y2)),
        String(durationMs),
      ]);
      if (DEBUG && LOG_ADB) {
        log(`${nowIso()} [${requestId}] adb ${result.command}`);
        if (result.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(result.stderr)}`);
        if (result.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(result.stdout)}`);
      }
      return respond(result.ok ? 200 : 500, result);
    }

    if (url.pathname === '/longPress') {
      const x = Number(body.x);
      const y = Number(body.y);
      const durationMs = Number.isFinite(Number(body.durationMs)) ? Math.max(0, Math.round(Number(body.durationMs))) : 600;

      if (!Number.isFinite(x) || !Number.isFinite(y)) {
        return respond(400, { ok: false, error: 'invalid x/y' });
      }

      // long press via swipe-to-self
      const result = await runAdb(serial, [
        'shell',
        'input',
        'swipe',
        String(Math.round(x)),
        String(Math.round(y)),
        String(Math.round(x)),
        String(Math.round(y)),
        String(durationMs),
      ]);
      if (DEBUG && LOG_ADB) {
        log(`${nowIso()} [${requestId}] adb ${result.command}`);
        if (result.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(result.stderr)}`);
        if (result.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(result.stdout)}`);
      }
      return respond(result.ok ? 200 : 500, result);
    }

    return respond(404, { ok: false, error: 'not found' });
  } catch (e) {
    const message = String(e?.message ?? e);
    if (DEBUG) {
      log(`${nowIso()} [${requestId}] 500 error=${JSON.stringify(message)}`);
    }
    return json(res, 500, { ok: false, error: message });
  }
});

server.listen(PORT, HOST, () => {
  log(`ftvrcm-proxy-server listening on http://${HOST}:${PORT}`);
  if (DEBUG) {
    log(`debug enabled: LOG_BODY=${LOG_BODY} LOG_ADB=${LOG_ADB}`);
  }
  if (!DEFAULT_SERIAL) {
    log('WARN: FIRETV_SERIAL is not set; clients must pass serial in request body.');
  }
  if (!PROXY_TOKEN) {
    log('WARN: PROXY_TOKEN is not set; requests are not authenticated.');
  }
});
