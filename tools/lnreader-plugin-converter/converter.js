const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const https = require('https');
const path = require('path');
const { fileURLToPath } = require('url');

function isHttpUrl(value) {
  return value.startsWith('http://') || value.startsWith('https://');
}

function isFileUrl(value) {
  return value.startsWith('file://');
}

function sanitizeFileName(value) {
  return value.replace(/[^a-zA-Z0-9._-]/g, '_');
}

function readLocalFile(filePath) {
  return fs.readFileSync(filePath);
}

function readRemote(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('https://') ? https : http;
    client
      .get(url, (res) => {
        if (res.statusCode && res.statusCode >= 400) {
          reject(new Error(`Request failed: ${res.statusCode} ${res.statusMessage}`));
          res.resume();
          return;
        }
        const chunks = [];
        res.on('data', (chunk) => chunks.push(chunk));
        res.on('end', () => resolve(Buffer.concat(chunks)));
      })
      .on('error', reject);
  });
}

function resolveInputPath(inputPath, value) {
  if (path.isAbsolute(value)) return value;
  return path.resolve(path.dirname(inputPath), value);
}

async function readSource(inputPath, value) {
  if (isHttpUrl(value)) {
    return readRemote(value);
  }

  if (isFileUrl(value)) {
    return readLocalFile(fileURLToPath(value));
  }

  return readLocalFile(resolveInputPath(inputPath, value));
}

function sha256(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

function normalizeEntries(raw) {
  if (Array.isArray(raw)) return raw;
  if (raw && Array.isArray(raw.entries)) return raw.entries;
  if (raw && Array.isArray(raw.plugins)) return raw.plugins;
  return null;
}

async function convertEntry(inputPath, entry, downloadDir) {
  const required = ['id', 'name', 'site', 'lang', 'version', 'url'];
  for (const field of required) {
    if (entry[field] === undefined || entry[field] === null || entry[field] === '') {
      throw new Error(`Missing required field "${field}" for entry ${entry.id || '<unknown>'}`);
    }
  }

  const version = typeof entry.version === 'string' ? Number(entry.version) : entry.version;
  if (!Number.isInteger(version)) {
    throw new Error(`Invalid version for entry ${entry.id}: ${entry.version}`);
  }

  const payload = await readSource(inputPath, entry.url);
  const hash = sha256(payload);

  if (downloadDir) {
    fs.mkdirSync(downloadDir, { recursive: true });
    const baseName = sanitizeFileName(entry.id);
    fs.writeFileSync(path.join(downloadDir, `${baseName}.js`), payload);

    if (entry.customJS) {
      const customJs = await readSource(inputPath, entry.customJS);
      fs.writeFileSync(path.join(downloadDir, `${baseName}.custom.js`), customJs);
    }

    if (entry.customCSS) {
      const customCss = await readSource(inputPath, entry.customCSS);
      fs.writeFileSync(path.join(downloadDir, `${baseName}.custom.css`), customCss);
    }
  }

  const output = {
    id: entry.id,
    name: entry.name,
    site: entry.site,
    lang: entry.lang,
    version,
    url: entry.url,
    sha256: hash,
    hasSettings: Boolean(entry.hasSettings),
  };

  if (entry.iconUrl) output.iconUrl = entry.iconUrl;
  if (entry.customJS) output.customJS = entry.customJS;
  if (entry.customCSS) output.customCSS = entry.customCSS;

  return output;
}

async function convertRepo({ inputPath, outputPath, downloadDir, pretty = true }) {
  const raw = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
  const entries = normalizeEntries(raw);
  if (!entries) {
    throw new Error('Input JSON must be an array or an object with "entries" or "plugins".');
  }

  const outputEntries = [];
  for (const entry of entries) {
    outputEntries.push(await convertEntry(inputPath, entry, downloadDir));
  }

  const json = pretty ? JSON.stringify(outputEntries, null, 2) : JSON.stringify(outputEntries);
  fs.writeFileSync(outputPath, `${json}\n`);
}

module.exports = {
  convertRepo,
};
