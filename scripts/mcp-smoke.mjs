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
const refForText = (snapshot, fragment) => {
  for (const line of snapshot.split('\n')) {
    if (!line.includes(fragment)) continue;
    const m = line.match(/\[ref=([a-zA-Z0-9]+)\]/);
    if (m) return m[1];
  }
  throw new Error(`no control containing "${fragment}" in the accessibility tree`);
};

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

    /* 2b. the mobile number: a short dial-code button, every country offered */
    snap = await tool('browser_snapshot', {});
    const picker = await evaluate(`() => {
      const select = document.getElementById('country_code');
      const button = document.querySelector('.picker__button');
      const option = select.options[select.selectedIndex];
      const control = document.getElementById('phone').closest('.control');
      return {
        count: select.options.length,
        iso: option.value,
        label: option.textContent.trim(),
        caption: button ? button.textContent.trim() : '(no button)',
        name: button ? button.getAttribute('aria-label') : '',
        buttonW: button ? Math.round(button.getBoundingClientRect().width) : 0,
        controlW: Math.round(control.getBoundingClientRect().width),
        hint: document.getElementById('phoneHint').textContent.trim(),
      };
    }`);
    check('country picker lists every country', picker.count >= 230, `${picker.count} options`);
    check('India is preselected with its dial code',
      picker.iso === 'IN' && picker.label.includes('+91'), `${picker.iso} ${picker.label}`);
    check('the closed picker shows only the dial code', picker.caption === '+91', picker.caption);
    check('the picker leaves most of the field to the number',
      picker.buttonW / picker.controlW < 0.25, `${picker.buttonW}px of ${picker.controlW}px`);
    check('picker has an accessible name', picker.name === 'Country dialling code', picker.name);
    check('hint states the digits India uses', /10 digits/.test(picker.hint), picker.hint);
    check('mobile field has a clean accessible name',
      /textbox "Mobile number"/.test(snap),
      (snap.match(/textbox "(Mobile[^"]*|\+91[^"]*)"/) || [])[1]);

    snap = await tool('browser_snapshot', {});
    await tool('browser_click', {
      element: 'Country dialling code', target: refFor(snap, 'Country dialling code'),
    });
    await tool('browser_wait_for', { time: 0.5 });
    const opened = await evaluate(`() => ({
      open: !document.querySelector('.picker__panel').hidden,
      options: [...document.querySelectorAll('.picker__option')].filter(li => !li.hidden).length,
      focus: document.activeElement.className,
    })`);
    check('the dial-code button opens the full country list',
      opened.open && opened.options >= 230, `${opened.options} options`);
    check('opening the list puts the cursor in its search box',
      /picker__search/.test(opened.focus), opened.focus);

    snap = await tool('browser_snapshot', {});
    await tool('browser_type', {
      element: 'Search country or dialling code',
      target: refFor(snap, 'Search country or dialling code'),
      text: 'united king',
      slowly: true,
    });
    await tool('browser_wait_for', { time: 0.4 });
    const filtered = await evaluate(`() => {
      const shown = [...document.querySelectorAll('.picker__option')]
        .filter(li => li.getBoundingClientRect().height > 0);
      return { count: shown.length, first: shown[0] ? shown[0].textContent.trim() : '' };
    }`);
    check('typing in the picker narrows the list',
      filtered.count === 1 && /United Kingdom/.test(filtered.first),
      `${filtered.count} match: ${filtered.first}`);

    snap = await tool('browser_snapshot', {});
    await tool('browser_click', {
      element: 'United Kingdom', target: refForText(snap, 'United Kingdom'),
    });
    await tool('browser_wait_for', { time: 0.4 });

    const abroad = await evaluate(`() => ({
      caption: document.querySelector('.picker__button').textContent.trim(),
      value: document.getElementById('country_code').value,
      hint: document.getElementById('phoneHint').textContent.trim(),
      closed: document.querySelector('.picker__panel').hidden,
    })`);
    check('choosing a country updates the button and the hint',
      abroad.caption === '+44' && abroad.value === 'GB'
        && /United Kingdom/.test(abroad.hint) && /10 digits/.test(abroad.hint),
      `${abroad.caption} / ${abroad.hint}`);
    check('the list closes after choosing', abroad.closed === true, String(abroad.closed));

    const phoneState = () => evaluate(`() => ({
      typed: document.getElementById('phone').value,
      error: document.getElementById('phoneError').textContent.trim(),
      hint: document.getElementById('phoneHint').textContent.trim(),
      invalid: document.getElementById('phone').closest('[data-field]').classList.contains('is-invalid'),
    })`);

    const setPhone = async text => {
      await tool('browser_evaluate', {
        function: "() => { const p = document.getElementById('phone'); p.value = ''; p.focus(); }",
      });
      await tool('browser_type', {
        element: 'Mobile number', target: refFor(snap, 'Mobile number'), text, slowly: true,
      });
      await tool('browser_wait_for', { time: 0.5 });
      return phoneState();
    };

    const short = await setPhone('9876543');
    check('a too-short number is flagged while typing',
      short.invalid && short.error.length > 0, short.error || 'no message');
    await shot('05-mobile-too-short.png');

    const junk = await setPhone('98abc7654321');
    check('letters cannot be typed into the field', !/[a-z]/i.test(junk.typed), junk.typed);

    const full = await setPhone('9876543210');
    check('a 10-digit Indian number clears the warning',
      !full.invalid && full.error === '', full.error || 'clean');

    // Back to India through the same UI, not by poking the select.
    snap = await tool('browser_snapshot', {});
    await tool('browser_click', {
      element: 'Country dialling code', target: refFor(snap, 'Country dialling code'),
    });
    await tool('browser_wait_for', { time: 0.3 });
    await tool('browser_type', {
      element: 'Search country or dialling code',
      target: refFor(await tool('browser_snapshot', {}), 'Search country or dialling code'),
      text: 'india',
      slowly: true,
    });
    await tool('browser_wait_for', { time: 0.4 });

    // A prefix match has to beat a country that merely contains the word:
    // "india" also matches British Indian Ocean Territory.
    const ranked = await evaluate(`() => {
      const shown = [...document.querySelectorAll('.picker__option')]
        .filter(li => li.getBoundingClientRect().height > 0);
      return { count: shown.length, first: shown[0] ? shown[0].textContent.trim() : '' };
    }`);
    check('a country whose name starts with the query is ranked first',
      ranked.count > 1 && /\+91 India$/.test(ranked.first),
      `${ranked.count} matches, first: ${ranked.first}`);

    await tool('browser_press_key', { key: 'Enter' });
    await tool('browser_wait_for', { time: 0.4 });
    check('India can be picked again with the keyboard',
      (await evaluate("() => document.getElementById('country_code').value")) === 'IN',
      await evaluate("() => document.getElementById('country_code').value"));

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

    /* 3b. what the browser is told when the server answers */
    const unique = `ava.${Date.now()}@alphagroup.in`;

    const fillForm = async (email, phone = '9876543210') => {
      await tool('browser_evaluate', {
        function: `() => {
          const set = (id, value) => {
            const el = document.getElementById(id);
            el.value = value;
            el.dispatchEvent(new Event('input', { bubbles: true }));
          };
          set('name', 'Ava Menon');
          set('email', ${JSON.stringify(email)});
          set('phone', ${JSON.stringify(phone)});
          set('password', 'Alpha#2026#Group');
          set('confirm_password', 'Alpha#2026#Group');
        }`,
      });
    };

    /* Click, then watch the page itself for the banner the server earns us.
       Polling inside one evaluate beats MCP round-trips: the banner only
       lives for a second or so before the redirect. */
    const submitAndRead = async () => {
      const shotId = await tool('browser_snapshot', {});
      await tool('browser_click', {
        element: 'Create account', target: refForText(shotId, 'Create account'),
      });
      return evaluate(`() => new Promise(resolve => {
        const started = Date.now();
        const read = () => {
          const error = document.querySelector('[data-flash]');
          const success = document.querySelector('[data-success]');
          const link = document.querySelector('[data-flash-link]');
          const email = document.getElementById('email');
          const state = {
            errorText: error && !error.hidden ? error.textContent.trim() : '',
            successText: success && !success.hidden ? success.textContent.trim() : '',
            link: link && !link.hidden ? link.textContent.trim() : '',
            emailFlagged: email ? email.closest('[data-field]').classList.contains('is-invalid') : false,
            path: location.pathname + location.search,
          };
          if (state.errorText || state.successText) return resolve(state);
          if (Date.now() - started > 10000) return resolve({ ...state, timedOut: true });
          setTimeout(read, 50);
        };
        read();
      })`);
    };

    const waitForPath = async (want, tries = 30) => {
      for (let i = 0; i < tries; i++) {
        const at = await evaluate('() => location.pathname');
        if (at === want) return at;
        await tool('browser_wait_for', { time: 0.3 });
      }
      return evaluate('() => location.pathname');
    };

    /* success */
    await fillForm(unique);
    const registered = await submitAndRead();
    check('a successful registration says so',
      /Account created/i.test(registered.successText), registered.successText || '(no banner)');
    await shot('10-registered.png');

    const landedPath = await waitForPath('/dashboard');
    const landed = await evaluate(`() => ({
      path: location.pathname,
      search: location.search,
      notice: (document.querySelector('.dash__notice') || {}).textContent || '',
    })`);
    check('the new account lands on the dashboard with a welcome notice',
      landedPath === '/dashboard' && /welcome=1/.test(landed.search)
        && /account is ready/i.test(landed.notice),
      `${landed.path}${landed.search} — ${landed.notice.trim() || 'no notice'}`);
    await shot('11-account-welcome.png', true);

    /* duplicate e-mail: the server answers 409 and the browser explains it */
    await tool('browser_navigate', { url: `${BASE}/signup` });
    await fillForm(unique);
    const duplicate = await submitAndRead();
    check('a duplicate e-mail is explained, with a way out',
      /already registered/i.test(duplicate.errorText)
        && /sign in/i.test(duplicate.link)
        && duplicate.emailFlagged,
      `${duplicate.errorText || '(no banner)'} [link: ${duplicate.link || 'none'}]`);
    await shot('12-signup-duplicate.png');

    /* the network drops */
    await tool('browser_navigate', { url: `${BASE}/signup` });
    await fillForm(`net.${Date.now()}@alphagroup.in`);
    await tool('browser_evaluate', {
      function: "() => { window.__fetch = window.fetch; window.fetch = () => Promise.reject(new TypeError('Failed to fetch')); }",
    });
    const offline = await submitAndRead();
    check('a dead connection is reported as such',
      /can't reach the server/i.test(offline.errorText), offline.errorText || '(no banner)');
    await shot('13-signup-network.png');

    /* the server falls over */
    await tool('browser_evaluate', {
      function: "() => { window.fetch = () => Promise.resolve(new Response('', { status: 500 })); }",
    });
    const broke = await submitAndRead();
    check('a server failure says the failure is ours',
      /failed on our side/i.test(broke.errorText), broke.errorText || '(no banner)');
    await shot('14-signup-server-error.png');

    await tool('browser_evaluate', {
      function: "() => { if (window.__fetch) window.fetch = window.__fetch; }",
    });

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
    // A handled 4xx still lands in the browser's resource log; those are
    // expected here (the duplicate-account test asks for one). JS errors are not.
    const scriptErrors = consoleErrors
      .split('\n')
      .filter(line => /\[ERROR\]/.test(line) && !/Failed to load resource/.test(line));
    check('no JavaScript errors in the console', scriptErrors.length === 0,
      scriptErrors[0] || 'clean');
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
