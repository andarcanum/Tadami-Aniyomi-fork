import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function createBookEngineFixture(options = {}) {
  const ktPath = path.resolve(__dirname, '../../main/java/eu/kanade/presentation/reader/novel/NovelBookEngineDocument.kt');
  const ktSource = fs.readFileSync(ktPath, 'utf8');
  const scriptMatch = ktSource.match(/<script>([\s\S]*?)<\/script>/);
  assert.ok(scriptMatch, 'Could not find <script> in NovelBookEngineDocument.kt');

  const scriptCode = scriptMatch[1].replace(/\$documentGeneration/g, '0');

  let currentTime = options.initialTime || 1000.0;
  let nextRafId = 1;
  const rafCallbacks = new Map();

  let currentScrollTop = options.initialScrollTop || 0;
  const viewport = {
    scrollHeight: options.scrollHeight || 100000,
    clientHeight: options.clientHeight || 1000,
    clientWidth: options.clientWidth || 800,
    addEventListener: () => {},
    removeEventListener: () => {},
    get scrollTop() {
      return currentScrollTop;
    },
    set scrollTop(v) {
      const max = Math.max(0, this.scrollHeight - this.clientHeight);
      const clamped = Math.max(0, Math.min(max, v));
      currentScrollTop = options.integerOnly ? Math.round(clamped) : clamped;
    },
  };

  const content = {
    children: [],
    childElementCount: 0,
    style: { setProperty: () => {} },
    querySelectorAll: () => [],
  };

  const dom = {
    'an-book-viewport': viewport,
    'an-book-content': content,
    'an-book-reader-css': { textContent: '' },
  };

  const ctx = {
    console: { log: () => {}, error: () => {} },
    document: {
      documentElement: {
        dataset: { anBookFlow: options.isPaginated ? 'paginated' : 'scrolled' },
      },
      getElementById: (id) => dom[id] || null,
      createElement: () => ({
        style: { setProperty: () => {} },
        appendChild: () => {},
        removeChild: () => {},
      }),
      body: { appendChild: () => {}, removeChild: () => {} },
      head: { appendChild: () => {} },
      addEventListener: () => {},
      createTreeWalker: () => ({ nextNode: () => null }),
    },
    window: {
      innerWidth: 800,
      innerHeight: 1000,
      addEventListener: () => {},
      requestAnimationFrame: (cb) => {
        const id = nextRafId++;
        rafCallbacks.set(id, cb);
        return id;
      },
      cancelAnimationFrame: (id) => {
        rafCallbacks.delete(id);
      },
    },
    performance: {
      now: () => currentTime,
    },
    Date: {
      now: () => currentTime,
    },
    NodeFilter: {
      SHOW_TEXT: 4,
    },
    Math,
    Number,
    Object,
    JSON,
  };

  ctx.window.window = ctx.window;
  ctx.window.document = ctx.document;
  ctx.window.performance = ctx.performance;
  ctx.window.Date = ctx.Date;
  ctx.requestAnimationFrame = ctx.window.requestAnimationFrame;
  ctx.cancelAnimationFrame = ctx.window.cancelAnimationFrame;

  vm.createContext(ctx);
  vm.runInContext(scriptCode, ctx);

  const engine = ctx.window.__anBookEngine;
  assert.ok(engine, '__anBookEngine must be exposed on window');

  return {
    engine,
    viewport,
    getCurrentTime: () => currentTime,
    stepFrame(dtMs) {
      currentTime += dtMs;
      const scheduled = Array.from(rafCallbacks.entries());
      rafCallbacks.clear();
      for (const [_, cb] of scheduled) {
        cb(currentTime);
      }
    },
    runDuration(durationSeconds, fps, syncCadenceMs = 250, rateCss = null, quantumCss = 1) {
      const dtMs = 1000 / fps;
      const totalSteps = Math.round((durationSeconds * 1000) / dtMs);
      let elapsedMs = 0;
      let lastSyncMs = 0;

      for (let i = 0; i < totalSteps; i++) {
        elapsedMs += dtMs;
        if (rateCss !== null && (elapsedMs - lastSyncMs >= syncCadenceMs)) {
          engine.setAutoScroll(rateCss, quantumCss);
          lastSyncMs = elapsedMs;
        }
        this.stepFrame(dtMs);
      }
    },
    hasActiveRaf: () => rafCallbacks.size > 0,
  };
}

