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

function envNum(name, fallback) {
  const v = process.env[name];
  if (v == null || v === '') return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

function getArgValue(longName, fallback) {
  const argv = process.argv.slice(2);
  const prefix = `${longName}=`;
  const hit = argv.find((a) => a === longName || a.startsWith(prefix));
  if (!hit) return fallback;
  if (hit === longName) {
    const idx = argv.indexOf(hit);
    const v = argv[idx + 1];
    return v == null ? fallback : v;
  }
  return hit.slice(prefix.length);
}

function getArgNum(longName, fallback) {
  const v = getArgValue(longName, undefined);
  if (v == null) return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

const DEBUG = hasFlag('--debug', '-d') || envBool('PROXY_DEBUG');
const LOG_BODY = hasFlag('--log-body') || envBool('PROXY_LOG_BODY');
const LOG_ADB = hasFlag('--log-adb') || envBool('PROXY_LOG_ADB');

// Optional shared secret. If set, clients must send `X-Auth-Token: <token>`.
const PROXY_TOKEN = process.env.PROXY_TOKEN ?? '';

// Default target device serial. Example: "192.168.11.12:5555".
// You can also pass `serial` per-request in JSON body.
const DEFAULT_SERIAL = process.env.FIRETV_SERIAL ?? '';

// ADB reliability knobs (env and/or CLI args)
// - env: ADB_TIMEOUT_MS, ADB_CONNECT_TIMEOUT_MS, ADB_CONNECT_COOLDOWN_MS, ADB_AUTH_FAIL_THRESHOLD,
//        ADB_AUTH_FAIL_WINDOW_MS, ADB_REAUTH_COOLDOWN_MS, ADB_REAUTH_MAX_PER_HOUR
// - args: --adb-timeout-ms, --adb-connect-timeout-ms, --adb-connect-cooldown-ms, --adb-auth-fail-threshold,
//         --adb-auth-fail-window-ms, --adb-reauth-cooldown-ms, --adb-reauth-max-per-hour
const ADB_TIMEOUT_MS = Math.max(1000, Math.round(getArgNum('--adb-timeout-ms', envNum('ADB_TIMEOUT_MS', 8000))));
const ADB_CONNECT_TIMEOUT_MS = Math.max(
  1000,
  Math.round(getArgNum('--adb-connect-timeout-ms', envNum('ADB_CONNECT_TIMEOUT_MS', 8000))),
);
const ADB_CONNECT_COOLDOWN_MS = Math.max(
  0,
  Math.round(getArgNum('--adb-connect-cooldown-ms', envNum('ADB_CONNECT_COOLDOWN_MS', 5000))),
);

const ADB_AUTH_FAIL_THRESHOLD = Math.max(
  1,
  Math.round(getArgNum('--adb-auth-fail-threshold', envNum('ADB_AUTH_FAIL_THRESHOLD', 3))),
);
const ADB_AUTH_FAIL_WINDOW_MS = Math.max(
  1000,
  Math.round(getArgNum('--adb-auth-fail-window-ms', envNum('ADB_AUTH_FAIL_WINDOW_MS', 60_000))),
);
const ADB_REAUTH_COOLDOWN_MS = Math.max(
  0,
  Math.round(getArgNum('--adb-reauth-cooldown-ms', envNum('ADB_REAUTH_COOLDOWN_MS', 10 * 60_000))),
);
const ADB_REAUTH_MAX_PER_HOUR = Math.max(
  0,
  Math.round(getArgNum('--adb-reauth-max-per-hour', envNum('ADB_REAUTH_MAX_PER_HOUR', 3))),
);

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

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function runAdbRaw(adbArgs, timeoutMs) {
  return new Promise((resolve) => {
    const child = spawn('adb', adbArgs, { stdio: ['ignore', 'pipe', 'pipe'] });

    let stdout = '';
    let stderr = '';
    let killedByTimeout = false;

    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');

    child.stdout.on('data', (d) => (stdout += d));
    child.stderr.on('data', (d) => (stderr += d));

    const timer =
      timeoutMs > 0
        ? setTimeout(() => {
            killedByTimeout = true;
            child.kill('SIGKILL');
          }, timeoutMs)
        : null;

    child.on('close', (code) => {
      if (timer) clearTimeout(timer);
      const out = stdout.trim();
      const err = stderr.trim();
      resolve({
        ok: code === 0 && !killedByTimeout,
        exitCode: killedByTimeout ? -9 : code ?? -1,
        stdout: out,
        stderr: err,
        timedOut: killedByTimeout,
        command: ['adb', ...adbArgs].join(' '),
      });
    });

    child.on('error', (e) => {
      if (timer) clearTimeout(timer);
      resolve({
        ok: false,
        exitCode: -1,
        stdout: '',
        stderr: String(e?.message ?? e),
        timedOut: false,
        command: ['adb', ...adbArgs].join(' '),
      });
    });
  });
}

function runAdbOnce(serial, args, timeoutMs = ADB_TIMEOUT_MS) {
  const adbArgs = [];
  if (serial) adbArgs.push('-s', serial);
  adbArgs.push(...args);
  return runAdbRaw(adbArgs, timeoutMs);
}

function isLikelyTcpSerial(serial) {
  // Best-effort heuristic: host:port, or IPv4:port.
  if (!serial) return false;
  return serial.includes(':');
}

function classifyAdbError(result) {
  const text = `${result?.stdout ?? ''}\n${result?.stderr ?? ''}`.toLowerCase();

  if (text.includes('device unauthorized') || text.includes('unauthorized')) return 'unauthorized';
  if (text.includes('failed to authenticate')) return 'unauthorized';

  if (text.includes('device offline') || text.includes('offline')) return 'offline';
  if (text.includes('no devices') || text.includes('device not found')) return 'not_found';
  if (text.includes('cannot connect') || text.includes('unable to connect') || text.includes('connection refused')) return 'connect_failed';
  if (result?.timedOut) return 'timeout';
  return '';
}

// Per-serial state (in-memory). This process is expected to be single-instance for local LAN.
const deviceState = new Map();

function getDeviceState(serial) {
  if (!deviceState.has(serial)) {
    deviceState.set(serial, {
      lastConnectAttemptAt: 0,
      connectAttempts: 0,
      authFailTimes: [],
      lastReauthAt: 0,
      reauthTimes: [],
    });
  }
  return deviceState.get(serial);
}

async function adbGetState(serial) {
  const r = await runAdbOnce(serial, ['get-state'], 2000);
  // When unauthorized/offline/notfound, adb tends to print errors to stderr.
  if (r.ok && r.stdout) return r.stdout.trim();
  const kind = classifyAdbError(r);
  if (kind === 'unauthorized') return 'unauthorized';
  if (kind === 'offline') return 'offline';
  if (kind === 'not_found') return 'not_found';
  return 'unknown';
}

async function adbConnect(serial, requestId) {
  // Only meaningful for TCP/IP serials (ip:port). For USB serials, connect doesn't apply.
  if (!isLikelyTcpSerial(serial)) return { ok: true, skipped: true, reason: 'non-tcp-serial' };

  const st = getDeviceState(serial);
  const now = Date.now();
  if (ADB_CONNECT_COOLDOWN_MS > 0 && now - st.lastConnectAttemptAt < ADB_CONNECT_COOLDOWN_MS) {
    return { ok: true, skipped: true, reason: 'connect-cooldown' };
  }

  st.lastConnectAttemptAt = now;
  st.connectAttempts += 1;

  if (DEBUG && LOG_ADB) {
    log(`${nowIso()} [${requestId}] adb connect attempt serial=${serial}`);
  }

  const r = await runAdbRaw(['connect', serial], ADB_CONNECT_TIMEOUT_MS);
  if (DEBUG && LOG_ADB) {
    log(`${nowIso()} [${requestId}] adb ${r.command}`);
    if (r.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(r.stderr)}`);
    if (r.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(r.stdout)}`);
  }
  return r;
}

function recordAuthFailure(serial) {
  const st = getDeviceState(serial);
  const now = Date.now();
  st.authFailTimes.push(now);
  // keep within window
  const cutoff = now - ADB_AUTH_FAIL_WINDOW_MS;
  while (st.authFailTimes.length && st.authFailTimes[0] < cutoff) st.authFailTimes.shift();
}

function shouldReauth(serial) {
  if (ADB_REAUTH_MAX_PER_HOUR === 0) return false;

  const st = getDeviceState(serial);
  const now = Date.now();

  // Cooldown to avoid hammering
  if (ADB_REAUTH_COOLDOWN_MS > 0 && now - st.lastReauthAt < ADB_REAUTH_COOLDOWN_MS) return false;

  // Threshold within window
  const cutoff = now - ADB_AUTH_FAIL_WINDOW_MS;
  const recentFails = st.authFailTimes.filter((t) => t >= cutoff).length;
  if (recentFails < ADB_AUTH_FAIL_THRESHOLD) return false;

  // Hourly cap
  const hourCutoff = now - 60 * 60_000;
  st.reauthTimes = st.reauthTimes.filter((t) => t >= hourCutoff);
  if (st.reauthTimes.length >= ADB_REAUTH_MAX_PER_HOUR) return false;

  return true;
}

async function adbReauth(serial, requestId) {
  const st = getDeviceState(serial);
  const now = Date.now();
  st.lastReauthAt = now;
  st.reauthTimes.push(now);

  // Best-effort recovery sequence:
  // 1) disconnect target (TCP)
  // 2) kill-server/start-server
  // 3) connect again (TCP)
  // Note: actual authorization requires user acceptance on the device.
  const steps = [];
  if (isLikelyTcpSerial(serial)) {
    steps.push(() => runAdbRaw(['disconnect', serial], 3000));
  }
  steps.push(() => runAdbRaw(['kill-server'], 3000));
  steps.push(() => runAdbRaw(['start-server'], 5000));
  if (isLikelyTcpSerial(serial)) {
    steps.push(() => runAdbRaw(['connect', serial], ADB_CONNECT_TIMEOUT_MS));
  }

  if (DEBUG) {
    log(`${nowIso()} [${requestId}] reauth starting serial=${serial}`);
  }

  const results = [];
  for (const step of steps) {
    // Small spacing to avoid immediate hammering when adb is restarting.
    // This is intentionally tiny to keep UX OK.
    // eslint-disable-next-line no-await-in-loop
    const r = await step();
    results.push(r);
    if (DEBUG && LOG_ADB) {
      log(`${nowIso()} [${requestId}] adb ${r.command}`);
      if (r.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(r.stderr)}`);
      if (r.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(r.stdout)}`);
    }
    // eslint-disable-next-line no-await-in-loop
    await sleep(150);
  }

  return { ok: results.every((r) => r.ok), steps: results };
}

