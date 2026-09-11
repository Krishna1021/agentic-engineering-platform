#!/usr/bin/env node
// Node 18+. Standard library only; no automatic approvals or mutation retries.
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const directory = path.dirname(fileURLToPath(import.meta.url));
const catalog = JSON.parse(await readFile(path.join(directory, 'scenarios.json'), 'utf8'));
const [name, ...options] = process.argv.slice(2);
if (!catalog[name]) {
  console.log('Usage: node scripts/run-scenario.mjs greenfield|brownfield|ambiguous [--repository=name] [--preview] [--clarify=workflow-id:revision]');
  process.exit(name ? 1 : 0);
}
for (const option of options) {
  if (option !== '--preview' && !option.startsWith('--repository=') && !option.startsWith('--clarify=')) throw new Error(`Unknown option: ${option}`);
}
const scenario = catalog[name];
const repository = options.find(value => value.startsWith('--repository='))?.slice(13);
const clarify = options.find(value => value.startsWith('--clarify='))?.slice(10);
let route = '', body;
if (clarify) {
  if (name !== 'ambiguous' || repository) throw new Error('Clarify requires ambiguous and no repository option.');
  const match = /^([0-9a-f-]{36}):([1-9][0-9]*)$/i.exec(clarify);
  if (!match) throw new Error('Supply --clarify=workflow-id:current-revision');
  route = `/${match[1]}/clarifications`;
  body = { revision: Number(match[2]), answer: await readFile(path.join(directory, scenario.answer), 'utf8') };
} else {
  if (scenario.repositoryRequired && !repository) throw new Error('Supply --repository=relative-server-directory');
  if (!scenario.repositoryRequired && repository) throw new Error('Greenfield must omit repository.');
  if (repository && (repository.length > 200 || !/^[a-zA-Z0-9_-]+(?:\/[a-zA-Z0-9_-]+)*$/.test(repository))) throw new Error('Invalid relative repository name.');
  body = { requirements: await readFile(path.join(directory, scenario.requirements), 'utf8'), ...(repository ? { repository } : {}) };
}
const base = new URL(process.env.PLATFORM_URL || 'http://localhost:8080');
if (base.username || base.password || (base.protocol !== 'https:' && !(base.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)))) throw new Error('Use HTTPS outside localhost and no embedded credentials.');
if (options.includes('--preview')) {
  console.log(JSON.stringify({ method: 'POST', url: new URL(`/api/v1/workflows${route}`, base).href, body }, null, 2));
  process.exit(0);
}
if (!process.env.OPERATOR_PASSWORD) throw new Error('Set OPERATOR_PASSWORD to match the server.');
const headers = { 'Content-Type': 'application/json', Authorization: `Basic ${Buffer.from(`operator:${process.env.OPERATOR_PASSWORD}`).toString('base64')}` };
async function api(suffix, payload) {
  const response = await fetch(new URL(`/api/v1/workflows${suffix}`, base), {
    method: payload === undefined ? 'GET' : 'POST', headers, redirect: 'error',
    body: payload === undefined ? undefined : JSON.stringify(payload), signal: AbortSignal.timeout(30000)
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}; inspect current server state before retrying a mutation.`);
  return response.json();
}
let workflow = await api(route, body);
if (!/^[0-9a-f-]{36}$/i.test(workflow.id)) throw new Error('Invalid workflow ID returned by server.');
const output = path.resolve(directory, '../build/agent-runs', workflow.id);
await mkdir(output, { recursive: true });
console.log(`Workflow ${workflow.id}; evidence ${output}`);
const deadline = Date.now() + 1800000;
while (true) {
  if (!Number.isSafeInteger(workflow.revision) || workflow.revision < 1) throw new Error('Invalid revision returned by server.');
  const revisionDirectory = path.join(output, `revision-${workflow.revision}`);
  await mkdir(revisionDirectory, { recursive: true });
  await writeFile(path.join(revisionDirectory, 'workflow.json'), JSON.stringify(workflow, null, 2));
  if (!['QUEUED', 'RUNNING'].includes(workflow.status)) {
    await writeFile(path.join(revisionDirectory, 'summary.json'), JSON.stringify(await api(`/${workflow.id}/summary`), null, 2));
    const events = [];
    let after = 0;
    while (true) {
      const page = await api(`/${workflow.id}/events?after=${after}`);
      if (!page.length) break;
      events.push(...page);
      const next = page.at(-1).sequence;
      if (next <= after) throw new Error('Audit cursor did not advance.');
      after = next;
    }
    await writeFile(path.join(revisionDirectory, 'events.json'), JSON.stringify(events, null, 2));
    console.log(`Revision ${workflow.revision}: ${workflow.status}`);
    if (name === 'ambiguous' && !clarify && workflow.status !== 'AWAITING_CLARIFICATION') throw new Error('Required business clarification was not requested; retain this as failed scenario evidence.');
    if (['FAILED', 'SAFE_STOPPED'].includes(workflow.status)) process.exitCode = 1;
    console.log('Inspect artifacts and actual test reports. A review pause is not completed acceptance. This runner never approves.');
    break;
  }
  if (Date.now() >= deadline) throw new Error(`Polling timeout. Workflow ${workflow.id} may still run; use the GET endpoint to inspect it.`);
  await new Promise(resolve => setTimeout(resolve, 2000));
  workflow = await api(`/${workflow.id}`);
}
