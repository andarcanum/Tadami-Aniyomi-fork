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
- `hasSettings` (boolean)

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

## Usage

```bash
node tools/lnreader-plugin-converter/index.js --input input.json --output repo.json
```

Optional: download plugin files locally

```bash
node tools/lnreader-plugin-converter/index.js --input input.json --output repo.json --download-dir ./downloads
```
