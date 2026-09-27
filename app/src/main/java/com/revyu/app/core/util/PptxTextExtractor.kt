package com.revyu.app.core.util

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Extracts plain text from a PowerPoint Presentation (.pptx) file without third-party OOXML libraries.
 *
 * A .pptx is a zip archive. As in Microsoft's MarkItDown converter, we extract text solely from
 * the presentation's slides and their corresponding notes slides. We explicitly NEVER walk
 * slideLayouts, slideMasters, or themes to avoid duplicating layout placeholders, theme names,
 * and master footers across the extracted material.
 *
 * Slide order is determined by reading <p:sldIdLst> in ppt/presentation.xml and resolving each
 * relationship ID via ppt/_rels/presentation.xml.rels. Numeric filename sorting (e.g. slide1, slide2)
 * is explicitly avoided because reordered or deleted slides in PowerPoint result in non-sequential
 * or misordered filenames.
 */
class PptxTextExtractor {

    data class PptxExtractionResult(
        val text: String,
        val textlessSlideNumbers: List<Int> = emptyList(),
        val warning: String? = null
    )

    private data class ShapeBlock(
        val x: Int = Int.MIN_VALUE,
        val y: Int = Int.MIN_VALUE,
        val isTitle: Boolean = false,
        val text: String
    )

    fun extract(stream: InputStream): PptxExtractionResult {
        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val canonicalPath = entry.name.removePrefix("/").replace("\\", "/").lowercase()
                    zipEntries[canonicalPath] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }

        if (zipEntries.isEmpty()) {
            throw ExtractionException("The PPTX file is empty or corrupted.")
        }

        val orderedSlidePaths = resolveSlideOrder(zipEntries)
        if (orderedSlidePaths.isEmpty()) {
            throw ExtractionException("No slides were found in this PPTX file.")
        }

        val fullTextBuilder = StringBuilder()
        val textlessSlideNumbers = mutableListOf<Int>()

        for ((index, slidePath) in orderedSlidePaths.withIndex()) {
            val slideNumber = index + 1
            val slideBytes = zipEntries[slidePath] ?: continue

            val shapeBlocks = parseSlideXml(slideBytes)

            // Resolve and parse notes slide if present
            val notesPath = resolveNotesSlidePath(slidePath, zipEntries)
            val notesText = notesPath?.let { zipEntries[it] }?.let { parseNotesSlideXml(it) }?.trim()

            // Separate title shape block from body shapes
            val titleBlock = shapeBlocks.firstOrNull { it.isTitle }
            val bodyBlocks = if (titleBlock != null) shapeBlocks.filter { it != titleBlock } else shapeBlocks

            val hasBodyText = bodyBlocks.any { it.text.isNotBlank() }
            val hasTitleText = titleBlock?.text?.isNotBlank() == true
            val hasNotesText = !notesText.isNullOrBlank()

            val isTextless = !hasBodyText && !hasTitleText && !hasNotesText

            if (isTextless) {
                textlessSlideNumbers.add(slideNumber)
            } else {
                if (fullTextBuilder.isNotEmpty()) {
                    if (!fullTextBuilder.endsWith("\n\n")) {
                        if (fullTextBuilder.endsWith("\n")) {
                            fullTextBuilder.append("\n")
                        } else {
                            fullTextBuilder.append("\n\n")
                        }
                    }
                }

                // Slide Header
                if (hasTitleText) {
                    fullTextBuilder.append("## Slide $slideNumber: ${titleBlock!!.text.trim()}\n")
                } else {
                    fullTextBuilder.append("## Slide $slideNumber\n")
                }

                // Sort body blocks by y (top to bottom) then x (left to right)
                val sortedBodyBlocks = bodyBlocks.sortedWith(
                    compareBy<ShapeBlock> { it.y }.thenBy { it.x }
                )

                for (block in sortedBodyBlocks) {
                    val trimmed = block.text.trim()
                    if (trimmed.isNotEmpty()) {
                        fullTextBuilder.append(trimmed).append("\n")
                    }
                }

                if (hasNotesText) {
                    fullTextBuilder.append("\n### Notes:\n").append(notesText).append("\n")
                }
            }
        }

