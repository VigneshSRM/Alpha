#!/usr/bin/env node
/**
 * End-to-end smoke test driven through the Playwright MCP server.
 *
 * The point of going through MCP rather than calling Playwright directly is
 * that the assertions are made against what the accessibility tree actually
 * exposes — the same thing a screen reader sees. A control whose accessible
 * name is wrong fails here even when it looks right on screen.
 *
 *   npm exec --package=@playwright/mcp -- playwright-mcp --help   # sanity
 *   BASE_URL=http://127.0.0.1:8080 node scripts/mcp-smoke.mjs
 *
 * Environment:
 *   BASE_URL     target (default http://127.0.0.1:8080)
 *   MCP_CLI      path to @playwright/mcp's cli.js; defaults to resolving the
 *                package, so `npm i -D @playwright/mcp` is enough
 *   BROWSER_BIN  optional browser executable (Playwright's bundled Chromium is
 *                used when unset)
 *   OUT_DIR      where screenshots land (default ./preview/sandbox-run)
 */

import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import path from 'node:path';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080';
const OUT = process.env.OUT_DIR || 'preview/sandbox-run';

const require = createRequire(import.meta.url);
const CLI = process.env.MCP_CLI || require.resolve('@playwright/mcp/cli.js');

const args = ['--headless', '--no-sandbox', '--isolated', '--image-responses', 'omit'];
if (process.env.BROWSER_BIN) args.push('--executable-path', process.env.BROWSER_BIN);

/* ---- minimal MCP stdio client ---------------------------------------- */

function connect(cliArgs) {
  const child = spawn(process.execPath, [CLI, ...cliArgs], { stdio: ['pipe', 'pipe', 'inherit'] });
  let buffer = '';
  let nextId = 0;
  const pending = new Map();

  child.stdout.on('data', chunk => {
    buffer += chunk.toString();
    let nl;
    while ((nl = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, nl).trim();
      buffer = buffer.slice(nl + 1);
      if (!line) continue;
      let msg;
      try { msg = JSON.parse(line); } catch { continue; }
      const slot = pending.get(msg.id);
      if (!slot) continue;
      pending.delete(msg.id);
      msg.error ? slot.reject(new Error(JSON.stringify(msg.error))) : slot.resolve(msg.result);
    }
  });

  const send = (method, params) => new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params: params || {} }) + '\n');
  });

  return { child, send };
}

const refFor = (snapshot, name) => {
  for (const line of snapshot.split('\n')) {
    if (!line.includes('"' + name + '"')) continue;
    const m = line.match(/\[ref=([a-zA-Z0-9]+)\]/);
    if (m) return m[1];
  }
  throw new Error(`no control named "${name}" in the accessibility tree`);
};

const noStrayBanner = `() => {
  const el = document.querySelector('[data-flash]');
  if (!el) return 'absent';
  const cs = getComputedStyle(el);
  return cs.display === 'none' || el.getBoundingClientRect().height === 0 ? 'hidden' : 'VISIBLE';
}`;

const failures = [];
const check = (label, condition, detail) => {
  const mark = condition ? 'PASS' : 'FAIL';
  console.log(`  [${mark}] ${label}${detail ? ' — ' + detail : ''}`);
  if (!condition) failures.push(label);
};

/* ---- the walkthrough -------------------------------------------------- */

