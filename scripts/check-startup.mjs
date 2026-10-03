// A temporary localhost probe of the existing server, never a service launcher.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, rm } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const bundle = resolve(process.argv[2] ?? 'dist/server.js');
await mkdir('.ημ', { recursive: true });
const scratch = await mkdtemp(resolve('.ημ/startup-'));
await mkdir(join(scratch, 'contracts'));
let output = '';
let closed = false;
let exit;
let spawnError;
const child = spawn(process.execPath, [bundle], {
  cwd: scratch,
  env: {
    SOL_HOST: '127.0.0.1',
    SOL_PORT: '0',
    CONTRACTS_DIR: join(scratch, 'contracts'),
    WORKSPACE_ROOT: scratch,
  },
  stdio: ['ignore', 'pipe', 'pipe'],
});
child.stdout.on('data', (chunk) => { output += chunk.toString(); });
child.stderr.on('data', (chunk) => { output += chunk.toString(); });
child.on('error', (error) => { spawnError = error; });
child.on('close', (code, signal) => { closed = true; exit = { code, signal }; });

/** Bound readiness and shutdown waits while reporting process creation errors. */
async function waitUntil(predicate, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (!predicate()) {
    if (spawnError) throw spawnError;
    if (Date.now() >= deadline) throw new Error('Sol startup/stop probe timed out');
    await delay(20);
  }
}

try {
  await waitUntil(() => closed || /http:\/\/127\.0\.0\.1:\d+/.test(output), 15_000);
  assert.equal(closed, false, `Server exited before listening: ${JSON.stringify(exit)}\n${output}`);
  const address = output.match(/http:\/\/127\.0\.0\.1:\d+/)[0];
  const response = await fetch(`${address}/health`, { signal: AbortSignal.timeout(3_000) });
  assert.equal(response.status, 200);
  const health = await response.json();
  assert.equal(health.status, 'ok');
  assert.equal(health.service, 'open-hax-sol-cljs');
  assert.equal(child.kill('SIGTERM'), true);
  await waitUntil(() => closed, 5_000);
  assert.deepEqual(exit, { code: 0, signal: null });
  console.log(JSON.stringify({ node: process.version, address, health, signal: 'SIGTERM', exit: 0 }));
} catch (error) {
  console.error(output);
  throw error;
} finally {
  try {
    if (!closed) {
      child.kill('SIGKILL');
      await waitUntil(() => closed, 5_000);
    }
  } finally {
    await rm(scratch, { recursive: true, force: true });
  }
}
