// aurora_forge.mjs v2 — генератор секрета пасхалки «Сердце Авроры».
// Использование:
//   node tools/aurora_forge.mjs scenario.json out/
// Создаёт:
//   out/AuroraVaultData.kt — константы для приложения (БЕЗ секрета, можно коммитить)
//   out/vault.json        — те же данные в JSON (для aurora_verify.mjs)
//
// v2: валидация схемы сценария + детерминированный VERSION ваулта
// (для мягкой миграции прогресса в AuroraHeartManager).
//
// scenario.json ДЕРЖАТЬ ВНЕ РЕПОЗИТОРИЯ — в нём ответы открытым текстом!

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const ITERATIONS = 120000;

// Должно бит в бит совпадать с AuroraVault.normalize()
const normalize = (s) =>
  s.normalize('NFC').toLowerCase().replaceAll('ё', 'е').trim().replace(/\s+/g, ' ');

function fail(msg) {
  console.error('SCHEMA: ' + msg);
  process.exit(1);
}

function validate(scenario) {
  if (typeof scenario.firstRiddle !== 'string' || !scenario.firstRiddle.length) {
    fail('firstRiddle обязателен (строка)');
  }
  if (!Array.isArray(scenario.stages) || scenario.stages.length < 1) {
    fail('stages обязателен (массив >= 1)');
  }
  scenario.stages.forEach((s, i) => {
    const n = i + 1;
    if (typeof s.answer !== 'string') fail(`stage ${n}: answer обязателен`);
    const norm = normalize(s.answer);
    if (norm.length < 3 || norm.length > 64) {
      fail(`stage ${n}: нормализованный answer должен быть 3..64 символа (сейчас ${norm.length})`);
    }
    if (!s.payload || typeof s.payload.kind !== 'string') fail(`stage ${n}: payload.kind обязателен`);
    const isLast = i === scenario.stages.length - 1;
    if (isLast && s.payload.kind !== 'final') fail(`stage ${n}: последняя ступень должна быть kind=final`);
    if (!isLast && s.payload.kind !== 'riddle') fail(`stage ${n}: промежуточная ступень должна быть kind=riddle`);
    if (!isLast && (typeof s.payload.riddle !== 'string' || !s.payload.riddle.length)) {
      fail(`stage ${n}: у промежуточной ступени должна быть следующая загадка payload.riddle`);
    }
    if (isLast && (!s.payload.themeMaterial || s.payload.themeMaterial.style !== 'aurora-metal')) {
      console.warn(
        `[forge] stage ${n}: в payload финала нет themeMaterial (style="aurora-metal") — ` +
          'тема AURORA_PRIME останется статичной, без живого перелива (см. README_AURORA.md).',
      );
    }
  });
  const answers = scenario.stages.map((s) => normalize(s.answer));
  if (new Set(answers).size !== answers.length) fail('ответы ступеней не должны повторяться');
}

function forgeStage(answer, payload) {
  const salt = crypto.randomBytes(16);
  const iv = crypto.randomBytes(12);
  const key = crypto.pbkdf2Sync(Buffer.from(normalize(answer), 'utf8'), salt, ITERATIONS, 32, 'sha256');
  const check = crypto.createHash('sha256').update(key).digest();
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ct = Buffer.concat([
    cipher.update(JSON.stringify(payload), 'utf8'),
    cipher.final(),
    cipher.getAuthTag(), // Java AES/GCM ожидает тег в конце шифртекста
  ]);
  return {
    salt: salt.toString('base64'),
    iv: iv.toString('base64'),
    check: check.toString('base64'),
    data: ct.toString('base64'),
  };
}

const ktEscape = (s) =>
  s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$').replace(/\n/g, '\\n');

const [, , scenarioPath, outDir = '.'] = process.argv;
if (!scenarioPath) {
  console.error('usage: node aurora_forge.mjs <scenario.json> [outDir]');
  process.exit(1);
}
const scenario = JSON.parse(fs.readFileSync(scenarioPath, 'utf8'));
validate(scenario);
const stages = scenario.stages.map((s) => forgeStage(s.answer, s.payload));

// Детерминированная версия ваулта — из контрольных хешей ступеней.
// Меняется при ЛЮБОЙ перегенерации (соли случайны) — это намеренно.
const version = parseInt(
  crypto.createHash('sha256').update(stages.map((s) => s.check).join('')).digest('hex').slice(0, 7),
  16,
);

const stageBlocks = stages
  .map(
    (s) => `        AuroraStage(
            salt = "${s.salt}",
            iv = "${s.iv}",
            check = "${s.check}",
            data = "${s.data}",
        ),`,
  )
  .join('\n');

const kt = `package eu.kanade.domain.easteregg.aurora

// СГЕНЕРИРОВАНО tools/aurora_forge.mjs — не редактировать вручную.
// В этом файле НЕТ секрета: только соли, контрольные хеши ключей (PBKDF2,
// ${ITERATIONS} итераций) и AES-256-GCM-шифртекст. Ответы и награда в кодовой
// базе не существуют — их невозможно извлечь анализом кода.
object AuroraVaultData {

    // Версия ваулта для мягкой миграции прогресса (AuroraHeartManager).
    const val VERSION = ${version}

    const val FIRST_RIDDLE = "${ktEscape(scenario.firstRiddle)}"

    val STAGES = listOf(
${stageBlocks}
    )
}
`;

fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'AuroraVaultData.kt'), kt);
fs.writeFileSync(
  path.join(outDir, 'vault.json'),
  JSON.stringify({ version, firstRiddle: scenario.firstRiddle, stages }, null, 2),
);
console.log(`OK: ${stages.length} stages, vault version ${version} -> ${path.join(outDir, 'AuroraVaultData.kt')}`);
