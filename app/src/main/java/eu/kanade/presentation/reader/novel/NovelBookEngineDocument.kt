package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow

/** Builds the isolated section document used by the dedicated book renderer. */
internal fun buildNovelBookEngineDocumentHtml(
    document: NovelBookDocument,
    flow: NovelBookEngineFlow,
    documentGeneration: Long = 0L,
    readerCss: String = "",
): String {
    val flowCss = when (flow) {
        NovelBookEngineFlow.PAGINATED -> """
            #an-book-viewport {
              position: fixed;
              inset: 0;
              overflow: hidden;
              touch-action: none;
              /* Depth styles turn the column box in 3D, so the viewport carries the perspective.
                 It lives in the paged stylesheet only: the scrolled flow never transforms its
                 content, and promoting the whole stitched document to a 3D composited layer there
                 made every scroll frame repaint one giant texture. */
              perspective: 1400px !important;
              perspective-origin: 50% 50% !important;
            }
            #an-book-content {
              /* Only the parts of the paged geometry that cannot be fought over live here: the
                 reader stylesheet forces width/height/padding/background with !important, so the
                 column box itself is sized from JS with inline !important styles, which beat every
                 stylesheet rule. See applyPagedGeometry below. */
              box-sizing: border-box !important;
              column-fill: auto !important;
              overflow: visible !important;
              /* Texture, background image and OLED gradient are painted on html/body, so every
                 layer above them has to stay see-through or the reader shows a flat colour. */
              background: transparent !important;
              /* The page turn animates the column box (depth scales it, book rotates it, curl
                 tilts it), so the paged flow keeps the transform promotion that makes those turns
                 smooth. The scrolled flow must not have it: see the viewport rule above. */
              transform-style: preserve-3d !important;
              will-change: transform, opacity, filter !important;
            }
            #an-book-content img,
            #an-book-content svg,
            #an-book-content video {
              object-fit: contain;
              break-inside: avoid;
              page-break-inside: avoid;
            }
            /* One block per chapter stitched into the paged document. The paged flow used to hold
               exactly one section, which is why crossing a chapter meant swapping the document and
               showing a half empty page. Sections now flow into the same column box, so a chapter
               boundary is just another column. */
            .an-book-section {
              display: block;
              background: transparent !important;
            }
            /* Block the voice is reading, in the paged flow. */
            #an-book-content [data-an-tts-highlight] {
              background-color: rgba(128, 128, 128, 0.28);
              border-radius: 4px;
            }
            /* Marker used to measure how many columns the section actually produced. */
            #an-book-end {
              display: block;
              width: 1px;
              height: 1px;
            }
        """.trimIndent()
        NovelBookEngineFlow.SCROLLED -> """
            #an-book-viewport {
              position: fixed;
              inset: 0;
              overflow-x: hidden;
              overflow-y: auto;
              background: transparent !important;
            }
            #an-book-content {
              box-sizing: border-box;
              width: 100%;
              min-height: 100%;
              /* Texture, background image and OLED gradient are painted on html/body, so this
                 layer must stay see-through instead of covering them with a flat colour. */
              background: transparent !important;
            }
            /* One block per chapter the reader stitched into the document, so crossing a chapter
               is a plain scroll instead of a document swap. */
            .an-book-section {
              display: block;
              width: 100%;
              background: transparent !important;
            }
            /* Block the voice is reading. The chapter WebView paints its highlight from its own
               script; the book document is built here, so without this rule the anchor script
               marked the right paragraph and nothing at all changed on screen. */
            #an-book-content [data-an-tts-highlight] {
              background-color: rgba(128, 128, 128, 0.28);
              border-radius: 4px;
            }
            #an-book-content img,
            #an-book-content svg,
            #an-book-content video {
              max-width: 100%;
              height: auto;
              object-fit: contain;
            }
        """.trimIndent()
    }
    // Both flows stitch chapters together now, so both wrap their content in a section element: it
    // is what lets a position be expressed as (section, offset) and what the stitching addresses.
    val sectionMarkup = "<section class=\"an-book-section\" " +
        "data-an-index=\"${document.sectionIndex}\" " +
        "data-an-chapter=\"${document.chapterId}\">${document.html}</section>"
    val engineScript = """
        <script>
          (function() {
            const viewport = document.getElementById('an-book-viewport');
            const content = document.getElementById('an-book-content');
            const isPaginated = document.documentElement.dataset.anBookFlow === 'paginated';
            const documentGeneration = $documentGeneration;
            // Boot marker: a paged document that never reaches the ready handshake still leaves a
            // trace in logcat, and the error hook surfaces exceptions thrown by this script.
            console.log('an-book-boot flow=' + document.documentElement.dataset.anBookFlow +
              ' generation=' + documentGeneration +
              ' window=' + window.innerWidth + 'x' + window.innerHeight +
              ' viewport=' + (viewport ? viewport.clientWidth + 'x' + viewport.clientHeight : 'missing') +
              ' kids=' + (content ? content.childElementCount : 'missing'));
            window.addEventListener('error', function(event) {
              console.log('an-book-error ' + ((event && event.message) ? event.message : 'unknown'));
            });
            // Inline !important styles beat every stylesheet rule, including the reader CSS and
            // any user CSS, which is why the paged geometry is applied from here.
            const setImportant = function(element, property, value) {
              element.style.setProperty(property, value, 'important');
            };
            let pageColumn = 1;
            let pageGap = 0;
            let pagePitch = 1;
            // How close to the edge of the resident columns the reader has to be before the next
            // chapter is asked for. Two columns is roughly the scrolled flow's "one viewport ahead".
            const PAGE_STITCH_MARGIN = 2;
            let pageIndex = 0;
            // Text length of each resident section, computed once per mount. The cheap scroll
            // report derives the reading offset from geometry and these cached counts instead of
            // walking the section's text nodes on every frame.
            let sectionCharCounts = {};
            // Paged geometry is measured as a delta between rects of the column box. A page turn
            // animates that box (depth scales it, book rotates it, curl tilts it), so anything
            // measured mid-turn came back distorted: the page count collapsed, a turn could report a
            // premature end of the chapter and the reported text offset snapped to its start, which
            // is why the paged progress bar only ever sat at the beginning or the end. Measurements
            // are taken while the box is settled and cached for the duration of the turn.
            let turnActive = false;
            let pageCountCache = 1;
            let pageOffsetCache = { page: -1, section: -1, offset: 0 };
            // Every cached page measurement belongs to one geometry and one set of resident
            // sections. Restyling the document or stitching a chapter into it invalidates both, so
            // the caches are dropped in one place instead of being half updated.
            const invalidatePagedCaches = function() {
              turnActive = false;
              pageCountCache = 1;
              pageOffsetCache = { page: -1, section: -1, offset: 0 };
            };
            const sentinel = (function() {
              if (!isPaginated) return null;
              const marker = document.createElement('span');
              marker.id = 'an-book-end';
              marker.setAttribute('aria-hidden', 'true');
              content.appendChild(marker);
              return marker;
            })();
            const applyPagedGeometry = function() {
              if (!isPaginated) return;
              const style = window.getComputedStyle(content);
              const padLeft = parseFloat(style.paddingLeft) || 0;
              const padRight = parseFloat(style.paddingRight) || 0;
              const padTop = parseFloat(style.paddingTop) || 0;
              const padBottom = parseFloat(style.paddingBottom) || 0;
              const width = Math.max(1, viewport.clientWidth || window.innerWidth || 1);
              const height = Math.max(1, viewport.clientHeight || window.innerHeight || 1);
              pageColumn = Math.max(1, width - padLeft - padRight);
              pageGap = Math.max(0, padLeft + padRight);
              pagePitch = pageColumn + pageGap;
              setImportant(content, 'width', width + 'px');
              setImportant(content, 'height', height + 'px');
              setImportant(content, 'max-height', height + 'px');
              setImportant(content, 'min-height', '0px');
              setImportant(content, 'column-count', 'auto');
              setImportant(content, 'column-width', pageColumn + 'px');
              setImportant(content, 'column-gap', pageGap + 'px');
              setImportant(content, 'column-fill', 'auto');
              // An unbreakable element taller than the page box moves to the next column and
              // leaves the page blank: that is what painted one strip and nothing else.
              const columnHeight = Math.max(1, height - padTop - padBottom);
              const media = content.querySelectorAll('img, svg, video');
              for (let index = 0; index < media.length; index += 1) {
                setImportant(media[index], 'max-width', pageColumn + 'px');
                setImportant(media[index], 'max-height', columnHeight + 'px');
                setImportant(media[index], 'height', 'auto');
              }
              // Cached measurements belong to the geometry that produced them.
              invalidatePagedCaches();
            };
            const pageCount = function() {
              if (!isPaginated) return 1;
              // A turn is in flight, so the last settled measurement is the truthful one.
              if (turnActive) return pageCountCache;
              // Overflow columns are painted outside the content box and do not reliably grow
              // scrollWidth, so the count is measured from a marker at the end of the section.
              let width = 0;
              if (sentinel) {
                const contentRect = content.getBoundingClientRect();
                const markerRect = sentinel.getBoundingClientRect();
                width = markerRect.left - contentRect.left + markerRect.width;
              }
              width = Math.max(width, content.scrollWidth, pagePitch);
              pageCountCache = Math.max(1, Math.ceil((width - 1) / pagePitch));
              return pageCountCache;
            };
            const currentPage = function() {
              if (!isPaginated) return 0;
              return Math.max(0, Math.min(pageIndex, pageCount() - 1));
            };
            // Translating the column box is deterministic: scrollLeft on an overflow-hidden
            // container is fought over by scroll anchoring and reset by relayouts.
            // The paged geometry is applied with inline !important styles, so the page turn has
            // to be animated from here as well: a stylesheet rule can never beat an inline
            // !important transform, which is why every style looked instant.
            const TURN_DURATION_MILLIS = 320;
            // Book Flip hinges at the leading edge of the VISIBLE page with real perspective;
            // rotating around the strip's far-left origin made the page sweep along an arc that
            // grew with the page index. Every other style keeps its original whole-strip shape.
            const FLIP_PERSPECTIVE_PX = 1200;
            const FLIP_ANGLE_DEG = 76;
            const flipOriginXPx = function(startPage, forward) {
              return forward ? startPage * pagePitch : (startPage + 1) * pagePitch;
            };
            const turnOrigin = function(style, originXPx) {
              if (style === 'book_flip') return Math.round(originXPx) + 'px center';
              if (style === 'book') return 'left center';
              if (style === 'curl') return 'bottom right';
              return '50% 50%';
            };
            const turnTransform = function(style, offsetPx, forward, originXPx) {
              const base = 'translateX(' + offsetPx + 'px)';
              if (style === 'depth') return base + ' scale(0.86) translateZ(-140px)';
              if (style === 'book_flip') {
                const sign = forward ? -1 : 1;
                return base + ' perspective(' + FLIP_PERSPECTIVE_PX + 'px) rotateY(' +
                  (sign * FLIP_ANGLE_DEG) + 'deg) scale(0.97)';
              }
              if (style === 'book') {
                return base + ' rotateY(' + (forward ? -24 : 24) + 'deg) scale(0.94)';
              }
              if (style === 'curl') return base + ' rotateZ(-5deg) rotateX(9deg) scale(0.94)';
              return base;
            };
            let turnTimer = 0;
            const settlePage = function(offsetPx, durationMillis) {
              setImportant(content, 'transition',
                'transform ' + durationMillis + 'ms cubic-bezier(0.25, 1, 0.5, 1), opacity ' +
                durationMillis + 'ms ease-out, filter ' + durationMillis + 'ms ease-out');
              setImportant(content, 'transform', 'translateX(' + offsetPx + 'px)');
              setImportant(content, 'opacity', '1');
              setImportant(content, 'filter', 'none');
            };
            const goToPage = function(page, styleName) {
              const target = Math.max(0, Math.min(page, pageCount() - 1));
              const style = String(styleName || 'SLIDE').toLowerCase();
              const startPage = pageIndex;
              pageIndex = target;
              const startX = -startPage * pagePitch;
              const targetX = -target * pagePitch;
              const forward = target >= startPage;
              if (turnTimer !== 0) {
                window.clearTimeout(turnTimer);
                turnTimer = 0;
              }
              // A rapid turn can start on top of a running animation, so the column box is snapped
              // back to a pure translation of the page being left. Everything measured below then
              // sees settled geometry, and the turn still animates because the transition property is
              // set again right after.
              turnActive = false;
              setImportant(content, 'transition', 'none');
              setImportant(content, 'transform', 'translateX(' + startX + 'px)');
              setImportant(content, 'opacity', '1');
              setImportant(content, 'filter', 'none');
              // Drop any paper copied onto the column by a previous Book Flip.
              setImportant(content, 'background-color', '');
              setImportant(content, 'background-image', '');
              pageOffsetCache = {
                page: target,
                section: sectionIndexOf(sectionNodeAtPage(target)),
                offset: measurePageOffset(target)
              };
              const flipOriginX = flipOriginXPx(startPage, forward);
              setImportant(content, 'transform-origin', turnOrigin(style, flipOriginX));
              if (style === 'instant' || startPage === target) {
                setImportant(content, 'transition', 'none');
                setImportant(content, 'transform', 'translateX(' + targetX + 'px)');
                setImportant(content, 'opacity', '1');
                setImportant(content, 'filter', 'none');
                return target;
              }
              if (style === 'slide') {
                turnActive = true;
                settlePage(targetX, TURN_DURATION_MILLIS);
                window.setTimeout(function() { turnActive = false; }, TURN_DURATION_MILLIS);
                return target;
              }
              // Two phases: the current page first lifts, tilts or curls away, then the target
              // page settles back into a flat, fully opaque column box.
              const half = Math.max(90, Math.round(TURN_DURATION_MILLIS / 2));
              turnActive = true;
              setImportant(content, 'transition',
                'transform ' + half + 'ms ease-in, opacity ' + half + 'ms ease-in, filter ' +
                half + 'ms ease-in');
              setImportant(content, 'transform', turnTransform(style, startX, forward, flipOriginX));
              if (style === 'book_flip') {
                // Paper does not fade while flipping; a light shade sells the lift instead.
                // The underlay itself is painted on html/body and content stays transparent by
                // design, so a tilting sheet would expose bare text over the static backdrop.
                // Copy the body's painted background onto the sheet, phase-shifted to the page
                // being lifted, so it carries its own paper mid-air and hands it back on land.
                const bodyStyle = getComputedStyle(document.body);
                const paperShiftX = Math.round(startPage * pagePitch);
                setImportant(content, 'background-color', bodyStyle.backgroundColor);
                if (bodyStyle.backgroundImage && bodyStyle.backgroundImage !== 'none') {
                  setImportant(content, 'background-image', bodyStyle.backgroundImage);
                  setImportant(content, 'background-repeat', bodyStyle.backgroundRepeat);
                  setImportant(content, 'background-size', bodyStyle.backgroundSize);
                  setImportant(
                    content,
                    'background-position',
                    (-paperShiftX) + 'px ' + (bodyStyle.backgroundPositionY || '0px'),
                  );
                }
                setImportant(content, 'opacity', '1');
                setImportant(content, 'filter', 'brightness(0.90)');
              } else {
                setImportant(content, 'opacity', '0.82');
                setImportant(content, 'filter', 'brightness(0.88)');
              }
              turnTimer = window.setTimeout(function() {
                turnTimer = 0;
                settlePage(targetX, half);
                window.setTimeout(function() {
                  turnActive = false;
                  // Hand the underlay back to the fixed body layer once the sheet has landed.
                  setImportant(content, 'background-color', '');
                  setImportant(content, 'background-image', '');
                }, half);
              }, half);
              return target;
            };
            // The scrolled document can hold several chapters at once, so a position is only
            // unambiguous as (section, offset inside that section). The paged document has no section
            // wrappers and reports -1, which keeps the engine on the section it opened.
            const sectionNodes = function() {
              return Array.prototype.slice.call(content.children).filter(function(node) {
                return node.classList && node.classList.contains('an-book-section');
              });
            };
            const sectionIndexOf = function(node) {
              if (!node || typeof node.getAttribute !== 'function') return -1;
              const value = parseInt(node.getAttribute('data-an-index'), 10);
              return isNaN(value) ? -1 : value;
            };
            const sectionChapterOf = function(node) {
              if (!node || typeof node.getAttribute !== 'function') return 0;
              const value = parseInt(node.getAttribute('data-an-chapter'), 10);
              return isNaN(value) ? 0 : value;
            };
            const sectionNodeAt = function(sectionIndex) {
              const nodes = sectionNodes();
              for (let index = 0; index < nodes.length; index += 1) {
                if (sectionIndexOf(nodes[index]) === sectionIndex) return nodes[index];
              }
              return null;
            };
            const resolveSectionNode = function(sectionIndex) {
              const nodes = sectionNodes();
              if (nodes.length === 0) return content;
              if (sectionIndex === undefined || sectionIndex === null || sectionIndex < 0) return nodes[0];
              return sectionNodeAt(sectionIndex);
            };
            const textNodesIn = function(root) {
              const nodes = [];
              if (!root) return nodes;
              const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
              let node = walker.nextNode();
              while (node) {
                if ((node.nodeValue || '').length > 0) nodes.push(node);
                node = walker.nextNode();
              }
              return nodes;
            };
            const textNodes = function() {
              return textNodesIn(content);
            };
            const totalCharCount = function(nodes) {
              return nodes.reduce(function(total, node) {
                return total + (node.nodeValue || '').length;
              }, 0);
            };
            // First column a section occupies. In the paged flow sections are laid out side by side
            // in the column box, so "where a section starts" is a page number, not a vertical edge.
            const sectionStartPage = function(node) {
              if (!node || !isPaginated) return 0;
              const contentRect = content.getBoundingClientRect();
              const rect = node.getBoundingClientRect();
              return Math.max(0, Math.floor((rect.left - contentRect.left) / pagePitch));
            };
            // Section that owns [page]: the last one that starts at or before it.
            const sectionNodeAtPage = function(page) {
              const nodes = sectionNodes();
              if (nodes.length === 0) return content;
              let candidate = nodes[0];
              for (let index = 0; index < nodes.length; index += 1) {
                if (sectionStartPage(nodes[index]) <= page) candidate = nodes[index];
              }
              return candidate;
            };
            // Section the viewport currently starts in: the last one whose top edge is at or above
            // the top of the viewport, or - when paged - the one owning the current column.
            const currentSectionNode = function() {
              const nodes = sectionNodes();
              if (nodes.length === 0) return content;
              if (isPaginated) return sectionNodeAtPage(currentPage());
              const top = viewport.getBoundingClientRect().top;
              let candidate = nodes[0];
              for (let index = 0; index < nodes.length; index += 1) {
                if (nodes[index].getBoundingClientRect().top - top <= 1) candidate = nodes[index];
              }
              return candidate;
            };
            // Offset of [page] inside its section from the column geometry: how far the page sits in
            // the section's column span, scaled by the section's text length. This is the cheap
            // paged position: the previous implementation binary-searched the text with a range rect
            // per step, each of which forced a layout of the whole column box - too slow to run at
            // document ready or on a page turn.
            const pageFractionOffsetIn = function(sectionNode, page, cachedTotal) {
              const total = cachedTotal !== undefined ? cachedTotal : totalCharCount(textNodesIn(sectionNode));
              if (total <= 0) return 0;
              const start = sectionStartPage(sectionNode);
              const span = Math.max(1, sectionPageSpan(sectionNode));
              const within = Math.max(0, Math.min(page - start, span - 1));
              const fraction = span <= 1 ? 0 : within / (span - 1);
              return Math.round(fraction * Math.max(0, total - 1));
            };
            const fallbackOffsetIn = function(sectionNode, cachedTotal) {
              const total = cachedTotal !== undefined ? cachedTotal : totalCharCount(textNodesIn(sectionNode));
              if (total <= 0) return 0;
              if (isPaginated) {
                // Pages are shared by every resident section, so the fraction has to be taken
                // inside this section's own column range instead of across the whole document.
                return pageFractionOffsetIn(sectionNode, currentPage(), total);
              }
              const rect = sectionNode.getBoundingClientRect();
              const top = viewport.getBoundingClientRect().top;
              const height = Math.max(1, rect.height);
              const fraction = Math.max(0, Math.min(1, (top - rect.top) / height));
              return Math.round(fraction * Math.max(0, total - 1));
            };
            const offsetWithin = function(sectionNode, container, offsetInContainer) {
              const nodes = textNodesIn(sectionNode);
              // A caret can land on an element instead of text (a point in the page padding or in the
              // gap between two blocks). Resolving it to the child it points at keeps the offset where
              // the reader is; it used to collapse onto the first text node, i.e. the chapter start.
              let target = container;
              let targetOffset = offsetInContainer;
              if (container && container.nodeType === Node.ELEMENT_NODE) {
                const children = container.childNodes;
                const index = Math.max(0, Math.min(offsetInContainer, children.length - 1));
                target = children.length > 0 ? children[index] : container;
                targetOffset = 0;
              }
              let offset = 0;
              for (const node of nodes) {
                if (node === target) {
                  return offset + Math.max(0, Math.min(targetOffset, (node.nodeValue || '').length));
                }
                if (target && target.nodeType === Node.ELEMENT_NODE && target.contains(node)) {
                  return offset;
                }
                offset += (node.nodeValue || '').length;
              }
              return offset;
            };
            // Range over the character at an exact text offset of a section. Restoring a position and
            // asking which page an offset landed on both need it, so it lives in one place.
            const rangeAtOffset = function(nodes, charOffset) {
              const total = totalCharCount(nodes);
              if (nodes.length === 0 || total <= 0) return null;
              let remaining = Math.max(0, Math.min(Number(charOffset) || 0, total - 1));
              let target = nodes[nodes.length - 1];
              let localOffset = (target.nodeValue || '').length;
              for (const node of nodes) {
                const length = (node.nodeValue || '').length;
                if (remaining <= length) {
                  target = node;
                  localOffset = remaining;
                  break;
                }
                remaining -= length;
              }
              const length = (target.nodeValue || '').length;
              const start = Math.max(0, Math.min(localOffset, length));
              const range = document.createRange();
              range.setStart(target, start);
              range.setEnd(target, Math.min(length, start + 1));
              return range;
            };
            // A range on whitespace between blocks has an empty rect, which would read as column 0,
            // so the enclosing element answers for it instead.
            const rectOfRange = function(range) {
              const rect = range.getBoundingClientRect();
              if (rect.width > 0 || rect.height > 0) return rect;
              const parent = range.startContainer.parentElement;
              return parent ? parent.getBoundingClientRect() : rect;
            };
            const pageOfOffset = function(nodes, charOffset, contentRect) {
              const range = rangeAtOffset(nodes, charOffset);
              if (!range) return 0;
              const rect = rectOfRange(range);
              return Math.max(0, Math.floor((rect.left - contentRect.left) / pagePitch));
            };
            // How many columns a section occupies, measured from its own bounding box.
            const sectionPageSpan = function(node) {
              if (!node || !isPaginated) return 1;
              const contentRect = content.getBoundingClientRect();
              const rect = node.getBoundingClientRect();
              const start = Math.floor((rect.left - contentRect.left) / pagePitch);
              const end = Math.floor((rect.right - contentRect.left - 1) / pagePitch);
              return Math.max(1, end - start + 1);
            };
            const measurePageOffset = function(page) {
              const node = sectionNodeAtPage(page);
              const index = sectionIndexOf(node);
              let total = sectionCharCounts[index];
              if (total === undefined) {
                total = totalCharCount(textNodesIn(node));
                if (total > 0) sectionCharCounts[index] = total;
              }
              return pageFractionOffsetIn(node, page, total);
            };
            const charOffsetAtPage = function(page) {
              const section = sectionIndexOf(sectionNodeAtPage(page));
              if (turnActive) return pageOffsetCache.offset;
              if (pageOffsetCache.page === page && pageOffsetCache.section === section) {
                return pageOffsetCache.offset;
              }
              const offset = measurePageOffset(page);
              pageOffsetCache = { page: page, section: section, offset: offset };
              return offset;
            };
            const locationAtViewportStart = function() {
              const sectionNode = currentSectionNode();
              if (!sectionNode) return { sectionIndex: -1, charOffset: 0 };
              if (isPaginated) {
                return {
                  sectionIndex: sectionIndexOf(sectionNode),
                  charOffset: charOffsetAtPage(currentPage())
                };
              }
              const bounds = viewport.getBoundingClientRect();
              const x = Math.min(bounds.right - 1, bounds.left + Math.max(1, viewport.clientWidth * 0.08));
              const y = Math.min(bounds.bottom - 1, bounds.top + Math.max(1, viewport.clientHeight * 0.08));
              let range = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
              if (!range && document.caretPositionFromPoint) {
                const position = document.caretPositionFromPoint(x, y);
                if (position) {
                  range = document.createRange();
                  range.setStart(position.offsetNode, position.offset);
                  range.collapse(true);
                }
              }
              if (!range || !sectionNode.contains(range.startContainer)) {
                return {
                  sectionIndex: sectionIndexOf(sectionNode),
                  charOffset: fallbackOffsetIn(sectionNode)
                };
              }
              return {
                sectionIndex: sectionIndexOf(sectionNode),
                charOffset: offsetWithin(sectionNode, range.startContainer, range.startOffset)
              };
            };
            const charOffsetAtViewportStart = function() {
              return locationAtViewportStart().charOffset;
            };
            const relocate = function() {
              const position = locationAtViewportStart();
              return JSON.stringify({
                kind: 'moved',
                sectionIndex: position.sectionIndex,
                charOffset: position.charOffset
              });
            };
            let relocateFrame = 0;
            const reportRelocated = function() {
              const startedAt = Date.now();
              try {
                if (window.AnBookNative && typeof window.AnBookNative.onRelocated === 'function') {
                  window.AnBookNative.onRelocated($documentGeneration, relocate());
                }
              } catch (_) {
                // The native renderer may have been detached while this frame was pending.
              }
              // Mirrored into logcat by the renderer's WebChromeClient, so a slow exact relocation
              // can be measured with: adb logcat -s NovelBookWebView
              const duration = Date.now() - startedAt;
              if (duration >= RELOCATE_SLOW_THRESHOLD_MILLIS) {
                console.log('an-book-relocate-slow ' + duration + 'ms');
              }
            };
            const pushRelocated = function() {
              if (relocateFrame !== 0) return;
              relocateFrame = requestAnimationFrame(function() {
                relocateFrame = 0;
                reportRelocated();
              });
            };
            let lastReportedSectionIndex = -1;
            let lastReportedCharOffset = -1;
            // Reports the reading position from geometry instead of a caret hit-test: the section
            // under the viewport top plus how far into it the viewport is, over the cached text
            // length. Walking the whole section's text nodes per frame is what made the scroll
            // path expensive; the exact caret resolution now only runs when the scroll settles.
            const cheapCharOffsetAtViewportStart = function() {
              const sectionNode = currentSectionNode();
              if (!sectionNode) return 0;
              const sectionIndex = sectionIndexOf(sectionNode);
              let total = sectionCharCounts[sectionIndex];
              if (total === undefined) {
                total = totalCharCount(textNodesIn(sectionNode));
                if (total > 0) sectionCharCounts[sectionIndex] = total;
              }
              return fallbackOffsetIn(sectionNode, total);
            };
            const reportCheapRelocated = function() {
              const sectionNode = currentSectionNode();
              if (!sectionNode) return;
              const sectionIndex = sectionIndexOf(sectionNode);
              const charOffset = cheapCharOffsetAtViewportStart();
              if (sectionIndex === lastReportedSectionIndex && charOffset === lastReportedCharOffset) {
                return;
              }
              lastReportedSectionIndex = sectionIndex;
              lastReportedCharOffset = charOffset;
              try {
                if (window.AnBookNative && typeof window.AnBookNative.onRelocated === 'function') {
                  window.AnBookNative.onRelocated($documentGeneration, JSON.stringify({
                    kind: 'moved',
                    sectionIndex: sectionIndex,
                    charOffset: charOffset
                  }));
                }
              } catch (_) {
                // The native renderer may have been detached while this frame was pending.
              }
            };
            // How often the document reports its position while the reader is scrolling. The exact
            // caret-based resolution is expensive (it walks the whole section's text), so the scroll
            // path reports a cheap geometry-based offset at this cadence and resolves exactly once
            // the scroll settles.
            const RELOCATE_REPORT_INTERVAL_MILLIS = 120;
            // How long the viewport has to stay still before the exact position is resolved.
            const SCROLL_END_SETTLE_MILLIS = 160;
            // Exact relocations above this duration are logged (an-book-relocate-slow), so the
            // settle cost stays measurable on device after the scroll path was made cheap.
            const RELOCATE_SLOW_THRESHOLD_MILLIS = 8;
            // The stitch check reads scrollHeight, which forces a layout of the whole stitched
            // document; the check is gated on both time and travel so it cannot run every frame.
            const STITCH_CHECK_INTERVAL_MILLIS = 300;
            let bookFrame = 0;
            let lastReportTime = 0;
            let scrollEndTimer = 0;
            let lastStitchCheckTime = 0;
            let lastStitchScrollTop = -1;
            // One rAF per frame for everything scroll-driven: the neighbouring-chapter requests and
            // the throttled position report. Scroll events can fire several times per frame; without
            // this coalescing each one forced a layout (scrollHeight reads) and scheduled its own
            // expensive report, which is what made finger scrolling stutter.
            const pushBookFrameWork = function() {
              if (bookFrame !== 0) return;
              bookFrame = requestAnimationFrame(function() {
                bookFrame = 0;
                const now = Date.now();
                // scrollTop is a cheap read; the layout-forcing stitch check runs at most once per
                // half viewport of travel, far inside the 1.25-viewport stitch margin.
                const travelled = lastStitchScrollTop < 0 ||
                  Math.abs(viewport.scrollTop - lastStitchScrollTop) >= viewport.clientHeight * 0.5;
                if (travelled && now - lastStitchCheckTime >= STITCH_CHECK_INTERVAL_MILLIS) {
                  lastStitchCheckTime = now;
                  lastStitchScrollTop = viewport.scrollTop;
                  pushStitchRequests();
                }
                if (now - lastReportTime >= RELOCATE_REPORT_INTERVAL_MILLIS) {
                  lastReportTime = now;
                  reportCheapRelocated();
                }
              });
            };
            const reportBoundary = function(kind) {
              try {
                if (window.AnBookNative && typeof window.AnBookNative.onBoundary === 'function') {
                  window.AnBookNative.onBoundary($documentGeneration, kind);
                }
              } catch (_) {
                // The native renderer may have been detached while this frame was pending.
              }
            };
            const reportSectionMeasured = function(sectionNode) {
              const index = sectionIndexOf(sectionNode);
              if (index < 0) return;
              const count = totalCharCount(textNodesIn(sectionNode));
              if (count > 0) sectionCharCounts[index] = count;
              try {
                if (window.AnBookNative && typeof window.AnBookNative.onSectionMeasured === 'function') {
                  window.AnBookNative.onSectionMeasured(
                    $documentGeneration,
                    index,
                    sectionChapterOf(sectionNode),
                    count);
                }
              } catch (_) {
                // The native renderer may have been detached while this frame was pending.
              }
            };
            // A boundary report is a request for more content well before the edge, not a jump at
            // the edge: by the time the reader scrolls into the next chapter it is already part of
            // this document, so there is nothing to swap and nothing to jump over.
            let stitchForwardRequested = -1;
            let stitchBackwardRequested = -1;
            const pushStitchRequests = function() {
              const nodes = sectionNodes();
              if (nodes.length === 0) return;
              const lastSection = sectionIndexOf(nodes[nodes.length - 1]);
              const firstSection = sectionIndexOf(nodes[0]);
              if (isPaginated) {
                // The paged flow asks for the neighbouring chapter a few columns before the reader
                // turns into it, exactly like the scrolled flow asks a viewport ahead. Without this
                // the paged document held one chapter and the crossing was a document swap.
                const count = pageCount();
                const page = currentPage();
                if (count - page <= PAGE_STITCH_MARGIN && stitchForwardRequested !== lastSection) {
                  stitchForwardRequested = lastSection;
                  reportBoundary('end');
                }
                if (firstSection > 0 && page <= PAGE_STITCH_MARGIN &&
                  stitchBackwardRequested !== firstSection) {
                  stitchBackwardRequested = firstSection;
                  reportBoundary('start');
                }
                return;
              }
              const height = Math.max(1, viewport.clientHeight);
              const maximum = Math.max(0, viewport.scrollHeight - height);
              const lastIndex = lastSection;
              const firstIndex = firstSection;
              if (maximum - viewport.scrollTop <= height * 1.25 && stitchForwardRequested !== lastIndex) {
                stitchForwardRequested = lastIndex;
                reportBoundary('end');
              }
              if (firstIndex > 0 && viewport.scrollTop <= height * 0.5 && stitchBackwardRequested !== firstIndex) {
                stitchBackwardRequested = firstIndex;
                reportBoundary('start');
              }
            };
            viewport.addEventListener('scroll', function() {
              pushBookFrameWork();
              // While the finger is moving, only the cheap throttled report runs. Once the
              // viewport stays still for a moment, the exact caret-based position is resolved and
              // reported once, so the stored reading position stays exact.
              if (scrollEndTimer !== 0) {
                window.clearTimeout(scrollEndTimer);
                scrollEndTimer = 0;
              }
              scrollEndTimer = window.setTimeout(function() {
                scrollEndTimer = 0;
                reportRelocated();
              }, SCROLL_END_SETTLE_MILLIS);
            }, { passive: true });
            // The geometry has to survive orientation changes, reader-chrome padding changes and
            // font reflows, so it is reapplied and the current page re-clamped on every resize.
            applyPagedGeometry();
            window.addEventListener('resize', function() {
              if (!isPaginated) return;
              applyPagedGeometry();
              goToPage(pageIndex, 'INSTANT');
              pushRelocated();
            });
            const scrollToOffsetIn = function(sectionNode, charOffset) {
              const nodes = textNodesIn(sectionNode);
              const range = rangeAtOffset(nodes, charOffset);
              if (!range) return relocate();
              if (isPaginated) {
                goToPage(pageOfOffset(nodes, charOffset, content.getBoundingClientRect()), 'INSTANT');
              } else {
                const rect = rectOfRange(range);
                const viewportRect = viewport.getBoundingClientRect();
                viewport.scrollTop = Math.max(0, viewport.scrollTop + rect.top - viewportRect.top);
              }
              return relocate();
            };
            const goToCharOffset = function(charOffset, sectionIndex) {
              const node = resolveSectionNode(sectionIndex);
              if (!node) return relocate();
              return scrollToOffsetIn(node, charOffset);
            };
            // Reopening the book restores by fraction: the stored position was a fraction of an
            // estimated chapter length, and only this document knows the real one.
            const goToFraction = function(fraction, sectionIndex) {
              const node = resolveSectionNode(sectionIndex);
              if (!node) return relocate();
              const total = totalCharCount(textNodesIn(node));
              const safe = Math.max(0, Math.min(Number(fraction) || 0, 1));
              return scrollToOffsetIn(node, Math.round(safe * Math.max(0, total - 1)));
            };
            const buildSectionNode = function(sectionIndex, chapterId, html) {
              const node = document.createElement('section');
              node.className = 'an-book-section';
              node.setAttribute('data-an-index', String(sectionIndex));
              node.setAttribute('data-an-chapter', String(chapterId));
              node.innerHTML = html;
              return node;
            };
            // Re-anchors the paged document on the position it was showing.
            //
            // Inserting or restyling a section relays out every column, so the page the reader was
            // on now shows different text. The locator survives that: the position is re-resolved
            // from (section, char offset) after the new geometry settled.
            const restorePagedAnchor = function(anchor) {
              if (!isPaginated || !anchor) return;
              invalidatePagedCaches();
              applyPagedGeometry();
              const node = resolveSectionNode(anchor.sectionIndex);
              if (node) scrollToOffsetIn(node, anchor.charOffset);
            };
            // Images decode after insertion, so a section added above the viewport keeps growing.
            // Compensating every height change it causes is what holds the reading position still.
            const holdPositionWhileLoading = function(node) {
              const images = node.querySelectorAll('img');
              if (images.length === 0) return;
              if (isPaginated) {
                // A decoded image changes the column count, so the paged document re-resolves its
                // locator instead of compensating pixels it does not scroll by.
                const repage = function() {
                  restorePagedAnchor(locationAtViewportStart());
                  pushRelocated();
                };
                for (let index = 0; index < images.length; index += 1) {
                  images[index].addEventListener('load', repage, { once: true });
                  images[index].addEventListener('error', repage, { once: true });
                }
                return;
              }
              let last = node.offsetHeight;
              const fix = function() {
                const height = node.offsetHeight;
                const delta = height - last;
                if (delta === 0) return;
                last = height;
                if (node.getBoundingClientRect().top < viewport.getBoundingClientRect().top) {
                  viewport.scrollTop = Math.max(0, viewport.scrollTop + delta);
                }
              };
              for (let index = 0; index < images.length; index += 1) {
                images[index].addEventListener('load', fix, { once: true });
                images[index].addEventListener('error', fix, { once: true });
              }
            };
            const appendSection = function(sectionIndex, chapterId, html) {
              if (sectionNodeAt(sectionIndex)) return true;
              const anchor = isPaginated ? locationAtViewportStart() : null;
              const node = buildSectionNode(sectionIndex, chapterId, html);
              // The paged flow measures its column count from a sentinel that has to stay last.
              if (sentinel && sentinel.parentNode === content) {
                content.insertBefore(node, sentinel);
              } else {
                content.appendChild(node);
              }
              restorePagedAnchor(anchor);
              holdPositionWhileLoading(node);
              reportSectionMeasured(node);
              stitchForwardRequested = -1;
              pushRelocated();
              return true;
            };
            const prependSection = function(sectionIndex, chapterId, html) {
              if (sectionNodeAt(sectionIndex)) return true;
              const anchor = isPaginated ? locationAtViewportStart() : null;
              const node = buildSectionNode(sectionIndex, chapterId, html);
              const before = viewport.scrollHeight;
              content.insertBefore(node, content.firstChild);
              if (isPaginated) {
                // Every column shifted right by the inserted chapter, so the page index is restored
                // from the locator rather than patched with a pixel delta.
                restorePagedAnchor(anchor);
              } else {
                const after = viewport.scrollHeight;
                viewport.scrollTop = Math.max(0, viewport.scrollTop + (after - before));
              }
              holdPositionWhileLoading(node);
              reportSectionMeasured(node);
              stitchBackwardRequested = -1;
              pushRelocated();
              return true;
            };
            const replaceSection = function(sectionIndex, chapterId, html) {
              const node = sectionNodeAt(sectionIndex);
              if (!node) return false;
              const anchor = isPaginated ? locationAtViewportStart() : null;
              const above = !isPaginated &&
                node.getBoundingClientRect().top < viewport.getBoundingClientRect().top;
              const before = viewport.scrollHeight;
              const fresh = buildSectionNode(sectionIndex, chapterId, html);
              node.parentNode.replaceChild(fresh, node);
              if (isPaginated) {
                restorePagedAnchor(anchor);
              } else {
                const after = viewport.scrollHeight;
                if (above) viewport.scrollTop = Math.max(0, viewport.scrollTop + (after - before));
              }
              holdPositionWhileLoading(fresh);
              reportSectionMeasured(fresh);
              pushRelocated();
              return true;
            };
            const removeSection = function(sectionIndex) {
              const node = sectionNodeAt(sectionIndex);
              if (!node) return true;
              delete sectionCharCounts[sectionIndex];
              const anchor = isPaginated ? locationAtViewportStart() : null;
              // Never drop the section the reader is looking at: it is the anchor everything else
              // is measured against.
              if (isPaginated && anchor && anchor.sectionIndex === sectionIndex) return false;
              const above = !isPaginated &&
                node.getBoundingClientRect().top < viewport.getBoundingClientRect().top;
              const before = viewport.scrollHeight;
              node.parentNode.removeChild(node);
              if (isPaginated) {
                restorePagedAnchor(anchor);
              } else {
                const after = viewport.scrollHeight;
                if (above) viewport.scrollTop = Math.max(0, viewport.scrollTop - (before - after));
              }
              stitchForwardRequested = -1;
              stitchBackwardRequested = -1;
              return true;
            };
            const waitForImage = function(image) {
              if (image.complete) {
                return typeof image.decode === 'function'
                  ? image.decode().catch(function() { return undefined; })
                  : Promise.resolve();
              }
              return new Promise(function(resolve) {
                let settled = false;
                const settle = function() {
                  if (settled) return;
                  settled = true;
                  resolve();
                };
                image.addEventListener('load', settle, { once: true });
                image.addEventListener('error', settle, { once: true });
                window.setTimeout(settle, 5000);
              }).then(function() {
                return typeof image.decode === 'function'
                  ? image.decode().catch(function() { return undefined; })
                  : undefined;
              });
            };
            const images = Array.from(content.querySelectorAll('img'));
            const ready = Promise.all(images.map(waitForImage))
              .then(function() {
                return new Promise(function(resolve) {
                  requestAnimationFrame(function() {
                    requestAnimationFrame(resolve);
                  });
                });
              })
              .then(function() {
                applyPagedGeometry();
                // Ask for the neighbouring chapters immediately: a chapter shorter than the prefetch
                // margin must not wait for a scroll event to become continuous.
                pushStitchRequests();
                // Mirrored into logcat by the renderer's WebChromeClient so the real layout numbers
                // of the book document can be inspected with: adb logcat -s NovelBookWebView
                try {
                  const contentStyle = window.getComputedStyle(content);
                  const rootStyle = window.getComputedStyle(document.documentElement);
                  console.log('an-book-diag ' + JSON.stringify({
                    flow: document.documentElement.dataset.anBookFlow,
                    vw: viewport.clientWidth,
                    vh: viewport.clientHeight,
                    cw: content.offsetWidth,
                    ch: content.offsetHeight,
                    csw: content.scrollWidth,
                    csh: content.scrollHeight,
                    pitch: pagePitch,
                    pages: pageCount(),
                    page: currentPage(),
                    height: contentStyle.height,
                    padding: contentStyle.padding,
                    columnWidth: contentStyle.columnWidth,
                    columnCount: contentStyle.columnCount,
                    columnGap: contentStyle.columnGap,
                    columnFill: contentStyle.columnFill,
                    transform: contentStyle.transform,
                    visibility: contentStyle.visibility,
                    opacity: contentStyle.opacity,
                    bodyH: document.body.clientHeight,
                    rootH: document.documentElement.clientHeight,
                    rootBg: String(rootStyle.backgroundImage).slice(0, 60),
                    kids: content.childElementCount,
                    chars: (content.textContent || '').trim().length
                  }));
                } catch (_) {
                  // Diagnostics must never break the document.
                }
                // Cache the resident sections' text lengths once, so the cheap scroll reports never
                // have to walk the text nodes again.
                const residentNodes = sectionNodes();
                for (let index = 0; index < residentNodes.length; index += 1) {
                  const residentNode = residentNodes[index];
                  const residentIndex = sectionIndexOf(residentNode);
                  if (residentIndex >= 0) {
                    const residentCount = totalCharCount(textNodesIn(residentNode));
                    if (residentCount > 0) sectionCharCounts[residentIndex] = residentCount;
                  }
                }
                const payload = JSON.stringify({
                  kind: 'ready',
                  pageCount: pageCount(),
                  currentPage: currentPage(),
                  // The ready payload only feeds progress bookkeeping, so the cheap geometry-based
                  // offset is enough here: the exact caret resolution would force a full layout at
                  // open time, which is what made page mode appear to load forever on long sections.
                  charOffset: cheapCharOffsetAtViewportStart(),
                  charCount: totalCharCount(textNodes())
                });
                try {
                  if (window.AnBookNative && typeof window.AnBookNative.onReady === 'function') {
                    window.AnBookNative.onReady($documentGeneration, payload);
                  }
                } catch (_) {
                  // A stale document must not keep the new renderer open waiting for its callback.
                }
                return payload;
              });
            // Auto-scroll runs as a requestAnimationFrame loop inside the document instead of an
            // evaluateJavascript round trip per frame from Kotlin. setAutoScroll only updates the
            // per-frame speed; the loop advances the viewport itself, stops at the document end,
            // and gives up if no sync arrives for a while (the Kotlin loop pauses while the reader
            // UI is visible, and cancelled coroutines have no chance to say goodbye).
            const AUTO_SCROLL_KEEPALIVE_MILLIS = 1000;
            let autoScrollFrame = 0;
            let autoScrollPxPerFrame = 0;
            let autoScrollLastSync = 0;
            const autoScrollStep = function() {
              autoScrollFrame = 0;
              if (autoScrollPxPerFrame <= 0 || isPaginated) return;
              if (Date.now() - autoScrollLastSync > AUTO_SCROLL_KEEPALIVE_MILLIS) {
                autoScrollPxPerFrame = 0;
                return;
              }
              const maximum = Math.max(0, viewport.scrollHeight - viewport.clientHeight);
              if (viewport.scrollTop >= maximum - 1) {
                autoScrollPxPerFrame = 0;
                return;
              }
              viewport.scrollTop = Math.min(maximum, viewport.scrollTop + autoScrollPxPerFrame);
              autoScrollFrame = requestAnimationFrame(autoScrollStep);
            };
            const setAutoScroll = function(pxPerFrame) {
              const speed = Math.max(0, Math.round(Number(pxPerFrame) || 0));
              autoScrollPxPerFrame = speed;
              autoScrollLastSync = Date.now();
              if (speed <= 0) {
                if (autoScrollFrame !== 0) {
                  window.cancelAnimationFrame(autoScrollFrame);
                  autoScrollFrame = 0;
                }
                return;
              }
              if (autoScrollFrame === 0) {
                autoScrollFrame = requestAnimationFrame(autoScrollStep);
              }
            };
            window.__anBookEngine = Object.freeze({
              ready: ready,
              goTo: goToCharOffset,
              goToFraction: goToFraction,
              relocate: relocate,
              appendSection: appendSection,
              prependSection: prependSection,
              replaceSection: replaceSection,
              removeSection: removeSection,
              next: function(styleName) {
                if (isPaginated) {
                  const targetPage = currentPage() + 1;
                  if (targetPage >= pageCount()) {
                    // Ask for the next chapter before reporting the end, so the engine can stitch it
                    // in and the reader turns one more column instead of swapping the document.
                    pushStitchRequests();
                    return JSON.stringify({ kind: 'end' });
                  }
                  goToPage(targetPage, styleName);
                  pushStitchRequests();
                  return relocate();
                }
                const maximum = Math.max(0, viewport.scrollHeight - viewport.clientHeight);
                if (viewport.scrollTop >= maximum - 1) return JSON.stringify({ kind: 'end' });
                viewport.scrollTop = Math.min(maximum, viewport.scrollTop + viewport.clientHeight * 0.9);
                return relocate();
              },
              previous: function(styleName) {
                if (isPaginated) {
                  const targetPage = currentPage() - 1;
                  if (targetPage < 0) {
                    pushStitchRequests();
                    return JSON.stringify({ kind: 'start' });
                  }
                  goToPage(targetPage, styleName);
                  pushStitchRequests();
                  return relocate();
                }
                if (viewport.scrollTop <= 1) return JSON.stringify({ kind: 'start' });
                viewport.scrollTop = Math.max(0, viewport.scrollTop - viewport.clientHeight * 0.9);
                return relocate();
              },
              canScrollForward: function() {
                if (!viewport) return false;
                if (isPaginated) return currentPage() < pageCount() - 1;
                return viewport.scrollTop < (viewport.scrollHeight - viewport.clientHeight - 1);
              },
              scrollBy: function(px) {
                if (isPaginated || !viewport || !px) return 0;
                const before = viewport.scrollTop;
                const maximum = Math.max(0, viewport.scrollHeight - viewport.clientHeight);
                viewport.scrollTop = Math.max(0, Math.min(maximum, before + px));
                // The scroll event listener coalesces the stitch check and the position report
                // into one rAF, so this stays cheap even when called every frame.
                pushBookFrameWork();
                return Math.round(viewport.scrollTop - before);
              },
              setAutoScroll: setAutoScroll,
              autoScrollActive: function() { return autoScrollPxPerFrame > 0; },
              // Applies reader styles to the open document.
              //
              // Changing a setting used to rebuild the whole document, which threw away the
              // rendered book and landed the reader at the top of the chapter. Swapping the text of
              // one style element keeps the document alive; the position is then re-resolved from
              // the locator, because new type metrics mean new columns and new scroll offsets.
              applyReaderCss: function(css, sectionIndex, charOffset) {
                const element = document.getElementById('an-book-reader-css');
                if (element) element.textContent = typeof css === 'string' ? css : '';
                if (isPaginated) {
                  invalidatePagedCaches();
                  applyPagedGeometry();
                }
                const node = resolveSectionNode(sectionIndex);
                if (node) scrollToOffsetIn(node, Math.max(0, charOffset || 0));
                pushStitchRequests();
                return relocate();
              }
            });
          })();
        </script>
    """.trimIndent()
    return """
        <!doctype html>
        <html id="an-book-root" data-an-book-flow="${flow.name.lowercase()}">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
          <!-- Reader styles live alone in this element so a setting change can replace them without
               rebuilding the document. It stays ahead of the layout rules below, which keep the
               same authority over the book geometry as before. -->
          <style id="an-book-reader-css">$readerCss</style>
          <style>
            html, body {
              box-sizing: border-box;
              width: 100%;
              height: 100%;
              margin: 0;
              padding: 0;
              overflow: hidden;
              background-color: transparent !important;
              background-image: none !important;
            }
            $flowCss
            html#an-book-root,
            html#an-book-root > body {
              height: 100% !important;
              min-height: 100% !important;
              max-height: 100% !important;
              background-color: transparent !important;
              background-image: none !important;
            }
            #an-book-atmosphere {
              display: none !important;
            }
          </style>
        </head>
        <body data-an-section="${document.sectionIndex}" data-an-chapter="${document.chapterId}">
          <main id="an-book-viewport">
            <article id="an-book-content">$sectionMarkup</article>
          </main>
          $engineScript
        </body>
        </html>
    """.trimIndent()
}
