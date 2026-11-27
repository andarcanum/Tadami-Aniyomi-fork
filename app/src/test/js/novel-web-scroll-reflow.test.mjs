import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const bridgeSource = fs.readFileSync(
  new URL('../../main/java/eu/kanade/presentation/reader/novel/NovelReaderWebViewBridge.kt', import.meta.url),
  'utf8',
);
const cssMethod = bridgeSource.slice(bridgeSource.indexOf('internal fun WebView.applyReaderCss('));
const cssScript = cssMethod.match(/evaluateJavascript\(\s*"""([\s\S]*?)"""\.trimIndent\(\)/)?.[1];
assert.ok(cssScript, 'The real applyReaderCss evaluateJavascript body must be available');
const cancelMethod = bridgeSource.slice(
  bridgeSource.indexOf('internal fun WebView.cancelReaderScrollRestore('),
  bridgeSource.indexOf('internal fun WebView.restoreWebViewScroll('),
);
// Older code only cancels the native request; execute its (absent) JS as a no-op
// so the lifecycle regression fails on scroll behavior, not on a missing symbol.
const cancelScript = cancelMethod.match(/evaluateJavascript\(\s*"""([\s\S]*?)"""\.trimIndent\(\)/)?.[1] || '';

class EventTargetFixture {
  listeners = new Map();

  addEventListener(type, listener) {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set());
    this.listeners.get(type).add(listener);
  }

  removeEventListener(type, listener) {
    this.listeners.get(type)?.delete(listener);
  }

  dispatchEvent(event) {
    for (const listener of [...(this.listeners.get(event.type) || [])]) listener(event);
  }
}

class ElementFixture extends EventTargetFixture {
  constructor(tagName, textContent = '') {
    super();
    this.tagName = tagName.toUpperCase();
    this.textContent = textContent;
    this.attributes = new Map();
    this.children = [];
    const properties = new Map();
    this.style = {
      setProperty: (name, value, priority) => properties.set(name, { value, priority }),
      getPropertyValue: (name) => properties.get(name)?.value || '',
      removeProperty: (name) => properties.delete(name),
    };
  }

  removeAttribute(name) { this.attributes.delete(name); }
  getAttribute(name) { return this.attributes.get(name) ?? null; }
  setAttribute(name, value) { this.attributes.set(name, value); }
  querySelectorAll() { return this.children; }
  querySelector() { return null; }
}

function createChapterFixture(options = {}) {
  const frames = new Map();
  let nextFrameId = 1;
  let scrollY = options.scrollY ?? 500;
  let scrollHeight = options.scrollHeight ?? 1800;
  let scrollEventQueued = false;
  let cssLayoutHeight = null;
  const scrollWrites = [];
  const paragraph = new ElementFixture('p', 'Reader text');
  paragraph.setAttribute('align', 'right');
  paragraph.setAttribute('bgcolor', '#000000');
  paragraph.setAttribute('color', '#ffffff');
  const body = new ElementFixture('body');
  body.children = [paragraph];
  const root = new ElementFixture('html');
  Object.defineProperty(root, 'scrollHeight', { get: () => scrollHeight });
  const styles = new Map();
  const document = new EventTargetFixture();
  Object.assign(document, {
    documentElement: root,
    body,
    head: { appendChild: (style) => styles.set(style.id, style) },
    getElementById: (id) => styles.get(id) || null,
    createElement: (tag) => {
      const element = new ElementFixture(tag);
      if (tag === 'style') {
        let textContent = '';
        Object.defineProperty(element, 'textContent', {
          get: () => textContent,
          set: (value) => {
            textContent = value;
            if (cssLayoutHeight !== null) {
              scrollHeight = cssLayoutHeight;
              cssLayoutHeight = null;
              moveScroll(scrollY);
            }
          },
        });
      }
      return element;
    },
  });
  const window = new EventTargetFixture();
  Object.assign(window, {
    document,
    innerHeight: options.innerHeight ?? 800,
    innerWidth: options.innerWidth ?? 400,
    requestAnimationFrame: (callback) => {
      const id = nextFrameId++;
      frames.set(id, callback);
      return id;
    },
    cancelAnimationFrame: (id) => frames.delete(id),
    scrollTo: (_x, y) => {
      scrollWrites.push(y);
      moveScroll(y);
    },
  });
  Object.defineProperty(window, 'scrollY', { get: () => scrollY });
  window.window = window;
  function moveScroll(y) {
    const next = Math.max(0, Math.min(Math.max(0, scrollHeight - window.innerHeight), y));
    if (next !== scrollY) scrollEventQueued = true;
    scrollY = next;
  }
  const context = vm.createContext({
    window, document, HTMLElement: ElementFixture,
    requestAnimationFrame: window.requestAnimationFrame,
    cancelAnimationFrame: window.cancelAnimationFrame,
  });
  return {
    window, document, paragraph, styles, scrollWrites,
    applyCss({ preserve = true, css = 'body { color: red; }', height = null, forceFont = true, forceAlign = true, forceIndent = true } = {}) {
      cssLayoutHeight = height;
      const values = {
        preserveScrollFlag: String(preserve),
        WEB_READER_STYLE_ELEMENT_ID: '__an_reader_style__',
        quotedCss: JSON.stringify(css),
        fontFlag: String(forceFont),
        alignFlag: String(forceAlign),
        firstLineIndentFlag: String(forceIndent),
      };
      const script = cssScript.replace(/\$([A-Za-z_][A-Za-z_0-9]*)/g, (_match, key) => {
        assert.ok(Object.hasOwn(values, key), `Unknown Kotlin script interpolation: ${key}`);
        return values[key];
      });
      vm.runInContext(script, context);
    },
    scrollTo(y) { window.scrollTo(0, y); },
    setLayout({ height = scrollHeight, viewportHeight = window.innerHeight, viewportWidth = window.innerWidth } = {}) {
      scrollHeight = height;
      window.innerHeight = viewportHeight;
      window.innerWidth = viewportWidth;
      moveScroll(scrollY); // Browser clamping queues a real scroll event too.
      window.dispatchEvent({ type: 'resize', target: window });
    },
    cancelReaderScrollRestore() { vm.runInContext(cancelScript, context); },
    replaceDocument({ rootOnly = false } = {}) {
      if (rootOnly) {
        const nextRoot = new ElementFixture('html');
        Object.defineProperty(nextRoot, 'scrollHeight', { get: () => scrollHeight });
        document.documentElement = nextRoot;
      } else {
        document.body = new ElementFixture('body');
      }
    },
    pageHide() { window.dispatchEvent({ type: 'pagehide', target: window }); },
    activeListenerCount() {
      return [...window.listeners.values()].reduce((total, listeners) => total + listeners.size, 0);
    },
    input(type) {
      let prevented = false;
      window.dispatchEvent({ type, target: document, preventDefault: () => { prevented = true; } });
      assert.equal(prevented, false, 'Reflow guards must not block reader input');
    },
    flushScrollEvents() {
      if (!scrollEventQueued) return;
      scrollEventQueued = false;
      const event = { type: 'scroll', target: document };
      document.dispatchEvent(event);
      window.dispatchEvent(event);
    },
    stepFrame({ flushScroll = true } = {}) {
      if (flushScroll) this.flushScrollEvents();
      const scheduled = [...frames];
      for (const [id, callback] of scheduled) {
        if (!frames.delete(id)) continue;
        callback(0);
      }
    },
    flushFrames(options) {
      for (let remaining = 12; frames.size && remaining > 0; remaining--) this.stepFrame(options);
      assert.equal(frames.size, 0, 'CSS restoration must finish in a bounded number of frames');
    },
  };
}

test('CSS delayed restoration cannot overwrite a later user or API scroll', () => {
  for (const { beforeFirstFrame, flushScroll } of [
    { beforeFirstFrame: true, flushScroll: true },
    { beforeFirstFrame: false, flushScroll: true },
    { beforeFirstFrame: true, flushScroll: false },
    { beforeFirstFrame: false, flushScroll: false },
  ]) {
    const fixture = createChapterFixture();
    fixture.applyCss();
    assert.equal(fixture.window.scrollY, 500);
    if (!beforeFirstFrame) fixture.stepFrame();
    fixture.scrollTo(900);
    fixture.flushFrames({ flushScroll });
    assert.equal(fixture.window.scrollY, 900, 'Later navigation wins over the captured CSS fraction');
    assert.equal(fixture.activeListenerCount(), 0);
  }
});

test('overlapping CSS and resize retain the active fraction instead of a clamped transient', () => {
  const fixture = createChapterFixture();
  fixture.applyCss({ height: 2800 }); // 50% of 2000 = 1000.
  assert.equal(fixture.window.scrollY, 1000);
  fixture.setLayout({ height: 1000, viewportHeight: 400, viewportWidth: 800 });
  assert.equal(fixture.window.scrollY, 600); // Geometry clamp, not reader navigation.
  fixture.flushScrollEvents();
  fixture.applyCss({ height: 2400 }); // The same 50% of 2000 = 1000, not 100%.
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 1000);
});

