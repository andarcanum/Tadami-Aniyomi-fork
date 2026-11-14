import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function createResizeFixture(options = {}) {
  const ktPath = path.resolve(
    __dirname,
    '../../main/java/eu/kanade/presentation/reader/novel/NovelBookEngineDocument.kt',
  );
  const ktSource = fs.readFileSync(ktPath, 'utf8');
  const scriptMatch = ktSource.match(/<script>([\s\S]*?)<\/script>/);
  assert.ok(scriptMatch, 'Could not find <script> in NovelBookEngineDocument.kt');

  const scriptCode = scriptMatch[1].replace(/\$documentGeneration/g, '0');
  const listeners = new Map();
  const contentInlineStyles = {};
  let currentScrollTop = options.initialScrollTop || 0;
  let columnCount = options.initialColumns || 4;
  let currentCharPageWidth = options.charPageWidth || 400;
  const pitchOffsetMap = new Map();

  const viewport = {
    scrollHeight: options.scrollHeight || 4000,
    clientHeight: options.clientHeight || 1000,
    clientWidth: options.clientWidth || 800,
    getBoundingClientRect: () => ({
      left: 0,
      top: 0,
      right: viewport.clientWidth,
      bottom: viewport.clientHeight,
      width: viewport.clientWidth,
      height: viewport.clientHeight,
    }),
    addEventListener: (event, cb) => {
      if (!listeners.has(`viewport:${event}`)) listeners.set(`viewport:${event}`, []);
      listeners.get(`viewport:${event}`).push(cb);
    },
    removeEventListener: () => {},
    get scrollTop() {
      return currentScrollTop;
    },
    set scrollTop(v) {
      const max = Math.max(0, this.scrollHeight - this.clientHeight);
      currentScrollTop = Math.max(0, Math.min(max, v));
    },
  };

  const text = options.sectionText || 'A'.repeat(1600);
  const textNode = {
    nodeType: 3,
    nodeValue: text,
    textContent: text,
    length: text.length,
  };
  const currentTranslateX = () => {
    const match = String(contentInlineStyles.transform || '').match(/translateX\((-?\d+(?:\.\d+)?)px\)/);
    return match ? parseFloat(match[1]) : 0;
  };
  const sectionNode = {
    classList: { contains: (cls) => cls === 'an-book-section' },
    dataset: { anIndex: '0', anChapter: '10' },
    getAttribute: (name) => (name === 'data-an-index' ? '0' : name === 'data-an-chapter' ? '10' : null),
    contains: (node) => node === textNode,
    getBoundingClientRect: () => {
      if (options.isPaginated) {
        const tx = currentTranslateX();
        const totalWidth = Math.max(1, columnCount * currentCharPageWidth);
        return {
          left: tx,
          top: 0,
          right: tx + totalWidth,
          bottom: viewport.clientHeight,
          width: totalWidth,
          height: viewport.clientHeight,
        };
      }
      return {
        left: 0,
        top: -currentScrollTop,
        right: viewport.clientWidth,
        bottom: viewport.scrollHeight - currentScrollTop,
        width: viewport.clientWidth,
        height: viewport.scrollHeight,
      };
    },
  };

  const content = {
    children: [sectionNode],
    childElementCount: 1,
    offsetWidth: options.clientWidth || 800,
    offsetHeight: options.clientHeight || 1000,
    contains: (node) => node === textNode || node === sectionNode,
    get scrollWidth() {
      return Math.max(1, columnCount * currentCharPageWidth);
    },
    get scrollHeight() {
      return viewport.scrollHeight;
    },
    style: {
      setProperty: (prop, val) => {
        contentInlineStyles[prop] = val;
      },
    },
    getBoundingClientRect: () => {
      const tx = options.isPaginated ? currentTranslateX() : 0;
      return {
        left: tx,
        top: 0,
        right: tx + viewport.clientWidth,
        bottom: viewport.clientHeight,
        width: viewport.clientWidth,
        height: viewport.clientHeight,
      };
    },
    appendChild: () => {},
    removeChild: () => {},
    querySelectorAll: (selector) => {
      if (selector === '.an-book-section') return [sectionNode];
      return [];
    },
  };

  const dom = {
    'an-book-viewport': viewport,
    'an-book-content': content,
    'an-book-reader-css': { textContent: '' },
  };

  let caretOffsetOverride = options.initialCaretOffset ?? 0;

  const ctx = {
    console: { log: () => {}, error: () => {} },
    getComputedStyle: (el) => {
      if (el === ctx.document.documentElement) {
        return {
          backgroundColor: options.rootBackgroundColor || 'rgb(245, 236, 216)',
          backgroundImage: options.rootBackgroundImage || 'linear-gradient(red, blue)',
          backgroundRepeat: 'no-repeat',
          backgroundSize: '100% 100%',
          backgroundPositionY: '0px',
          getPropertyValue: (name) => (name === '--an-reader-bg' ? '#F5ECD8' : ''),
        };
      }
      if (el === ctx.document.body) {
        return {
          backgroundColor: 'rgba(0, 0, 0, 0)',
          backgroundImage: 'none',
          backgroundRepeat: 'no-repeat',
          backgroundSize: 'auto',
          backgroundPositionY: '0px',
        };
      }
      return {
        paddingLeft: '0px',
        paddingRight: '0px',
        paddingTop: '0px',
        paddingBottom: '0px',
        height: `${viewport.clientHeight}px`,
        transform: contentInlineStyles.transform || 'none',
      };
    },
    document: {
      documentElement: {
        dataset: { anBookFlow: options.isPaginated ? 'paginated' : 'scrolled' },
      },
      getElementById: (id) => dom[id] || null,
      createElement: () => ({
        style: { setProperty: () => {} },
        setAttribute: () => {},
        appendChild: () => {},
        removeChild: () => {},
        getBoundingClientRect: () => ({ left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 }),
      }),
      createRange: () => {
        const range = {
          _startOffset: 0,
          _endOffset: 0,
          setStart: (_node, off) => {
            range._startOffset = off;
          },
          setEnd: (_node, off) => {
            range._endOffset = off;
          },
          collapse: () => {},
          getBoundingClientRect: () => {
            const pitch = Math.max(1, viewport.clientWidth);
            const tx = options.isPaginated ? currentTranslateX() : 0;
            const charsPerPage = Math.max(1, Math.floor(text.length / Math.max(1, columnCount)));
            const col = Math.floor(range._startOffset / charsPerPage);
            const yTop = Math.round((range._startOffset / text.length) * viewport.scrollHeight) - currentScrollTop;
            return {
              left: tx + col * pitch,
              right: tx + col * pitch + 20,
              top: yTop,
              bottom: yTop + 20,
              width: 20,
              height: 20,
            };
          },
          getClientRects: () => [],
        };
        return range;
      },
      caretRangeFromPoint: (x, y) => {
        if (options.isPaginated) {
          const charsPerPage = Math.max(1, Math.floor(text.length / Math.max(1, columnCount)));
          const offset = Math.min(text.length, Math.max(0, Math.round(caretOffsetOverride)));
          return { startContainer: textNode, startOffset: offset };
        }
        const absY = currentScrollTop + y;
        const offset = Math.min(
          text.length,
          Math.max(0, Math.round((absY / Math.max(1, viewport.scrollHeight)) * text.length)),
        );
        return { startContainer: textNode, startOffset: offset };
      },
      body: { appendChild: () => {}, removeChild: () => {} },
      head: { appendChild: () => {} },
      addEventListener: () => {},
      createTreeWalker: () => {
        let emitted = false;
        return {
          nextNode: () => {
            if (emitted) return null;
            emitted = true;
            return textNode;
          },
        };
      },
    },
    window: {
      innerWidth: options.clientWidth || 800,
      innerHeight: options.clientHeight || 1000,
      addEventListener: (event, cb) => {
        if (!listeners.has(`window:${event}`)) listeners.set(`window:${event}`, []);
        listeners.get(`window:${event}`).push(cb);
      },
      setTimeout: (cb) => 1,
      clearTimeout: () => {},
      requestAnimationFrame: (cb) => 1,
      cancelAnimationFrame: () => {},
      AnBookNative: {
        onReady: () => {},
        onRelocated: () => {},
        onScrollBoundary: () => {},
        onSectionMeasured: () => {},
      },
    },
    performance: { now: () => 1000 },
    Date: { now: () => 1000 },
    Node: { TEXT_NODE: 3 },
    NodeFilter: { SHOW_TEXT: 4 },
    Math,
    Number,
    Object,
    JSON,
    Array,
    Promise,
    String,
  };

  ctx.window.window = ctx.window;
  ctx.window.document = ctx.document;
  ctx.window.performance = ctx.performance;
  ctx.window.getComputedStyle = ctx.getComputedStyle;
  ctx.window.AnBookNative = ctx.window.AnBookNative;
  ctx.requestAnimationFrame = ctx.window.requestAnimationFrame;
  ctx.cancelAnimationFrame = ctx.window.cancelAnimationFrame;

  vm.runInNewContext(scriptCode, ctx);

  return {
    viewport,
    content,
    contentInlineStyles,
    engine: ctx.window.__anBookEngine,
    setLayout(newWidth, newHeight, newColumns, newScrollHeight) {
      viewport.clientWidth = newWidth;
      viewport.clientHeight = newHeight;
      currentCharPageWidth = newWidth;
      columnCount = newColumns;
      if (typeof newScrollHeight === 'number') viewport.scrollHeight = newScrollHeight;
    },
    setCaretOffset(offset) {
      caretOffsetOverride = offset;
    },
    triggerWindowResize() {
      (listeners.get('window:resize') || []).forEach((cb) => cb());
    },
    triggerScroll() {
      (listeners.get('viewport:scroll') || []).forEach((cb) => cb());
    },
  };
}

