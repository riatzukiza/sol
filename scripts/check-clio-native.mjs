// Test a compiled Sol/Clio ESM adapter with isolated files and no provider calls.
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const bundle = resolve(process.argv[2] ?? 'target/clio-native/probe.js');
const scratch = await mkdtemp(join(tmpdir(), 'sol-clio-native-'));
try {
  const { probe } = await import(pathToFileURL(bundle).href);
  const evidence = await probe(scratch);
  assert.match(evidence, /:status :passed/);
  assert.match(evidence, /:events 2/);
  assert.match(evidence, /:identical-retry true/);
  assert.match(evidence, /:reopen-replay true/);
  assert.match(evidence, /:stream-sequences \[1 2\]/);
  console.log(JSON.stringify({ node: process.version, evidence }));
} finally {
  await rm(scratch, { recursive: true, force: true });
}