test('queued self-scroll and resize clamps cannot cancel the active fraction', () => {
  const fixture = createChapterFixture();
  fixture.applyCss({ height: 2800 });
  assert.equal(fixture.window.scrollY, 1000);
  fixture.setLayout({ height: 1000, viewportHeight: 400 });
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 300, '50% of the final 600px scroll range is retained');
});

test('rapid resize back to the old range retains intent after a clamp or native restore', () => {
  for (const nativeRestore of [false, true]) {
    const fixture = createChapterFixture();
    fixture.applyCss({ height: 2800 }); // 50% of 2000 = 1000.
    fixture.setLayout({ height: 1000, viewportHeight: 400 }); // Clamp to 600.
    if (nativeRestore) fixture.scrollTo(300); // Native rotation restore at 50%.
    fixture.flushScrollEvents();
    fixture.stepFrame();
    fixture.setLayout({ height: 2400 }); // Return to the old 2000px range.
    fixture.flushFrames();
    assert.equal(fixture.window.scrollY, 1000, nativeRestore ? 'native restore' : 'resize clamp');
  }
});

test('resize events retain intent when scroll events coalesce until after resize back', () => {
  const fixture = createChapterFixture();
  fixture.applyCss({ height: 2800 });
  fixture.setLayout({ height: 1000, viewportHeight: 400 }); // Clamp is observable at resize.
  fixture.setLayout({ height: 2400 }); // Scroll events have not been dispatched yet.
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 1000);
});