test('paginated resize ignores 0px transients and preserves character offset across landscape rotation', () => {
  const f = createResizeFixture({
    isPaginated: true,
    clientWidth: 400,
    clientHeight: 800,
    charPageWidth: 400,
    initialColumns: 4, // 400 chars per page
  });

  // Navigate to charOffset = 850 (page 2 of 0..3 in portrait).
  f.engine.goTo(850, 0);
  const before = JSON.parse(f.engine.relocate());
  assert.equal(before.charOffset >= 800, true);

  // Transient 0x0 resize frame must not corrupt settled anchor or relocate output.
  f.setLayout(0, 0, 1);
  f.triggerWindowResize();
  const duringTransient = JSON.parse(f.engine.relocate());
  assert.equal(duringTransient.charOffset >= 800, true);

  // Landscape (800x400, 2 pages total of 800 chars each): charOffset 850 belongs on page 1.
  f.setLayout(800, 400, 2);
  f.triggerWindowResize();
  assert.equal(f.contentInlineStyles.transform, 'translateX(-800px)');

  // Rotating back to portrait restores page 2 again without rewinding to 0.
  f.setLayout(400, 800, 4);
  f.triggerWindowResize();
  assert.equal(f.contentInlineStyles.transform, 'translateX(-800px)');
});

