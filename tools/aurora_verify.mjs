// aurora_verify.mjs — проверка, что цепочка решается ответами из сценария.
// Повторяет логику AuroraVault.tryOpen (Kotlin) один в один.
// Использование: node tools/aurora_verify.mjs scenario.json vault.json

import crypto from 'node:crypto';
import fs from 'node:fs';

const ITERATIONS = 120000;
const normalize = (s) =>
  s.normalize('NFC').toLowerCase().replaceAll('ё', 'е').trim().replace(/\s+/g, ' ');

function tryOpen(phrase, stage) {
  const salt = Buffer.from(stage.salt, 'base64');
  const key = crypto.pbkdf2Sync(Buffer.from(normalize(phrase), 'utf8'), salt, ITERATIONS, 32, 'sha256');
  const check = crypto.createHash('sha256').update(key).digest();
  if (!check.equals(Buffer.from(stage.check, 'base64'))) return null;
  const raw = Buffer.from(stage.data, 'base64');
  const ct = raw.subarray(0, raw.length - 16);
  const tag = raw.subarray(raw.length - 16);
  const d = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(stage.iv, 'base64'));
  d.setAuthTag(tag);
  return Buffer.concat([d.update(ct), d.final()]).toString('utf8');
}

const [, , scenarioPath, vaultPath] = process.argv;
const scenario = JSON.parse(fs.readFileSync(scenarioPath, 'utf8'));
const vault = JSON.parse(fs.readFileSync(vaultPath, 'utf8'));

let failed = false;
vault.stages.forEach((stage, i) => {
  const wrong = tryOpen('неправильный ответ', stage);
  const plain = tryOpen(scenario.stages[i].answer, stage);
  if (wrong !== null) { console.error(`FAIL stage ${i + 1}: открылась неверным ответом!`); failed = true; return; }
  if (plain === null) { console.error(`FAIL stage ${i + 1}: не открылась верным ответом`); failed = true; return; }
  const payload = JSON.parse(plain);
  console.log(`stage ${i + 1} OK (kind=${payload.kind}${payload.echoTitle ? ', ' + payload.echoTitle : ''})`);
});
process.exit(failed ? 1 : 0);
