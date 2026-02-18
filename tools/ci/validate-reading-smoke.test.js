const test = require('node:test');
const assert = require('node:assert/strict');

const {
  validateReadingSmokeChecklist,
} = require('./validate-reading-smoke');

test('passes when both required checklist items are checked', () => {
  const prBody = `
## Reading Regression Gate (required for reading-critical changes)

- [x] I ran manual smoke checks for anime, manga, and novel viewing flows.
- [X] I verified there were no crashes, ANRs, or blank screens in those flows.
`;

  const result = validateReadingSmokeChecklist(prBody);
  assert.equal(result.ok, true);
  assert.deepEqual(result.missingItems, []);
});

test('fails when checklist section is missing', () => {
  const result = validateReadingSmokeChecklist('no checklist');
  assert.equal(result.ok, false);
  assert.equal(result.missingItems.length, 2);
});

test('fails when one checkbox is unchecked', () => {
  const prBody = `
- [x] I ran manual smoke checks for anime, manga, and novel viewing flows.
- [ ] I verified there were no crashes, ANRs, or blank screens in those flows.
`;

  const result = validateReadingSmokeChecklist(prBody);
  assert.equal(result.ok, false);
  assert.equal(result.missingItems.length, 1);
  assert.equal(
    result.missingItems[0],
    '- [x] I verified there were no crashes, ANRs, or blank screens in those flows.',
  );
});