async function ensureAdbReady(serial, requestId) {
  const state = await adbGetState(serial);
  if (state === 'device') return { ok: true, state };

  if (state === 'unauthorized') {
    recordAuthFailure(serial);
    if (shouldReauth(serial)) {
      await adbReauth(serial, requestId);
    }
    return { ok: false, state: 'unauthorized' };
  }

  if (state === 'offline' || state === 'not_found' || state === 'unknown') {
    await adbConnect(serial, requestId);
    const after = await adbGetState(serial);
    return { ok: after === 'device', state: after };
  }

  return { ok: false, state };
}

async function runAdb(serial, args, requestId) {
  // 1) ensure connected/authorized (best-effort)
  await ensureAdbReady(serial, requestId);

  // 2) execute command
  let result = await runAdbOnce(serial, args, ADB_TIMEOUT_MS);

  // 3) on certain errors, try to recover once
  const kind = classifyAdbError(result);
  if (kind === 'unauthorized') {
    recordAuthFailure(serial);
    if (shouldReauth(serial)) {
      await adbReauth(serial, requestId);
    }
    // retry once (even if not reauthed, device might have been accepted manually)
    result = await runAdbOnce(serial, args, ADB_TIMEOUT_MS);
  } else if (kind === 'offline' || kind === 'not_found' || kind === 'connect_failed') {
    await adbConnect(serial, requestId);
    result = await runAdbOnce(serial, args, ADB_TIMEOUT_MS);
  }

  return result;
}