test('clocked auto-scroll moves at accurate physical rate independent of frame rate (30, 60, 120 fps)', () => {
  // Speed 1: 31.25 physical px/s. In 10s: 312.5 physical px -> delivered 312 (+-1)
  for (const fps of [30, 60, 120]) {
    const fixture = createBookEngineFixture();
    const rateCss = 31.25;
    const quantumCss = 1.0;

    fixture.engine.setAutoScroll(rateCss, quantumCss);
    fixture.runDuration(10, fps, 250, rateCss, quantumCss);

    const moved = fixture.viewport.scrollTop;
    assert.ok(
      Math.abs(moved - 312.5) <= 1.0,
      `At ${fps} fps, expected ~312 px, got ${moved}`,
    );
  }
});

test('rates 1, 37, 100 deliver 312, 1335, 3125 +-1 delivered physical pixels across 10s', () => {
  // Speed 1: 31.25 px/s -> ~312 px in 10s
  // Speed 37: 133.522727 px/s -> ~1335 px in 10s
  // Speed 100: 312.5 px/s -> ~3125 px in 10s
  const cases = [
    { speed: 1, rate: 31.25, expected: 312.5 },
    { speed: 37, rate: 133.522727, expected: 1335.2 },
    { speed: 100, rate: 312.5, expected: 3125.0 },
  ];

  for (const c of cases) {
    for (const fps of [30, 60, 120]) {
      const fixture = createBookEngineFixture();
      fixture.engine.setAutoScroll(c.rate, 1.0);
      fixture.runDuration(10, fps, 250, c.rate, 1.0);

      const moved = fixture.viewport.scrollTop;
      assert.ok(
        Math.abs(moved - c.expected) <= 1.5,
        `Speed ${c.speed} at ${fps} fps: expected ~${c.expected}, got ${moved}`,
      );
    }
  }
});

test('display scales 1, 2, 3 preserve scale conversion and quantum tolerance', () => {
  // Speed 1 physical rate = 31.25 physical px/s -> 312.5 physical px in 10s
  for (const scale of [1, 2, 3]) {
    const fixture = createBookEngineFixture();
    const rateCss = 31.25 / scale;
    const quantumCss = 1.0 / scale;

    fixture.engine.setAutoScroll(rateCss, quantumCss);
    fixture.runDuration(10, 60, 250, rateCss, quantumCss);

    const physicalMoved = fixture.viewport.scrollTop * scale;
    assert.ok(
      Math.abs(physicalMoved - 312.5) <= 1.5,
      `Scale ${scale}: expected ~312.5 physical px, got ${physicalMoved}`,
    );
  }
});

test('pause, stop, and restart without stale jumps', () => {
  const fixture = createBookEngineFixture();
  const rateCss = 100.0;
  fixture.engine.setAutoScroll(rateCss, 1.0);

  // Run 1 second (100 px)
  fixture.runDuration(1, 60, 250, rateCss, 1.0);
  const after1s = fixture.viewport.scrollTop;
  assert.ok(Math.abs(after1s - 100.0) <= 1.0);

  // Stop auto scroll
  fixture.engine.setAutoScroll(0, 1.0);
  assert.equal(fixture.hasActiveRaf(), false);
  assert.equal(fixture.engine.autoScrollActive(), false);

  // Advance clock by 5 seconds while stopped
  fixture.stepFrame(5000);
  assert.equal(fixture.viewport.scrollTop, after1s);

  // Restart after pause: must NOT jump by the 5000ms pause!
  fixture.engine.setAutoScroll(rateCss, 1.0);
  assert.equal(fixture.engine.autoScrollActive(), true);

  // Run 1 more second: should move another ~100 px (total ~200 px)
  fixture.runDuration(1, 60, 250, rateCss, 1.0);
  const after2s = fixture.viewport.scrollTop;
  assert.ok(
    Math.abs(after2s - 200.0) <= 2.0,
    `Expected ~200 px after restart, got ${after2s}`,
  );
});