test('native lifecycle cancellation invalidates queued CSS callbacks before replacement', () => {
  const fixture = createChapterFixture();
  fixture.applyCss();
  fixture.cancelReaderScrollRestore();
  fixture.setLayout({ height: 2800 });
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 500, 'Released document must not apply the old 50% target');
});

test('a new gesture takes priority even before its first scroll event arrives', () => {
  for (const type of ['pointerdown', 'touchstart', 'wheel', 'keydown']) {
    const fixture = createChapterFixture();
    fixture.applyCss();
    fixture.input(type);
    fixture.setLayout({ height: 2800 });
    fixture.flushFrames();
    assert.equal(fixture.window.scrollY, 500, type);
  }
});

test('a queued real scroll irreversibly discards intent even if the position returns', () => {
  const fixture = createChapterFixture();
  fixture.applyCss();
  fixture.scrollTo(900);
  fixture.flushScrollEvents();
  fixture.scrollTo(500);
  fixture.setLayout({ height: 2800 });
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 500);
  assert.equal(fixture.activeListenerCount(), 0);
});

test('CSS after navigation captures the new fraction instead of the previous request', () => {
  const fixture = createChapterFixture();
  fixture.applyCss({ height: 2800 }); // 50% → 1000 of 2000.
  fixture.scrollTo(1600); // New navigation at 80%, event is still queued.
  fixture.applyCss({ height: 3800 }); // 80% of 3000 = 2400.
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 2400);
});