async function main() {
  mkdirSync(OUT, { recursive: true });
  const { child, send } = connect(args);

  await send('initialize', {
    protocolVersion: '2025-06-18',
    capabilities: {},
    clientInfo: { name: 'alpha-smoke', version: '1.0.0' },
  });
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');

  const tool = async (name, toolArgs) => {
    const res = await send('tools/call', { name, arguments: toolArgs });
    const text = (res.content || []).filter(c => c.type === 'text').map(c => c.text).join('\n');
    if (res.isError) throw new Error(`${name}: ${text}`);
    return text;
  };
  const evaluate = async fn => {
    const raw = await tool('browser_evaluate', { function: fn });
    const body = raw.split('### Result')[1];
    return JSON.parse(body.split('### Ran Playwright code')[0].trim());
  };
  const shot = (file, fullPage = false) =>
    tool('browser_take_screenshot', { filename: path.join(OUT, file), scale: 'css', fullPage });

  try {
    await tool('browser_resize', { width: 1440, height: 900 });

    /* 1. sign in */
    await tool('browser_navigate', { url: `${BASE}/login` });
    let snap = await tool('browser_snapshot', {});
    check('sign-in page renders', snap.includes('"Welcome back"'));
    check('no stray error banner above the e-mail field',
      (await evaluate(noStrayBanner)) !== 'VISIBLE');
    check('password field has a clean accessible name',
      /textbox "Password"/.test(snap),
      (snap.match(/textbox "(Password[^"]*)"/) || [])[1]);

    /* 2. request an account */
    await tool('browser_click', {
      element: 'Request an account link',
      target: refFor(snap, 'Request an account'),
    });
    await tool('browser_wait_for', { text: 'Create your account' });
    check('lands on the request-account page', (await evaluate('() => location.pathname')) === '/signup');
    check('no stray error banner above the e-mail field (sign-up)',
      (await evaluate(noStrayBanner)) !== 'VISIBLE');

    /* 3. fill it in; the verdict must come from the server */
    snap = await tool('browser_snapshot', {});
    for (const [label, value] of [
      ['Full name', 'Ava Menon'],
      ['Work e-mail', 'ava.menon@alphagroup.in'],
      ['Password', 'Summer2026'],
      ['Confirm password', 'Summer2026'],
    ]) {
      await tool('browser_type', { element: label, target: refFor(snap, label), text: value, slowly: true });
    }
    await tool('browser_wait_for', { time: 1 });

    const verdict = fn => evaluate(`() => ({
      state: document.getElementById('policyInfoBtn').dataset.state,
      met: [...document.querySelectorAll('.checklist__item.is-met')].map(l => l.dataset.rule),
      unmet: [...document.querySelectorAll('.checklist__item.is-unmet')].map(l => l.dataset.rule),
      says: document.getElementById('policyPopMessage').textContent.trim(),
    })`);

    const weak = await verdict();
    check('weak password is rejected', weak.state === 'invalid' && weak.unmet.length > 0,
      `unmet: ${weak.unmet.join(', ')}`);

    snap = await tool('browser_snapshot', {});
    await tool('browser_evaluate', { function: "() => document.getElementById('password').select()" });
    await tool('browser_press_key', { key: 'Backspace' });
    await tool('browser_type', {
      element: 'Password', target: refFor(snap, 'Password'), text: 'Alpha#2026#Group', slowly: true,
    });
    await tool('browser_wait_for', { time: 1 });

    const strong = await verdict();
    check('strong password is accepted', strong.state === 'valid' && strong.unmet.length === 0,
      `${strong.met.length}/6 rules met`);
    await shot('03-signup.png');

    /* 4. the account area */
    await tool('browser_navigate', { url: `${BASE}/account` });
    const account = await evaluate(`() => ({
      heading: document.querySelector('.dash__hero h1').textContent.trim(),
      email: document.querySelector('.topbar__email').textContent.trim(),
      rows: [...document.querySelectorAll('.kv div')].map(d => d.querySelector('dt').textContent),
      chips: [...document.querySelectorAll('.policy-chips li')].length,
      timer: document.getElementById('sessionTimer').textContent.trim(),
    })`);
    check('account page greets the user', /^Welcome back, /.test(account.heading), account.heading);
    check('account shows the e-mail', account.email.includes('@'), account.email);
    check('account has name, e-mail, id and session rows',
      ['Name', 'E-mail', 'User ID', 'Provider', 'Session expires in'].every(r => account.rows.includes(r)),
      account.rows.join(', '));
    check('session countdown is live', /\d+[ms]|\d+m \d+s|\d+s/.test(account.timer), account.timer);
    check('policy chips rendered', account.chips === 6, `${account.chips} chips`);
    await shot('04-account.png', true);

    /* 5. nothing threw along the way */
    const consoleErrors = await tool('browser_console_messages', { level: 'error', all: true });
    check('no console errors', /Errors: 0/.test(consoleErrors) || /^$/.test(consoleErrors.trim()),
      consoleErrors.split('\n').pop());
  } finally {
    await tool('browser_close', {}).catch(() => {});
    child.kill();
  }

  if (failures.length) {
    console.log(`\n${failures.length} check(s) failed: ${failures.join(', ')}`);
    process.exit(1);
  }
  console.log('\nall checks passed');
}

main().catch(err => {
  console.error('smoke test aborted:', err.message);
  process.exit(1);
});