        val text = fullTextBuilder.toString().trim()
        val warning = if (textlessSlideNumbers.isNotEmpty()) {
            formatTextlessWarning(textlessSlideNumbers)
        } else {
            null
        }

        return PptxExtractionResult(
            text = text,
            textlessSlideNumbers = textlessSlideNumbers,
            warning = warning
        )
    }

    /**
     * Resolves slide presentation order by reading <p:sldIdLst> in ppt/presentation.xml and
     * mapping relationship IDs via ppt/_rels/presentation.xml.rels.
     */
    private fun resolveSlideOrder(zipEntries: Map<String, ByteArray>): List<String> {
        val presBytes = zipEntries["ppt/presentation.xml"] ?: return fallbackSlideOrder(zipEntries)
        val relsBytes = zipEntries["ppt/_rels/presentation.xml.rels"] ?: return fallbackSlideOrder(zipEntries)

        val rIds = parseSldIdLst(presBytes)
        if (rIds.isEmpty()) return fallbackSlideOrder(zipEntries)

        val rIdToTarget = parseRelationships(relsBytes)

        val orderedPaths = mutableListOf<String>()
        for (rId in rIds) {
            val target = rIdToTarget[rId] ?: continue
            val canonicalPath = normalizePath("ppt", target)
            if (zipEntries.containsKey(canonicalPath)) {
                orderedPaths.add(canonicalPath)
            }
        }

        return if (orderedPaths.isNotEmpty()) orderedPaths else fallbackSlideOrder(zipEntries)
    }

    private fun fallbackSlideOrder(zipEntries: Map<String, ByteArray>): List<String> {
        return zipEntries.keys
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") && !it.contains("_rels") }
            .sortedBy { extractSlideNumber(it) }
    }

    private fun extractSlideNumber(path: String): Int {
        val fileName = path.substringAfterLast("/")
        val numStr = fileName.removePrefix("slide").removeSuffix(".xml")
        return numStr.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun parseXmlDoc(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(bytes))
    }

    private fun parseSldIdLst(bytes: ByteArray): List<String> {
        val doc = parseXmlDoc(bytes)
        val rIds = mutableListOf<String>()
        val sldIdList = doc.getElementsByTagNameNS("*", "sldId")
        for (i in 0 until sldIdList.length) {
            val elem = sldIdList.item(i) as Element
            var rId = elem.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            if (rId.isEmpty()) {
                rId = elem.getAttribute("r:id")
            }
            if (rId.isEmpty()) {
                for (j in 0 until elem.attributes.length) {
                    val attr = elem.attributes.item(j)
                    if (attr.nodeName == "r:id" || attr.localName == "id") {
                        if (attr.nodeValue.startsWith("rId") || !attr.nodeValue.all { it.isDigit() }) {
                            rId = attr.nodeValue
                            break
                        }
                    }
                }
            }
            if (rId.isNotEmpty()) {
                rIds.add(rId)
            }
        }
        return rIds
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val doc = parseXmlDoc(bytes)
        val rels = mutableMapOf<String, String>()
        val relList = doc.getElementsByTagNameNS("*", "Relationship")
        for (i in 0 until relList.length) {
            val elem = relList.item(i) as Element
            val id = elem.getAttribute("Id")
            val target = elem.getAttribute("Target")
            if (id.isNotEmpty() && target.isNotEmpty()) {
                rels[id] = target
            }
        }
        return rels
    }

    private fun parseSlideXml(bytes: ByteArray): List<ShapeBlock> {
        val doc = parseXmlDoc(bytes)
        val blocks = mutableListOf<ShapeBlock>()

        // Find shapes (<p:sp>)
        val spList = doc.getElementsByTagNameNS("*", "sp")
        for (i in 0 until spList.length) {
            val spElem = spList.item(i) as Element

            // Do not process text inside tables as plain shapes
            if (isInsideTable(spElem)) continue

            val isTitle = checkIsTitle(spElem)
            val (x, y) = getCoordinates(spElem)
            val text = extractTextFromTxBody(spElem)

            if (text.isNotBlank()) {
                blocks.add(ShapeBlock(x = x, y = y, isTitle = isTitle, text = text))
            }
        }

        // Find tables (<a:tbl>)
        val tblList = doc.getElementsByTagNameNS("*", "tbl")
        for (i in 0 until tblList.length) {
            val tblElem = tblList.item(i) as Element
            val (x, y) = getCoordinates(tblElem)
            val tableMarkdown = extractTableMarkdown(tblElem)
            if (tableMarkdown.isNotBlank()) {
                blocks.add(ShapeBlock(x = x, y = y, isTitle = false, text = tableMarkdown))
            }
        }

        return blocks
    }

    private fun isInsideTable(node: Node): Boolean {
        var parent = node.parentNode
        while (parent != null) {
            if (parent is Element && (parent.localName == "tbl" || parent.nodeName.endsWith(":tbl"))) {
                return true
            }
            parent = parent.parentNode
        }
        return false
    }

    private fun checkIsTitle(spElem: Element): Boolean {
        val phList = spElem.getElementsByTagNameNS("*", "ph")
        for (i in 0 until phList.length) {
            val ph = phList.item(i) as Element
            val typeVal = ph.getAttribute("type")
            if (typeVal == "title" || typeVal == "ctrTitle") {
                return true
            }
        }
        return false
    }

    private fun getCoordinates(elem: Element): Pair<Int, Int> {
        val offList = elem.getElementsByTagNameNS("*", "off")
        if (offList.length > 0) {
            val off = offList.item(0) as Element
            val x = off.getAttribute("x").toIntOrNull() ?: Int.MIN_VALUE
            val y = off.getAttribute("y").toIntOrNull() ?: Int.MIN_VALUE
            return Pair(x, y)
        }
        return Pair(Int.MIN_VALUE, Int.MIN_VALUE)
    }

    private fun extractTextFromTxBody(elem: Element): String {
        val txBodyList = elem.getElementsByTagNameNS("*", "txBody")
        if (txBodyList.length == 0) return ""
        val txBody = txBodyList.item(0) as Element

        val pList = txBody.getElementsByTagNameNS("*", "p")
        val lines = mutableListOf<String>()

        for (i in 0 until pList.length) {
            val pElem = pList.item(i) as Element
            val pText = extractParagraphText(pElem)
            lines.add(pText)
        }

        return lines.joinToString("\n").trim()
    }

    private fun extractParagraphText(pElem: Element): String {
        val sb = StringBuilder()
        val childNodes = pElem.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val localName = child.localName ?: child.nodeName.substringAfterLast(":")
                when (localName) {
                    "r", "fld" -> {
                        val tList = child.getElementsByTagNameNS("*", "t")
                        for (j in 0 until tList.length) {
                            sb.append(tList.item(j).textContent)
                        }
                    }
                    "t" -> sb.append(child.textContent)
                    "br" -> sb.append("\n")
                }
            }
        }
        return sb.toString()
    }

    private fun extractTableMarkdown(tblElem: Element): String {
        val trList = tblElem.getElementsByTagNameNS("*", "tr")
        if (trList.length == 0) return ""

        val rows = mutableListOf<List<String>>()
        for (i in 0 until trList.length) {
            val tr = trList.item(i) as Element
            val tcList = tr.getElementsByTagNameNS("*", "tc")
            val rowCells = mutableListOf<String>()
            for (j in 0 until tcList.length) {
                val tc = tcList.item(j) as Element
                val cellText = extractTextFromTxBody(tc).replace("\n", " ").trim().replace("|", "\\|")
                rowCells.add(cellText)
            }
            rows.add(rowCells)
        }

        return formatPipeTable(rows)
    }

    private fun resolveNotesSlidePath(slidePath: String, zipEntries: Map<String, ByteArray>): String? {
        val lastSlash = slidePath.lastIndexOf('/')
        if (lastSlash < 0) return null
        val dir = slidePath.substring(0, lastSlash)
        val file = slidePath.substring(lastSlash + 1)

        val relsEntryPath = "$dir/_rels/$file.rels"
        val relsBytes = zipEntries[relsEntryPath] ?: return null

        val rIdToTarget = parseRelationships(relsBytes)
        val notesTarget = rIdToTarget.values.firstOrNull { it.contains("notesSlide", ignoreCase = true) }
            ?: return null

        return normalizePath(dir, notesTarget)
    }

    /**
     * Parses a notes slide XML, extracting text ONLY from the body shape (<p:ph type="body">).
     * Slide number, slide image, header, and footer placeholders are explicitly discarded.
     */
    private fun parseNotesSlideXml(bytes: ByteArray): String {
        val doc = parseXmlDoc(bytes)
        val notesBuilder = StringBuilder()

        val spList = doc.getElementsByTagNameNS("*", "sp")
        for (i in 0 until spList.length) {
            val spElem = spList.item(i) as Element

            val phList = spElem.getElementsByTagNameNS("*", "ph")
            var placeholderType: String? = null
            if (phList.length > 0) {
                val ph = phList.item(0) as Element
                if (ph.hasAttribute("type")) {
                    placeholderType = ph.getAttribute("type")
                }
            }

            // Discard slide number (sldNum), slide image (sldImg), header (hdr), and footer (ftr)
            val isDiscarded = placeholderType in listOf("sldNum", "sldImg", "hdr", "ftr")
            if (!isDiscarded) {
                val text = extractTextFromTxBody(spElem).trim()
                if (text.isNotEmpty()) {
                    if (notesBuilder.isNotEmpty()) notesBuilder.append("\n")
                    notesBuilder.append(text)
                }
            }
        }

        return notesBuilder.toString().trim()
    }

    private fun formatPipeTable(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val numCols = rows.maxOfOrNull { it.size } ?: 0
        if (numCols == 0) return ""

        val sb = StringBuilder()
        val firstRow = rows[0]
        val headerCells = (0 until numCols).map { i -> firstRow.getOrNull(i).orEmpty() }

        sb.append("| ").append(headerCells.joinToString(" | ")).append(" |\n")
        sb.append("| ").append((0 until numCols).joinToString(" | ") { "---" }).append(" |\n")

        for (r in 1 until rows.size) {
            val row = rows[r]
            val cells = (0 until numCols).map { i -> row.getOrNull(i).orEmpty() }
            sb.append("| ").append(cells.joinToString(" | ")).append(" |\n")
        }

        return sb.toString().trim()
    }

    private fun normalizePath(baseDir: String, target: String): String {
        val cleanTarget = target.replace("\\", "/")
        if (cleanTarget.startsWith("/")) {
            return cleanTarget.removePrefix("/")
        }
        val segments = (baseDir.split("/") + cleanTarget.split("/")).filter { it.isNotEmpty() }
        val resolved = mutableListOf<String>()
        for (seg in segments) {
            when (seg) {
                "." -> {}
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(seg)
            }
        }
        return resolved.joinToString("/").lowercase()
    }

    private fun formatTextlessWarning(slides: List<Int>): String {
        if (slides.isEmpty()) return ""
        val listStr = when (slides.size) {
            1 -> "Slide ${slides[0]}"
            2 -> "Slides ${slides[0]} and ${slides[1]}"
            else -> "Slides ${slides.dropLast(1).joinToString(", ")} and ${slides.last()}"
        }
        val verb = if (slides.size == 1) "was" else "were"
        return "$listStr had no text (images or charts only) and $verb skipped."
    }
}
