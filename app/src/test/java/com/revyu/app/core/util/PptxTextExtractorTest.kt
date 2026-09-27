package com.revyu.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PptxTextExtractorTest {

    private val extractor = PptxTextExtractor()

    private fun createPptxStream(entries: Map<String, String>): InputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            for ((path, content) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(baos.toByteArray())
    }

    @Test
    fun testSingleSlideWithTitleAndBodyText() {
        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst>
                    <p:sldId id="256" r:id="rId2"/>
                </p:sldIdLst>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:nvSpPr>
                                <p:nvPr>
                                    <p:ph type="title"/>
                                </p:nvPr>
                            </p:nvSpPr>
                            <p:txBody>
                                <a:p><a:r><a:t>Algorithms 101</a:t></a:r></a:p>
                            </p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:spPr>
                                <a:xfrm><a:off x="1000" y="2000"/></a:xfrm>
                            </p:spPr>
                            <p:txBody>
                                <a:p><a:r><a:t>Sorting and Searching</a:t></a:r></a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val entries = mapOf(
            "ppt/presentation.xml" to presentationXml,
            "ppt/_rels/presentation.xml.rels" to presentationRels,
            "ppt/slides/slide1.xml" to slide1Xml
        )

        val result = extractor.extract(createPptxStream(entries))
        val expected = "## Slide 1: Algorithms 101\nSorting and Searching"
        assertEquals(expected, result.text)
        assertTrue(result.textlessSlideNumbers.isEmpty())
        assertNull(result.warning)
    }

    @Test
    fun testNonSequentialSlideOrder() {
        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst>
                    <p:sldId id="257" r:id="rId3"/>
                    <p:sldId id="256" r:id="rId2"/>
                </p:sldIdLst>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
                <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide3.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:txBody><a:p><a:r><a:t>Content of file slide1.xml</a:t></a:r></a:p></p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val slide3Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:txBody><a:p><a:r><a:t>Content of file slide3.xml</a:t></a:r></a:p></p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val entries = mapOf(
            "ppt/presentation.xml" to presentationXml,
            "ppt/_rels/presentation.xml.rels" to presentationRels,
            "ppt/slides/slide1.xml" to slide1Xml,
            "ppt/slides/slide3.xml" to slide3Xml
        )

        val result = extractor.extract(createPptxStream(entries))
        // slide3.xml comes FIRST according to sldIdLst (rId3 then rId2)
        val expected = "## Slide 1\nContent of file slide3.xml\n\n## Slide 2\nContent of file slide1.xml"
        assertEquals(expected, result.text)
    }

    @Test
    fun testTableWithPipeEscapingAndHeaderRow() {
        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst><p:sldId id="256" r:id="rId2"/></p:sldIdLst>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:graphicFrame>
                            <a:graphic>
                                <a:graphicData>
                                    <a:tbl>
                                        <a:tr>
                                            <a:tc><a:txBody><a:p><a:r><a:t>Option | Name</a:t></a:r></a:p></a:txBody></a:tc>
                                            <a:tc><a:txBody><a:p><a:r><a:t>Score</a:t></a:r></a:p></a:txBody></a:tc>
                                        </a:tr>
                                        <a:tr>
                                            <a:tc><a:txBody><a:p><a:r><a:t>Item A | B</a:t></a:r></a:p></a:txBody></a:tc>
                                            <a:tc><a:txBody><a:p><a:r><a:t>95%</a:t></a:r></a:p></a:txBody></a:tc>
                                        </a:tr>
                                    </a:tbl>
                                </a:graphicData>
                            </a:graphic>
                        </p:graphicFrame>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val entries = mapOf(
            "ppt/presentation.xml" to presentationXml,
            "ppt/_rels/presentation.xml.rels" to presentationRels,
            "ppt/slides/slide1.xml" to slide1Xml
        )

        val result = extractor.extract(createPptxStream(entries))
        assertTrue(result.text.contains("| Option \\| Name | Score |"))
        assertTrue(result.text.contains("| --- | --- |"))
        assertTrue(result.text.contains("| Item A \\| B | 95% |"))
    }

    @Test
    fun testGroupedShapeExtraction() {
        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst><p:sldId id="256" r:id="rId2"/></p:sldIdLst>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:grpSp>
                            <p:sp>
                                <p:spPr><a:xfrm><a:off x="10" y="10"/></a:xfrm></p:spPr>
                                <p:txBody><a:p><a:r><a:t>Grouped Shape Item 1</a:t></a:r></a:p></p:txBody>
                            </p:sp>
                            <p:sp>
                                <p:spPr><a:xfrm><a:off x="10" y="20"/></a:xfrm></p:spPr>
                                <p:txBody><a:p><a:r><a:t>Grouped Shape Item 2</a:t></a:r></a:p></p:txBody>
                            </p:sp>
                        </p:grpSp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val entries = mapOf(
            "ppt/presentation.xml" to presentationXml,
            "ppt/_rels/presentation.xml.rels" to presentationRels,
            "ppt/slides/slide1.xml" to slide1Xml
        )

        val result = extractor.extract(createPptxStream(entries))
        assertTrue(result.text.contains("Grouped Shape Item 1"))
        assertTrue(result.text.contains("Grouped Shape Item 2"))
    }

    @Test
    fun testSlideWithBlankNotesPart() {
        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst><p:sldId id="256" r:id="rId2"/></p:sldIdLst>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide1.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp><p:txBody><a:p><a:r><a:t>Slide content</a:t></a:r></a:p></p:txBody></p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val notesSlide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:notes xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                     xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:nvSpPr><p:nvPr><p:ph type="body"/></p:nvPr></p:nvSpPr>
                            <p:txBody><a:p><a:r><a:t>   </a:t></a:r></a:p></p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:notes>
        """.trimIndent()

        val entries = mapOf(
            "ppt/presentation.xml" to presentationXml,
            "ppt/_rels/presentation.xml.rels" to presentationRels,
            "ppt/slides/_rels/slide1.xml.rels" to slide1Rels,
            "ppt/slides/slide1.xml" to slide1Xml,
            "ppt/notesSlides/notesSlide1.xml" to notesSlide1Xml
        )

        val result = extractor.extract(createPptxStream(entries))
        assertTrue(!result.text.contains("Notes:"))
    }

    @Test
    fun testNotesSlideWithSlideNumberPlaceholderLeakPrevention() {
        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst><p:sldId id="256" r:id="rId2"/></p:sldIdLst>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="../notesSlides/notesSlide1.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp><p:txBody><a:p><a:r><a:t>Slide content</a:t></a:r></a:p></p:txBody></p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val notesSlide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:notes xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                     xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:nvSpPr><p:nvPr><p:ph type="sldNum"/></p:nvPr></p:nvSpPr>
                            <p:txBody><a:p><a:r><a:t>1</a:t></a:r></a:p></p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:nvSpPr><p:nvPr><p:ph type="body"/></p:nvPr></p:nvSpPr>
                            <p:txBody><a:p><a:r><a:t>Real presenter note for slide 1.</a:t></a:r></a:p></p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:notes>
        """.trimIndent()

        val entries = mapOf(
            "ppt/presentation.xml" to presentationXml,
            "ppt/_rels/presentation.xml.rels" to presentationRels,
            "ppt/slides/_rels/slide1.xml.rels" to slide1Rels,
            "ppt/slides/slide1.xml" to slide1Xml,
            "ppt/notesSlides/notesSlide1.xml" to notesSlide1Xml
        )

        val result = extractor.extract(createPptxStream(entries))
        assertTrue(result.text.contains("### Notes:\nReal presenter note for slide 1."))
        assertTrue(!result.text.contains("### Notes:\n1"))
    }

    @Test
    fun testShapeOrderingBySpatialYCoordinate() {
        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst><p:sldId id="256" r:id="rId2"/></p:sldIdLst>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
            </Relationships>
        """.trimIndent()

        // XML document order has Shape B (y=5000) BEFORE Shape A (y=1000)
        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:spPr><a:xfrm><a:off x="100" y="5000"/></a:xfrm></p:spPr>
                            <p:txBody><a:p><a:r><a:t>Lower shape B</a:t></a:r></a:p></p:txBody>
                        </p:sp>
                        <p:sp>
                            <p:spPr><a:xfrm><a:off x="100" y="1000"/></a:xfrm></p:spPr>
                            <p:txBody><a:p><a:r><a:t>Upper shape A</a:t></a:r></a:p></p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val entries = mapOf(
            "ppt/presentation.xml" to presentationXml,
            "ppt/_rels/presentation.xml.rels" to presentationRels,
            "ppt/slides/slide1.xml" to slide1Xml
        )

        val result = extractor.extract(createPptxStream(entries))
        val posA = result.text.indexOf("Upper shape A")
        val posB = result.text.indexOf("Lower shape B")
        assertTrue(posA >= 0 && posB >= 0 && posA < posB)
    }

    @Test
    fun testTextlessSlideWarning() {
        val presentationXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst>
                    <p:sldId id="256" r:id="rId2"/>
                    <p:sldId id="257" r:id="rId3"/>
                </p:sldIdLst>
            </p:presentation>
        """.trimIndent()

        val presentationRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
                <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
            </Relationships>
        """.trimIndent()

        val slide1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp><p:txBody><a:p><a:r><a:t>Slide 1 text</a:t></a:r></a:p></p:txBody></p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        // slide2Xml contains only an image (no text)
        val slide2Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                   xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:cSld>
                    <p:spTree>
                        <p:pic>
                            <p:blipFill><a:blip r:embed="rId1"/></p:blipFill>
                        </p:pic>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val entries = mapOf(
            "ppt/presentation.xml" to presentationXml,
            "ppt/_rels/presentation.xml.rels" to presentationRels,
            "ppt/slides/slide1.xml" to slide1Xml,
            "ppt/slides/slide2.xml" to slide2Xml
        )

        val result = extractor.extract(createPptxStream(entries))
        assertEquals(listOf(2), result.textlessSlideNumbers)
        assertNotNull(result.warning)
        assertEquals("Slide 2 had no text (images or charts only) and was skipped.", result.warning)
    }
}
