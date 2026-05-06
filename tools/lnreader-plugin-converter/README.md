# LNReader Plugin Converter (Novel)

Generates a repo JSON index for Tadami novel plugins and computes `sha256` for each plugin script.

## Input format

Accepts either:
- A JSON array of entries, or
- An object with `entries` or `plugins` array.

Each entry requires:
- `id`
- `name`
- `site`
- `lang`
- `version` (number or numeric string)
- `url` (http/https/file/local path)

Optional fields:
- `iconUrl`
- `customJS`
- `customCSS`
- `hasSettings` (boolean metadata hint)

Example:
```json
[
  {
    "id": "example.plugin",
    "name": "Example",
    "site": "https://example.com",
    "lang": "en",
    "version": 1,
    "url": "https://example.com/plugin.js",
    "iconUrl": "https://example.com/icon.png",
    "customJS": "https://example.com/custom.js",
    "customCSS": "https://example.com/custom.css",
    "hasSettings": true
  }
]
```

Notes:

- `hasSettings` is a repository metadata hint used for faster UI decisions.
- Installed novel plugins can still expose settings at runtime through LNReader-style `pluginSettings` even when `hasSettings` is missing or `false`.
- Tadami's novel runtime keeps backward compatibility with legacy array-style `settings` definitions as well.

## Usage

```bash
node tools/lnreader-plugin-converter/index.js --input input.json --output repo.json
```

Optional: download plugin files locally

```bash
node tools/lnreader-plugin-converter/index.js --input input.json --output repo.json --download-dir ./downloads
```
