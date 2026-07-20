# Инструменты «Сердца Авроры»

## aurora_forge.mjs (v2)

Генерирует `AuroraVaultData.kt` (можно коммитить) и `vault.json` (для проверки, НЕ коммитить) из сценария с ответами.

```bash
node tools/aurora_forge.mjs scenario.json app/src/main/java/eu/kanade/domain/easteregg/aurora
node tools/aurora_verify.mjs scenario.json app/src/main/java/eu/kanade/domain/easteregg/aurora/vault.json
rm app/src/main/java/eu/kanade/domain/easteregg/aurora/vault.json
```

### Схема scenario.json

```jsonc
{
  "firstRiddle": "текст первой загадки",
  "stages": [
    { "answer": "ответ1", "payload": { "kind": "riddle", "echoTitle": "…", "riddle": "следующая загадка" } },
    {
      "answer": "ответN",
      "payload": {
        "kind": "final", "title": "…", "holder": "…", "letter": "…",
        "themeName": "…", "themeColors": { },
        "themeMaterial": { "style": "aurora-metal", "base": "#0A1626", "sheen": "#EAF6FF", "iridescence": "0.45", "gloss": "0.8" },
        "bonusPoints": 0
      }
    }
  ]
}
```

### Валидация (v2)

- `firstRiddle` — обязательная непустая строка;
- каждый `answer` после нормализации — 3..64 символа, без дублей;
- промежуточные ступени — `kind: riddle` с непустой `riddle`; последняя — `kind: final`;
- у финала настоятельно рекомендуется `payload.themeMaterial` (иначе форж выдаст предупреждение, а тема AURORA_PRIME будет статичной).

### Живая тема (`themeMaterial`)

Все значения — строки:

- `style` — обязательно `"aurora-metal"` (иначе материал игнорируется клиентом);
- `base` — hex-цвет «металла» (по умолчанию берётся `themeColors.surface`);
- `sheen` — hex-цвет блика (по умолчанию `#EAF6FF`);
- `iridescence`, `gloss` — числа `"0".."1"` (по умолчанию `0.4` / `0.8`).

При наличии этих данных клиент (v3.1) оживляет наградную тему: акцентные
цвета бликуют от наклона устройства и «дышат» (`AuroraPrimeColors.kt`),
а hero-поверхности рендерятся шейдерным «живым металлом»
(`Modifier.auroraMetal`, `AuroraLivingMaterial.kt`). Ранее разблокированный
payload без `themeMaterial` считается устаревшим и мягко мигрируется
(`AuroraHeartManager.migrateIfNeeded`).

### Версия ваулта

Форж пишет в `AuroraVaultData.kt` константу `VERSION` (детерминированно из контрольных хешей ступеней). `AuroraHeartManager` сравнивает её с сохранённой и при несовпадении мягко сбрасывает прогресс — чистить данные приложения после перегенерации больше не нужно.

### Безопасность

- `scenario*.json` и `vault.json` — НИКОГДА не коммитить (см. .gitignore);
- ответы и награда существуют только в виде PBKDF2-хешей и AES-GCM-шифртекста;
- не трогать `normalize()` и `ITERATIONS`: они должны бит в бит совпадать с AuroraVault.kt;
- юнит-тест контракта: `AuroraNormalizeTest` (без ответов в коде).
