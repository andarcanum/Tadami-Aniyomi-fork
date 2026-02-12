package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NovelJsDomStoreTest {

    private lateinit var store: NovelJsDomStore

    @BeforeEach
    fun setUp() {
        store = NovelJsDomStore()
    }

    @Nested
    inner class LoadDocument {
        @Test
        fun `returns a positive handle`() {
            val handle = store.loadDocument("<div>hello</div>")
            handle shouldBeGreaterThan 0
        }

        @Test
        fun `getText on root returns all text`() {
            val root = store.loadDocument("<div>hello <span>world</span></div>")
            store.getText(root) shouldBe "hello world"
        }
    }

    @Nested
    inner class Select {
        @Test
        fun `can select elements from head`() {
            val root = store.loadDocument(
                """
                <html>
                  <head>
                    <meta name="description" content="meta-from-head" />
                    <script id="it-astro-state">["ok"]</script>
                  </head>
                  <body><h1>Title</h1></body>
                </html>
                """.trimIndent(),
            )

            val meta = store.select(root, "meta[name=description]")
            meta.size shouldBe 1
            store.getAttr(meta[0], "content") shouldBe "meta-from-head"

            val astro = store.select(root, "#it-astro-state")
            astro.size shouldBe 1
            store.getHtml(astro[0]) shouldBe "[\"ok\"]"
        }

        @Test
        fun `finds elements by CSS selector`() {
            val root = store.loadDocument("<ul><li class='a'>1</li><li class='b'>2</li></ul>")
            val lis = store.select(root, "li")
            lis.size shouldBe 2
            store.getText(lis[0]) shouldBe "1"
            store.getText(lis[1]) shouldBe "2"
        }

        @Test
        fun `finds elements by class`() {
            val root = store.loadDocument("<div><p class='target'>yes</p><p>no</p></div>")
            val found = store.select(root, ".target")
            found.size shouldBe 1
            store.getText(found[0]) shouldBe "yes"
        }

        @Test
        fun `returns empty array for no matches`() {
            val root = store.loadDocument("<div>hello</div>")
            store.select(root, ".nonexistent").size shouldBe 0
        }

        @Test
        fun `returns empty array for invalid handle`() {
            store.select(99999, "div").size shouldBe 0
        }
    }

    @Nested
    inner class Parent {
        @Test
        fun `returns parent handle`() {
            val root = store.loadDocument("<div><ul><li class='target'>item</li></ul></div>")
            val li = store.select(root, ".target")
            li.size shouldBe 1

            val parentHandle = store.parent(li[0])
            parentHandle shouldNotBe -1
            store.getTagName(parentHandle) shouldBe "ul"
        }

        @Test
        fun `returns grandparent via chaining`() {
            val root = store.loadDocument("<div id='gp'><ul><li class='target'>item</li></ul></div>")
            val li = store.select(root, ".target")[0]
            val ul = store.parent(li)
            val div = store.parent(ul)
            div shouldNotBe -1
            store.getTagName(div) shouldBe "div"
            store.getAttr(div, "id") shouldBe "gp"
        }

        @Test
        fun `returns -1 for root body`() {
            val root = store.loadDocument("<div>hello</div>")
            store.parent(root) shouldBe -1
        }

        @Test
        fun `returns -1 for invalid handle`() {
            store.parent(99999) shouldBe -1
        }
    }

    @Nested
    inner class Children {
        @Test
        fun `returns direct children only`() {
            val root = store.loadDocument("<div><ul><li>1</li><li>2</li></ul></div>")
            val ul = store.select(root, "ul")[0]
            val kids = store.children(ul, null)
            kids.size shouldBe 2
            store.getText(kids[0]) shouldBe "1"
            store.getText(kids[1]) shouldBe "2"
        }

        @Test
        fun `does not return grandchildren`() {
            val root = store.loadDocument("<div><ul><li><span>deep</span></li></ul></div>")
            val div = store.select(root, "div")[0]
            val kids = store.children(div, null)
            kids.size shouldBe 1
            store.getTagName(kids[0]) shouldBe "ul"
        }

        @Test
        fun `filters by selector`() {
            val root = store.loadDocument("<ul><li class='a'>1</li><li class='b'>2</li><li class='a'>3</li></ul>")
            val ul = store.select(root, "ul")[0]
            val filtered = store.children(ul, ".a")
            filtered.size shouldBe 2
            store.getText(filtered[0]) shouldBe "1"
            store.getText(filtered[1]) shouldBe "3"
        }
    }

    @Nested
    inner class NextAndPrev {
        @Test
        fun `next returns next sibling`() {
            val root = store.loadDocument("<div><p id='a'>A</p><p id='b'>B</p><p id='c'>C</p></div>")
            val a = store.select(root, "#a")[0]
            val nextHandle = store.next(a, null)
            nextHandle shouldNotBe -1
            store.getAttr(nextHandle, "id") shouldBe "b"
        }

        @Test
        fun `next with selector skips non-matching`() {
            val root = store.loadDocument("<div><p id='a'>A</p><p id='b'>B</p><p id='c' class='target'>C</p></div>")
            val a = store.select(root, "#a")[0]
            val nextTarget = store.next(a, ".target")
            nextTarget shouldNotBe -1
            store.getAttr(nextTarget, "id") shouldBe "c"
        }

        @Test
        fun `next returns -1 for last sibling`() {
            val root = store.loadDocument("<div><p id='only'>A</p></div>")
            val p = store.select(root, "#only")[0]
            store.next(p, null) shouldBe -1
        }

        @Test
        fun `prev returns previous sibling`() {
            val root = store.loadDocument("<div><p id='a'>A</p><p id='b'>B</p></div>")
            val b = store.select(root, "#b")[0]
            val prevHandle = store.prev(b, null)
            prevHandle shouldNotBe -1
            store.getAttr(prevHandle, "id") shouldBe "a"
        }

        @Test
        fun `prev returns -1 for first sibling`() {
            val root = store.loadDocument("<div><p id='first'>A</p><p>B</p></div>")
            val first = store.select(root, "#first")[0]
            store.prev(first, null) shouldBe -1
        }
    }

    @Nested
    inner class NextAllAndPrevAll {
        @Test
        fun `nextAll returns all following siblings`() {
            val root = store.loadDocument("<ul><li>1</li><li id='start'>2</li><li>3</li><li>4</li></ul>")
            val start = store.select(root, "#start")[0]
            val following = store.nextAll(start, null)
            following.size shouldBe 2
            store.getText(following[0]) shouldBe "3"
            store.getText(following[1]) shouldBe "4"
        }

        @Test
        fun `prevAll returns all preceding siblings`() {
            val root = store.loadDocument("<ul><li>1</li><li>2</li><li id='end'>3</li></ul>")
            val end = store.select(root, "#end")[0]
            val preceding = store.prevAll(end, null)
            preceding.size shouldBe 2
        }

        @Test
        fun `nextAll with selector filters results`() {
            val root = store.loadDocument("<ul><li id='s'>S</li><li class='x'>A</li><li>B</li><li class='x'>C</li></ul>")
            val s = store.select(root, "#s")[0]
            val filtered = store.nextAll(s, ".x")
            filtered.size shouldBe 2
        }
    }

    @Nested
    inner class Siblings {
        @Test
        fun `returns all siblings excluding self`() {
            val root = store.loadDocument("<ul><li>1</li><li id='me'>2</li><li>3</li></ul>")
            val me = store.select(root, "#me")[0]
            val sibs = store.siblings(me, null)
            sibs.size shouldBe 2
            store.getText(sibs[0]) shouldBe "1"
            store.getText(sibs[1]) shouldBe "3"
        }

        @Test
        fun `siblings with selector filters`() {
            val root = store.loadDocument("<ul><li class='a'>1</li><li id='me'>2</li><li class='a'>3</li><li>4</li></ul>")
            val me = store.select(root, "#me")[0]
            val filtered = store.siblings(me, ".a")
            filtered.size shouldBe 2
        }
    }

    @Nested
    inner class Closest {
        @Test
        fun `walks ancestors to find matching element`() {
            val root = store.loadDocument("<div class='outer'><div class='inner'><span id='target'>text</span></div></div>")
            val target = store.select(root, "#target")[0]
            val closest = store.closest(target, ".outer")
            closest shouldNotBe -1
            store.hasClass(closest, "outer") shouldBe true
        }

        @Test
        fun `returns self if it matches`() {
            val root = store.loadDocument("<div class='match'><span>x</span></div>")
            val div = store.select(root, ".match")[0]
            val closest = store.closest(div, ".match")
            closest shouldBe div
        }

        @Test
        fun `returns -1 when no ancestor matches`() {
            val root = store.loadDocument("<div><span id='s'>x</span></div>")
            val s = store.select(root, "#s")[0]
            store.closest(s, ".nonexistent") shouldBe -1
        }
    }

    @Nested
    inner class Contents {
        @Test
        fun `includes text nodes`() {
            val root = store.loadDocument("<div>text before<span>inner</span>text after</div>")
            val div = store.select(root, "div")[0]
            val contents = store.contents(div)
            contents.size shouldBeGreaterThan 1
        }
    }

    @Nested
    inner class Predicates {
        @Test
        fun `is_ returns true for matching selector`() {
            val root = store.loadDocument("<div class='foo bar'>x</div>")
            val div = store.select(root, "div")[0]
            store.is_(div, ".foo") shouldBe true
            store.is_(div, ".bar") shouldBe true
            store.is_(div, ".baz") shouldBe false
        }

        @Test
        fun `has returns true when descendants match`() {
            val root = store.loadDocument("<div><ul><li class='item'>x</li></ul></div>")
            val div = store.select(root, "div")[0]
            store.has(div, ".item") shouldBe true
            store.has(div, ".nonexistent") shouldBe false
        }
    }

    @Nested
    inner class ContentAccessors {
        @Test
        fun `getHtml returns innerHTML`() {
            val root = store.loadDocument("<div><b>bold</b></div>")
            val div = store.select(root, "div")[0]
            store.getHtml(div) shouldBe "<b>bold</b>"
        }

        @Test
        fun `getOuterHtml includes the element itself`() {
            val root = store.loadDocument("<div><b>bold</b></div>")
            val b = store.select(root, "b")[0]
            store.getOuterHtml(b) shouldBe "<b>bold</b>"
        }

        @Test
        fun `getAttr returns attribute value`() {
            val root = store.loadDocument("<a href='/path' class='link'>click</a>")
            val a = store.select(root, "a")[0]
            store.getAttr(a, "href") shouldBe "/path"
            store.getAttr(a, "class") shouldBe "link"
        }

        @Test
        fun `getAttr returns null for missing attribute`() {
            val root = store.loadDocument("<div>x</div>")
            val div = store.select(root, "div")[0]
            store.getAttr(div, "data-missing") shouldBe null
        }

        @Test
        fun `getAllAttrs returns all attributes`() {
            val root = store.loadDocument("<a href='/path' class='link' id='a1'>click</a>")
            val a = store.select(root, "a")[0]
            val attrs = store.getAllAttrs(a)
            attrs["href"] shouldBe "/path"
            attrs["class"] shouldBe "link"
            attrs["id"] shouldBe "a1"
        }

        @Test
        fun `hasClass checks class presence`() {
            val root = store.loadDocument("<div class='foo bar baz'>x</div>")
            val div = store.select(root, "div")[0]
            store.hasClass(div, "foo") shouldBe true
            store.hasClass(div, "bar") shouldBe true
            store.hasClass(div, "missing") shouldBe false
        }

        @Test
        fun `getData reads data- attributes`() {
            val root = store.loadDocument("<div data-id='123' data-name='test'>x</div>")
            val div = store.select(root, "div")[0]
            store.getData(div, "id") shouldBe "123"
            store.getData(div, "name") shouldBe "test"
            store.getData(div, "missing") shouldBe null
        }

        @Test
        fun `getVal reads value of input`() {
            val root = store.loadDocument("<input value='hello'/>")
            val input = store.select(root, "input")[0]
            store.getVal(input) shouldBe "hello"
        }

        @Test
        fun `getTagName returns lowercase tag`() {
            val root = store.loadDocument("<DIV><SPAN>x</SPAN></DIV>")
            val span = store.select(root, "span")[0]
            store.getTagName(span) shouldBe "span"
        }
    }

    @Nested
    inner class Mutations {
        @Test
        fun `remove removes element from tree`() {
            val root = store.loadDocument("<div><p id='keep'>keep</p><p id='remove'>remove</p></div>")
            val toRemove = store.select(root, "#remove")[0]
            store.remove(toRemove)
            store.select(root, "#remove").size shouldBe 0
            store.select(root, "#keep").size shouldBe 1
        }

        @Test
        fun `addClass adds class to element`() {
            val root = store.loadDocument("<div>x</div>")
            val div = store.select(root, "div")[0]
            store.addClass(div, "new-class")
            store.hasClass(div, "new-class") shouldBe true
        }

        @Test
        fun `removeClass removes class from element`() {
            val root = store.loadDocument("<div class='a b'>x</div>")
            val div = store.select(root, "div")[0]
            store.removeClass(div, "a")
            store.hasClass(div, "a") shouldBe false
            store.hasClass(div, "b") shouldBe true
        }
    }

    @Nested
    inner class HandleManagement {
        @Test
        fun `same element returns same handle`() {
            val root = store.loadDocument("<div><p>x</p></div>")
            val p1 = store.select(root, "p")
            val p2 = store.select(root, "p")
            p1.size shouldBe 1
            p2.size shouldBe 1
            p1[0] shouldBe p2[0]
        }

        @Test
        fun `releaseAll clears all handles`() {
            val root = store.loadDocument("<div><p>x</p></div>")
            store.select(root, "p").size shouldBe 1
            store.releaseAll()
            // After release, old handles are invalid
            store.getText(root) shouldBe ""
        }

        @Test
        fun `release frees single handle`() {
            val root = store.loadDocument("<div><p id='a'>x</p></div>")
            val p = store.select(root, "#a")[0]
            store.getText(p) shouldBe "x"
            store.release(p)
            store.getText(p) shouldBe ""
        }
    }

    @Nested
    inner class CheerioLikePatterns {
        @Test
        fun `find then parent chain works`() {
            val html = """
                <div class="novel-info">
                    <div class="chapters">
                        <table>
                            <tbody>
                                <tr class="chapter_row">
                                    <td><a href="/ch/1">Chapter 1</a></td>
                                </tr>
                                <tr class="chapter_row">
                                    <td><a href="/ch/2">Chapter 2</a></td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            """.trimIndent()
            val root = store.loadDocument(html)

            // Plugin pattern: find links, get parent row, get chapter name
            val links = store.select(root, "tr.chapter_row a")
            links.size shouldBe 2

            val firstLink = links[0]
            store.getAttr(firstLink, "href") shouldBe "/ch/1"
            store.getText(firstLink) shouldBe "Chapter 1"

            // Navigate up: a -> td -> tr.chapter_row
            val td = store.parent(firstLink)
            td shouldNotBe -1
            store.getTagName(td) shouldBe "td"

            val tr = store.parent(td)
            tr shouldNotBe -1
            store.hasClass(tr, "chapter_row") shouldBe true
        }

        @Test
        fun `next sibling navigation for chapter lists`() {
            val html = """
                <div>
                    <h3>Volume 1</h3>
                    <ul class="ch-list">
                        <li><a href="/ch/1">Ch 1</a></li>
                        <li><a href="/ch/2">Ch 2</a></li>
                    </ul>
                    <h3>Volume 2</h3>
                    <ul class="ch-list">
                        <li><a href="/ch/3">Ch 3</a></li>
                    </ul>
                </div>
            """.trimIndent()
            val root = store.loadDocument(html)

            val headings = store.select(root, "h3")
            headings.size shouldBe 2

            // Get chapter list after each heading via next sibling
            val list1 = store.next(headings[0], "ul")
            list1 shouldNotBe -1
            val chs1 = store.select(list1, "a")
            chs1.size shouldBe 2

            val list2 = store.next(headings[1], "ul")
            list2 shouldNotBe -1
            val chs2 = store.select(list2, "a")
            chs2.size shouldBe 1
            store.getText(chs2[0]) shouldBe "Ch 3"
        }

        @Test
        fun `children filter pattern common in plugins`() {
            val html = """
                <div id="info">
                    <span class="label">Author:</span>
                    <a href="/author/1">John Doe</a>
                    <span class="label">Status:</span>
                    <span class="value">Ongoing</span>
                </div>
            """.trimIndent()
            val root = store.loadDocument(html)
            val info = store.select(root, "#info")[0]

            val links = store.children(info, "a")
            links.size shouldBe 1
            store.getText(links[0]) shouldBe "John Doe"
        }
    }
}