function getSerial(body) {
  const serial = typeof body?.serial === 'string' ? body.serial : '';
  return serial || DEFAULT_SERIAL;
}

// Avoid piling up ADB input commands when the client is spamming keys.
// We intentionally do NOT queue. If another input is running, we reject new ones.
let inputInFlight = false;

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
      if (inputInFlight) {
        return respond(409, { ok: false, error: 'busy' });
      }
      inputInFlight = true;

      const x = Number(body.x);
      const y = Number(body.y);
      if (!Number.isFinite(x) || !Number.isFinite(y)) {
        inputInFlight = false;
        return respond(400, { ok: false, error: 'invalid x/y' });
      }

      try {
        const result = await runAdb(serial, ['shell', 'input', 'tap', String(Math.round(x)), String(Math.round(y))], requestId);
        if (DEBUG && LOG_ADB) {
          log(`${nowIso()} [${requestId}] adb ${result.command}`);
          if (result.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(result.stderr)}`);
          if (result.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(result.stdout)}`);
        }
        return respond(result.ok ? 200 : 500, result);
      } finally {
        inputInFlight = false;
      }
    }

    if (url.pathname === '/doubleTap') {
      if (inputInFlight) {
        return respond(409, { ok: false, error: 'busy' });
      }
      inputInFlight = true;

      const x = Number(body.x);
      const y = Number(body.y);
      if (!Number.isFinite(x) || !Number.isFinite(y)) {
        inputInFlight = false;
        return respond(400, { ok: false, error: 'invalid x/y' });
      }

      const rx = String(Math.round(x));
      const ry = String(Math.round(y));
      const shell = `input tap ${rx} ${ry} & sleep 0.1; input tap ${rx} ${ry}`;

      try {
        const result = await runAdb(serial, ['shell', shell], requestId);
        if (DEBUG && LOG_ADB) {
          log(`${nowIso()} [${requestId}] adb ${result.command}`);
          if (result.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(result.stderr)}`);
          if (result.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(result.stdout)}`);
        }
        return respond(result.ok ? 200 : 500, result);
      } finally {
        inputInFlight = false;
      }
    }

    if (url.pathname === '/swipe') {
      if (inputInFlight) {
        return respond(409, { ok: false, error: 'busy' });
      }
      inputInFlight = true;

      const x1 = Number(body.x1);
      const y1 = Number(body.y1);
      const x2 = Number(body.x2);
      const y2 = Number(body.y2);
      const durationMs = Number.isFinite(Number(body.durationMs)) ? Math.max(0, Math.round(Number(body.durationMs))) : 200;

      if (![x1, y1, x2, y2].every(Number.isFinite)) {
        inputInFlight = false;
        return respond(400, { ok: false, error: 'invalid x1/y1/x2/y2' });
      }

      try {
        const result = await runAdb(serial, [
          'shell',
          'input',
          'swipe',
          String(Math.round(x1)),
          String(Math.round(y1)),
          String(Math.round(x2)),
          String(Math.round(y2)),
          String(durationMs),
        ], requestId);
        if (DEBUG && LOG_ADB) {
          log(`${nowIso()} [${requestId}] adb ${result.command}`);
          if (result.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(result.stderr)}`);
          if (result.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(result.stdout)}`);
        }
        return respond(result.ok ? 200 : 500, result);
      } finally {
        inputInFlight = false;
      }
    }

    if (url.pathname === '/pinchIn' || url.pathname === '/pinchOut') {
      if (inputInFlight) {
        return respond(409, { ok: false, error: 'busy' });
      }
      inputInFlight = true;

      const x1Start = Number(body.x1Start);
      const y1Start = Number(body.y1Start);
      const x1End = Number(body.x1End);
      const y1End = Number(body.y1End);
      const x2Start = Number(body.x2Start);
      const y2Start = Number(body.y2Start);
      const x2End = Number(body.x2End);
      const y2End = Number(body.y2End);
      const durationMs = Number.isFinite(Number(body.durationMs)) ? Math.max(0, Math.round(Number(body.durationMs))) : 240;

      if (![x1Start, y1Start, x1End, y1End, x2Start, y2Start, x2End, y2End].every(Number.isFinite)) {
        inputInFlight = false;
        return respond(400, { ok: false, error: 'invalid pinch coordinates' });
      }

      const centerX = Math.round((x1Start + x2Start) / 2);
      const centerY = Math.round((y1Start + y2Start) / 2);
      const span = Math.max(40, Math.round(Math.abs(x1Start - x2Start) * 0.5));
      const delta = url.pathname === '/pinchOut' ? -span : span;
      const targetY = Math.round(centerY + delta);

      const shell =
        `input tap ${centerX} ${centerY} & sleep 0.1; ` +
        `input swipe ${centerX} ${centerY} ${centerX} ${targetY} ${durationMs};`;

      try {
        const result = await runAdb(serial, ['shell', shell], requestId);
        if (DEBUG && LOG_ADB) {
          log(`${nowIso()} [${requestId}] adb ${result.command}`);
          if (result.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(result.stderr)}`);
          if (result.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(result.stdout)}`);
        }
        return respond(result.ok ? 200 : 500, result);
      } finally {
        inputInFlight = false;
      }
    }

    if (url.pathname === '/longPress') {
      if (inputInFlight) {
        return respond(409, { ok: false, error: 'busy' });
      }
      inputInFlight = true;

      const x = Number(body.x);
      const y = Number(body.y);
      const durationMs = Number.isFinite(Number(body.durationMs)) ? Math.max(0, Math.round(Number(body.durationMs))) : 600;

      if (!Number.isFinite(x) || !Number.isFinite(y)) {
        inputInFlight = false;
        return respond(400, { ok: false, error: 'invalid x/y' });
      }

      // long press via swipe-to-self

      try {
        const result = await runAdb(serial, [
          'shell',
          'input',
          'swipe',
          String(Math.round(x)),
          String(Math.round(y)),
          String(Math.round(x)),
          String(Math.round(y)),
          String(durationMs),
        ], requestId);
        if (DEBUG && LOG_ADB) {
          log(`${nowIso()} [${requestId}] adb ${result.command}`);
          if (result.stderr) log(`${nowIso()} [${requestId}] adb stderr=${JSON.stringify(result.stderr)}`);
          if (result.stdout) log(`${nowIso()} [${requestId}] adb stdout=${JSON.stringify(result.stdout)}`);
        }
        return respond(result.ok ? 200 : 500, result);
      } finally {
        inputInFlight = false;
      }
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
    log(
      `adb knobs: TIMEOUT_MS=${ADB_TIMEOUT_MS} CONNECT_TIMEOUT_MS=${ADB_CONNECT_TIMEOUT_MS} CONNECT_COOLDOWN_MS=${ADB_CONNECT_COOLDOWN_MS} ` +
        `AUTH_FAIL_THRESHOLD=${ADB_AUTH_FAIL_THRESHOLD} AUTH_FAIL_WINDOW_MS=${ADB_AUTH_FAIL_WINDOW_MS} ` +
        `REAUTH_COOLDOWN_MS=${ADB_REAUTH_COOLDOWN_MS} REAUTH_MAX_PER_HOUR=${ADB_REAUTH_MAX_PER_HOUR}`,
    );
  }
  if (!DEFAULT_SERIAL) {
    log('WARN: FIRETV_SERIAL is not set; clients must pass serial in request body.');
  }
  if (!PROXY_TOKEN) {
    log('WARN: PROXY_TOKEN is not set; requests are not authenticated.');
  }
});
