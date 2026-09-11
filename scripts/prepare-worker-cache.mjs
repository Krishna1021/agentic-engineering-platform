#!/usr/bin/env node
// Copy only a trusted Gradle modules-2 cache, never properties or init scripts.
import { cp, lstat, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const source = process.argv[2];
if (!source || path.basename(path.resolve(source)) !== 'modules-2') throw new Error('Usage: node scripts/prepare-worker-cache.mjs /trusted/gradle-home/caches/modules-2');
if (!(await lstat(source)).isDirectory()) throw new Error('Cache source must be a directory, not a symlink.');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const target = path.join(root, 'build/worker-context/cache/modules-2');
await mkdir(path.dirname(target), { recursive: true });
await mkdir(target); // Refuse an existing context to avoid mixing stale dependencies.
await cp(source, target, { recursive: true, force: false, errorOnExist: true,
  filter: async entry => {
    const name = path.basename(entry);
    if (name.endsWith('.lock') || name === 'gc.properties') return false;
    if ((await lstat(entry)).isSymbolicLink()) throw new Error('Symlinks are not permitted in the prepared cache.');
    return true;
  }
});
console.log(`Prepared ${target}. Build worker/Dockerfile with build/worker-context.`);
