package eu.kanade.presentation.easteregg.crystal

import android.content.res.AssetManager
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Immutable authored geometry; each Canvas painter owns its cached Android paths. */
internal class CrystalLivingArtwork private constructor(
    val root: CrystalLivingSvgNode,
    val ids: Map<String, CrystalLivingSvgNode>,
    val viewBox: CrystalLivingViewBox,
) {
    val nodes: List<CrystalLivingSvgNode> = buildList {
        fun visit(node: CrystalLivingSvgNode) {
            add(node)
            node.children.forEach(::visit)
        }
        visit(root)
    }

    companion object {
        const val ASSET_NAME = "easteregg/crystal/living_corner_v8.svg"
        private val cacheLock = Any()

        @Volatile
        private var cachedArtwork: CrystalLivingArtwork? = null

        fun cachedOrNull(): CrystalLivingArtwork? = cachedArtwork

        fun loadCached(assets: AssetManager): CrystalLivingArtwork {
            cachedArtwork?.let { return it }
            return synchronized(cacheLock) {
                cachedArtwork ?: assets.open(ASSET_NAME).use(::load).also { cachedArtwork = it }
            }
        }

        val supportedTags = setOf(
            "svg", "g", "defs", "title", "desc", "path", "circle", "ellipse", "rect", "polygon",
            "linearGradient", "radialGradient", "stop", "clipPath",
        )

        fun load(input: InputStream): CrystalLivingArtwork {
            val bytes = input.readBytes()
            val source = bytes.toString(Charsets.UTF_8)
            require(!source.contains('\u0000') && !source.contains("<!DOCTYPE", ignoreCase = true)) {
                "Only local UTF-8 SVG documents without a doctype are allowed"
            }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw SAXException("External living scene entities are not allowed") }
            }
            val document = builder.parse(ByteArrayInputStream(bytes))
            val ids = linkedMapOf<String, CrystalLivingSvgNode>()
            var sequence = 0
            fun parse(element: Element): CrystalLivingSvgNode {
                val tag = element.localName ?: element.tagName
                require(element.namespaceURI == null || element.namespaceURI == "http://www.w3.org/2000/svg") {
                    "Unsupported living scene namespace"
                }
                require(tag in supportedTags) { "Unsupported living scene tag: " + tag }
                val attributes = buildMap {
                    for (index in 0 until element.attributes.length) {
                        val attribute = element.attributes.item(index)
                        put(attribute.nodeName, attribute.nodeValue)
                    }
                }
                val children = buildList {
                    for (index in 0 until element.childNodes.length) {
                        val child = element.childNodes.item(index)
                        if (child is Element) add(parse(child))
                    }
                }
                val node = CrystalLivingSvgNode(sequence++, tag, attributes, children)
                attributes["id"]?.let {
                    require(ids.put(it, node) == null) { "Duplicate living scene id: " + it }
                }
                return node
            }
            val root = parse(document.documentElement)
            require(root.tag == "svg") { "Expected a living SVG root" }
            val coordinates = requireNotNull(root.attributes["viewBox"]) { "Missing living viewBox" }
                .trim().split(Regex("""[\s,]+"""))
            require(coordinates.size == 4) { "A living viewBox needs four coordinates" }
            val values = coordinates.map { requireNotNull(it.toFloatOrNull()) { "Invalid living viewBox coordinate" } }
            val viewBox = CrystalLivingViewBox(values[0], values[1], values[2], values[3])
            val artwork = CrystalLivingArtwork(root, ids, viewBox)
            val urls = Regex("""url\(([^)]*)\)""")
            artwork.nodes.forEach { node ->
                node.attributes.values.forEach { value ->
                    urls.findAll(value).forEach { match ->
                        val target = match.groupValues[1].trim().trim('"', '\'')
                        require(target.startsWith("#")) { "External living scene paints are not allowed" }
                        require(ids.containsKey(target.removePrefix("#"))) {
                            "Missing living scene reference: " + target
                        }
                    }
                }
            }
            return artwork
        }
    }
}

/** Intrinsic authored camera bounds; negative origins never alter body/toy world coordinates. */
internal data class CrystalLivingViewBox(val x: Float, val y: Float, val width: Float, val height: Float) {
    init {
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() && width > 0f && height > 0f)
    }

    fun fit(widthPx: Float, heightPx: Float): CrystalLivingViewport {
        val scale = minOf(widthPx / width, heightPx / height)
        return CrystalLivingViewport(
            scale = scale,
            offsetX = (widthPx - width * scale) / 2f - x * scale,
            offsetY = (heightPx - height * scale) / 2f - y * scale,
        )
    }
}

internal data class CrystalLivingViewport(val scale: Float, val offsetX: Float, val offsetY: Float)

internal data class CrystalLivingSvgNode(
    val index: Int,
    val tag: String,
    val attributes: Map<String, String>,
    val children: List<CrystalLivingSvgNode>,
) {
    val classes: Set<String> = attributes["class"].orEmpty().split(Regex("""\s+""")).filter { it.isNotEmpty() }.toSet()

    // Inline authored presentation properties and custom ambient metadata are parsed once at load.
    val properties: Map<String, String> = attributes + attributes["style"].orEmpty().split(';').mapNotNull {
        val colon = it.indexOf(':')
        if (colon < 0) null else it.substring(0, colon).trim() to it.substring(colon + 1).trim()
    }.toMap()
    fun number(name: String, fallback: Float = 0f): Float =
        properties[name]?.removeSuffix("px")?.toFloatOrNull() ?: fallback
}
