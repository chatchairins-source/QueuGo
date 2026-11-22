import { createReadStream } from 'node:fs';
import { createInterface } from 'node:readline';
import { criticalBackupTables } from './backup-inventory-contract.mjs';

// pg_dump --use-copy emits one physical line per text COPY row, escaping
// embedded tabs/newlines/backslashes. Never print row contents or auth data.
const dumpPath = process.argv[2];
if (!dumpPath) throw new Error('Usage: inventory-from-copy-dump <data.sql>');
const counts = Object.fromEntries(criticalBackupTables.map(key => [key, 0]));
const seen = new Set();
const criticalSeen = new Set();
const identifier = '(?:"(?:[^"\n]|"")*"|[A-Za-z_][A-Za-z_0-9$]*)';
const copy = new RegExp('^COPY (' + identifier + ')\\.(' + identifier + ') \\(.*\\) FROM stdin;$');
const unquote = value => value.startsWith('"') ? value.slice(1, -1).replaceAll('""', '"') : value;
let table = null;
let rows = 0;
for await (const line of createInterface({ input: createReadStream(dumpPath), crlfDelay: Infinity })) {
  if (table !== null) {
    if (line === '\\.') {
      if (Object.hasOwn(counts, table)) counts[table] = rows;
      table = null;
    } else {
      rows++;
      if (!Number.isSafeInteger(rows)) throw new Error('COPY row count exceeds safe integer');
    }
    continue;
  }
  if (!line.startsWith('COPY ')) continue;
  const match = copy.exec(line);
  if (!match) throw new Error('Unsupported COPY header in backup dump');
  const parts = [unquote(match[1]), unquote(match[2])];
  const identity = JSON.stringify(parts);
  table = parts.join('.');
  if (seen.has(identity)) throw new Error('Duplicate COPY table in backup dump');
  seen.add(identity);
  if (Object.hasOwn(counts, table)) criticalSeen.add(table);
  rows = 0;
}
if (table !== null) throw new Error('Unterminated COPY section in backup dump');
if (criticalBackupTables.some(key => !criticalSeen.has(key))) throw new Error('Backup dump missing required COPY table');
console.log(JSON.stringify({ ...counts, inventory_basis: 'pg_dump_text_copy_v1' }));
