const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadIifeBundle(bundlePath, globalName) {
  const code = fs.readFileSync(bundlePath, 'utf8');
  const context = {};
  vm.createContext(context);
  vm.runInContext(code, context, { filename: path.basename(bundlePath) });
  const value = context[globalName];
  assert.ok(value, `${globalName} should be defined by ${bundlePath}`);
  return value.default || value;
}

test('dayjs bundle supports iso, custom formats, subtract, timestamp and null input', () => {
  const dayjs = loadIifeBundle(
    path.resolve(__dirname, '../../app/src/main/assets/js/dayjs.bundle.js'),
    '__dayjs',
  );

  const iso = dayjs('2026-02-11T08:30:00Z');
  assert.equal(iso.format('YYYY-MM-DD'), '2026-02-11');

  const custom = dayjs('02/10/2026 18:45', 'MM/DD/YYYY HH:mm');
  assert.equal(custom.format('YYYY-MM-DD HH:mm'), '2026-02-10 18:45');

  const shifted = dayjs('2026-02-11T00:00:00Z').subtract(2, 'day');
  assert.equal(shifted.format('YYYY-MM-DD'), '2026-02-09');

  const fromTimestamp = dayjs(1739184000000);
  assert.equal(fromTimestamp.isValid(), true);

  const nullInput = dayjs(null);
  assert.equal(nullInput.isValid(), false);
});

test('dayjs bundle supports localized long tokens like LL', () => {
  const dayjs = loadIifeBundle(
    path.resolve(__dirname, '../../app/src/main/assets/js/dayjs.bundle.js'),
    '__dayjs',
  );

  const formatted = dayjs('2026-02-11').format('LL');
  assert.notEqual(formatted, 'LL');
  assert.match(formatted, /2026/);
});

test('htmlparser2 bundle handles self-closing tags and nested nodes', () => {
  const htmlparser2 = loadIifeBundle(
    path.resolve(__dirname, '../../app/src/main/assets/js/htmlparser2.bundle.js'),
    '__htmlparser2',
  );

  const doc = htmlparser2.parseDocument('<div class="root"><img src="/a.png"/><br/><span><b>ok</b></span></div>');
  const root = htmlparser2.DomUtils.findOne((node) => node.name === 'div', doc.children, true);
  assert.ok(root);

  const imgs = htmlparser2.DomUtils.findAll((node) => node.name === 'img', [root]);
  const brs = htmlparser2.DomUtils.findAll((node) => node.name === 'br', [root]);
  const bold = htmlparser2.DomUtils.findOne((node) => node.name === 'b', [root], true);

  assert.equal(imgs.length, 1);
  assert.equal(brs.length, 1);
  assert.equal(htmlparser2.DomUtils.textContent(bold), 'ok');
});

test('htmlparser2 bundle exposes DomHandler parser flow and decoded attrs', () => {
  const htmlparser2 = loadIifeBundle(
    path.resolve(__dirname, '../../app/src/main/assets/js/htmlparser2.bundle.js'),
    '__htmlparser2',
  );

  const handler = new htmlparser2.DomHandler();
  const parser = new htmlparser2.Parser(handler, { decodeEntities: true });
  parser.write('<p data-title="a &amp; b">hello</p>');
  parser.end();

  const paragraph = htmlparser2.DomUtils.findOne((node) => node.name === 'p', handler.dom, true);
  assert.ok(paragraph);
  assert.equal(paragraph.attribs['data-title'], 'a & b');
  assert.equal(htmlparser2.DomUtils.textContent(paragraph), 'hello');
});