test('a newer non-preserving CSS generation cancels all pending restoration', () => {
  const fixture = createChapterFixture();
  fixture.applyCss();
  fixture.stepFrame(); // The older second callback is already queued.
  fixture.applyCss({ preserve: false, height: 2800 });
  const writesAfterLatestCss = fixture.scrollWrites.length;
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 500);
  assert.equal(fixture.scrollWrites.length, writesAfterLatestCss);
  assert.equal(fixture.activeListenerCount(), 0);
});

test('root or body replacement cannot retain an old document scroll callback', () => {
  for (const rootOnly of [true, false]) {
    const fixture = createChapterFixture();
    fixture.applyCss();
    fixture.stepFrame();
    fixture.replaceDocument({ rootOnly });
    fixture.setLayout({ height: 2800 });
    fixture.flushFrames();
    assert.equal(fixture.window.scrollY, 500);
    assert.equal(fixture.activeListenerCount(), 0);
    fixture.applyCss({ preserve: false });
    fixture.scrollTo(900);
    fixture.applyCss({ height: 3800 }); // New document: 45% of 3000 = 1350.
    fixture.flushFrames();
    assert.equal(fixture.window.scrollY, 1350);
  }
});

test('replacement reinitialization and native release do not leave old-root listeners', () => {
  const fixture = createChapterFixture();
  fixture.applyCss();
  const activeListeners = fixture.activeListenerCount();
  fixture.replaceDocument({ rootOnly: true });
  fixture.applyCss();
  assert.equal(fixture.activeListenerCount(), activeListeners, 'Reinitialization releases the old root request');
  fixture.replaceDocument({ rootOnly: true });
  fixture.cancelReaderScrollRestore();
  assert.equal(fixture.activeListenerCount(), 0, 'Native release works even after the root was replaced');
  fixture.flushFrames();
});

test('active fraction survives a zero-scroll-range resize before the next CSS generation', () => {
  const fixture = createChapterFixture();
  fixture.applyCss({ height: 2800 });
  fixture.setLayout({ height: 400, viewportHeight: 400 });
  assert.equal(fixture.window.scrollY, 0);
  fixture.flushScrollEvents();
  fixture.applyCss({ height: 2400 });
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 1000);
});

test('repeated CSS generations do not accumulate listeners and completion releases them', () => {
  const fixture = createChapterFixture();
  fixture.applyCss();
  const activeListeners = fixture.activeListenerCount();
  for (let i = 0; i < 10; i++) {
    fixture.applyCss();
    assert.equal(fixture.activeListenerCount(), activeListeners);
  }
  fixture.flushFrames();
  fixture.flushScrollEvents(); // A late own-scroll event cannot recreate the request.
  assert.equal(fixture.activeListenerCount(), 0);
  fixture.applyCss();
  fixture.pageHide();
  fixture.setLayout({ height: 2800 });
  fixture.flushFrames();
  assert.equal(fixture.window.scrollY, 500);
  assert.equal(fixture.activeListenerCount(), 0);
});

test('CSS normalization retains reader style and site-alignment reset contracts', () => {
  const fixture = createChapterFixture();
  fixture.applyCss({ preserve: false, css: 'body { background: transparent; }' });
  assert.equal(fixture.styles.get('__an_reader_style__').textContent, 'body { background: transparent; }');
  assert.equal(fixture.paragraph.getAttribute('align'), null);
  assert.equal(fixture.paragraph.getAttribute('bgcolor'), null);
  assert.equal(fixture.paragraph.getAttribute('color'), null);
  assert.equal(fixture.paragraph.style.getPropertyValue('font-family'), 'var(--an-reader-font)');
  assert.equal(fixture.paragraph.style.getPropertyValue('text-indent'), 'var(--an-reader-first-line-indent)');
  fixture.applyCss({ preserve: false, forceAlign: false, forceIndent: false });
  assert.equal(fixture.paragraph.style.getPropertyValue('text-align'), '');
  assert.equal(fixture.paragraph.style.getPropertyValue('text-indent'), '');
  assert.equal(fixture.window.scrollY, 500);
});