test('scrolled resize re-anchors scrollTop to current text offset', () => {
  const f = createResizeFixture({
    isPaginated: false,
    clientWidth: 400,
    clientHeight: 800,
    scrollHeight: 4000,
  });

  // Scroll to ~50% (charOffset ~800) and flush settled anchor via relocate().
  f.viewport.scrollTop = 2000;
  f.triggerScroll();
  const settled = JSON.parse(f.engine.relocate());
  assert.ok(settled.charOffset > 700, `expected >700, got ${settled.charOffset}`);

  // Width widens and document becomes shorter (scrollHeight 2000px): scrollTop must scale cleanly.
  f.setLayout(800, 400, 1, 2000);
  f.triggerWindowResize();
  assert.ok(
    f.viewport.scrollTop >= 900 && f.viewport.scrollTop <= 1200,
    `expected scrollTop near ~1000, got ${f.viewport.scrollTop}`,
  );
});

test('book_flip reads paper background from root when body is forced transparent', () => {
  const f = createResizeFixture({
    isPaginated: true,
    clientWidth: 400,
    clientHeight: 800,
    charPageWidth: 400,
    initialColumns: 4,
    rootBackgroundColor: 'rgb(245, 236, 216)',
    rootBackgroundImage: 'linear-gradient(rgb(1, 2, 3), rgb(4, 5, 6))',
  });

  f.engine.goTo(400, 0); // startPage = 1
  f.engine.next('BOOK_FLIP');

  assert.equal(f.contentInlineStyles['background-color'], 'rgb(245, 236, 216)');
  assert.equal(f.contentInlineStyles['background-image'], 'linear-gradient(rgb(1, 2, 3), rgb(4, 5, 6))');
  assert.equal(f.contentInlineStyles['background-size'], '400px 800px');
  assert.equal(f.contentInlineStyles['background-position'], '400px 0px');
});