test('keepalive expires after 1000ms without sync', () => {
  const fixture = createBookEngineFixture();
  fixture.engine.setAutoScroll(100.0, 1.0);

  // Step 200ms without sync: still active
  fixture.stepFrame(200);
  assert.equal(fixture.engine.autoScrollActive(), true);

  // Step past 1000ms keepalive window without any new setAutoScroll calls
  fixture.stepFrame(1100);
  assert.equal(fixture.engine.autoScrollActive(), false);
  assert.equal(fixture.hasActiveRaf(), false);
});

test('frame gap cap limits delta time to 250ms max', () => {
  const fixture = createBookEngineFixture();
  const rateCss = 100.0;
  fixture.engine.setAutoScroll(rateCss, 1.0);

  // Initial step to initialize last frame time
  fixture.stepFrame(16);

  // Keep sync fresh
  fixture.engine.setAutoScroll(rateCss, 1.0);

  // Huge frame freeze of 1000ms
  fixture.stepFrame(1000);

  // With a 250ms cap, max movement for that frame should be (100 * 250 / 1000) = 25 px,
  // NOT 100 px!
  const moved = fixture.viewport.scrollTop;
  assert.ok(
    moved <= 26.0,
    `Frame cap expected <= 26 px, got ${moved}`,
  );
});

test('stops at document end and resumes when data appended', () => {
  const fixture = createBookEngineFixture({
    scrollHeight: 2000,
    clientHeight: 1000,
    initialScrollTop: 950,
  });
  // Maximum scrollable is 2000 - 1000 = 1000.
  const rateCss = 100.0;
  fixture.engine.setAutoScroll(rateCss, 1.0);

  // Step until end reached
  fixture.runDuration(2, 60, 250, rateCss, 1.0);

  assert.equal(fixture.viewport.scrollTop, 1000);
  assert.equal(fixture.engine.autoScrollActive(), false);
  assert.equal(fixture.engine.canScrollForward(), false);

  // Append data: scrollHeight increases to 3000
  fixture.viewport.scrollHeight = 3000;
  assert.equal(fixture.engine.canScrollForward(), true);

  // Resume auto-scroll
  fixture.engine.setAutoScroll(rateCss, 1.0);
  assert.equal(fixture.engine.autoScrollActive(), true);
  fixture.stepFrame(100);
  assert.ok(fixture.viewport.scrollTop > 1000);
});

test('manual scrollBy performs unrounded movement without enabling auto-scroll', () => {
  const fixture = createBookEngineFixture({
    scrollHeight: 5000,
    clientHeight: 1000,
    initialScrollTop: 100,
  });

  assert.equal(fixture.engine.autoScrollActive(), false);

  const consumed = fixture.engine.scrollBy(25.5);
  assert.equal(consumed, 25.5);
  assert.equal(fixture.viewport.scrollTop, 125.5);

  // Auto-scroll loop remains inactive: stepping frames does not continue advancing
  assert.equal(fixture.engine.autoScrollActive(), false);
  fixture.stepFrame(16);
  fixture.stepFrame(16);
  assert.equal(fixture.viewport.scrollTop, 125.5);
  assert.equal(fixture.engine.autoScrollActive(), false);
});

test('keepalive re-sync at 250ms cadence continues continuous movement without resetting carry', () => {
  const fixture = createBookEngineFixture();
  const rateCss = 60;

  // Start auto-scroll
  fixture.engine.setAutoScroll(rateCss, 1.0);
  assert.equal(fixture.engine.autoScrollActive(), true);

  // Step frames for 200ms
  for (let i = 0; i < 12; i++) {
    fixture.stepFrame(16.666);
  }
  const midScrollTop = fixture.viewport.scrollTop;
  assert.ok(midScrollTop > 0);

  // At 250ms mark, keepalive arrives with the same rate
  fixture.stepFrame(50);
  fixture.engine.setAutoScroll(rateCss, 1.0);

  // Loop remains actively running without carry reset
  assert.equal(fixture.engine.autoScrollActive(), true);
  const beforeStep = fixture.viewport.scrollTop;
  fixture.stepFrame(16.666);
  const delta = fixture.viewport.scrollTop - beforeStep;
  assert.ok(delta > 0);

  // Immediate stop
  fixture.engine.setAutoScroll(0, 1.0);
  assert.equal(fixture.engine.autoScrollActive(), false);
  assert.equal(fixture.hasActiveRaf(), false);
});
