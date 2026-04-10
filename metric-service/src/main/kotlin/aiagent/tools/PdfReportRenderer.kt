package aiagent.tools

import com.example.resolveProjectSubdirectory
import com.example.sanitizeFileComponent
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.imageio.ImageIO

private const val a4WidthPoints = 595
private const val a4HeightPoints = 842
private const val renderScale = 2
private const val pageWidthPx = a4WidthPoints * renderScale
private const val pageHeightPx = a4HeightPoints * renderScale
private const val pageMarginPx = 72
private const val contentWidthPx = pageWidthPx - (pageMarginPx * 2)

private val bodyColor = Color(33, 37, 41)
private val headingColor = Color(22, 46, 91)
private val accentColor = Color(76, 110, 245)
private val reportFontFamily by lazy {
    val availableFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    listOf(
        "PingFang SC",
        "Hiragino Sans GB",
        "Microsoft YaHei",
        "Noto Sans CJK SC",
        "Source Han Sans SC",
        "SimHei",
        "Arial Unicode MS",
        Font.SANS_SERIF,
    ).firstOrNull { availableFamilies.contains(it) } ?: Font.SANS_SERIF
}

private data class TextStyle(
    val font: Font,
    val color: Color,
    val indentPx: Int,
    val spacingBeforePx: Int,
    val spacingAfterPx: Int,
    val lineSpacingPx: Int,
)

internal fun writeReportPdf(reportId: String, patientId: String, content: String): Path {
    val reportDirectory = resolveProjectSubdirectory("reports")
    val safePatientId = sanitizeFileComponent(patientId, fallback = "unknown_patient")
    val reportPath = reportDirectory.resolve("${safePatientId}_${reportId}.pdf")
    val pdfBytes = renderPdfDocument(content)
    Files.write(
        reportPath,
        pdfBytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
    )
    return reportPath
}

private fun renderPdfDocument(content: String): ByteArray {
    val pageImages = renderPages(content)
    return buildPdf(pageImages)
}

private fun renderPages(content: String): List<BufferedImage> {
    val pages = mutableListOf<BufferedImage>()
    var page = createPageImage()
    var graphics = createGraphics(page)
    var cursorY = pageMarginPx

    fun startNewPage() {
        graphics.dispose()
        pages += page
        page = createPageImage()
        graphics = createGraphics(page)
        cursorY = pageMarginPx
    }

    for (rawLine in content.lines()) {
        val style = resolveTextStyle(rawLine)
        val text = normalizeContentLine(rawLine)
        val metrics = graphics.getFontMetrics(style.font)
        val wrappedLines = if (text.isBlank()) listOf("") else wrapText(text, metrics, contentWidthPx - style.indentPx)

        cursorY += style.spacingBeforePx
        for (line in wrappedLines) {
            val lineHeight = metrics.height + style.lineSpacingPx
            if (cursorY + lineHeight > pageHeightPx - pageMarginPx) {
                startNewPage()
            }

            graphics.font = style.font
            graphics.color = style.color
            val activeMetrics = graphics.getFontMetrics(style.font)
            graphics.drawString(line, pageMarginPx + style.indentPx, cursorY + activeMetrics.ascent)
            cursorY += activeMetrics.height + style.lineSpacingPx
        }
        cursorY += style.spacingAfterPx
    }

    graphics.dispose()
    pages += page
    return pages
}

private fun createPageImage(): BufferedImage {
    val image = BufferedImage(pageWidthPx, pageHeightPx, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color.WHITE
    graphics.fillRect(0, 0, pageWidthPx, pageHeightPx)
    graphics.dispose()
    return image
}

private fun createGraphics(image: BufferedImage): Graphics2D {
    return image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        color = bodyColor
        background = Color.WHITE
    }
}

private fun resolveTextStyle(rawLine: String): TextStyle {
    val trimmed = rawLine.trimStart()
    return when {
        trimmed.startsWith("# ") -> TextStyle(
            font = Font(reportFontFamily, Font.BOLD, 24 * renderScale),
            color = accentColor,
            indentPx = 0,
            spacingBeforePx = 8,
            spacingAfterPx = 18,
            lineSpacingPx = 8,
        )

        trimmed.startsWith("## ") -> TextStyle(
            font = Font(reportFontFamily, Font.BOLD, 18 * renderScale),
            color = headingColor,
            indentPx = 0,
            spacingBeforePx = 14,
            spacingAfterPx = 10,
            lineSpacingPx = 6,
        )

        trimmed.matches(Regex("\\d+\\..*")) -> TextStyle(
            font = Font(reportFontFamily, Font.PLAIN, 12 * renderScale),
            color = bodyColor,
            indentPx = 18,
            spacingBeforePx = 2,
            spacingAfterPx = 4,
            lineSpacingPx = 4,
        )

        trimmed.startsWith("- ") -> TextStyle(
            font = Font(reportFontFamily, Font.PLAIN, 12 * renderScale),
            color = bodyColor,
            indentPx = 18,
            spacingBeforePx = 2,
            spacingAfterPx = 4,
            lineSpacingPx = 4,
        )

        trimmed.isBlank() -> TextStyle(
            font = Font(reportFontFamily, Font.PLAIN, 12 * renderScale),
            color = bodyColor,
            indentPx = 0,
            spacingBeforePx = 6,
            spacingAfterPx = 6,
            lineSpacingPx = 0,
        )

        else -> TextStyle(
            font = Font(reportFontFamily, Font.PLAIN, 12 * renderScale),
            color = bodyColor,
            indentPx = 0,
            spacingBeforePx = 2,
            spacingAfterPx = 6,
            lineSpacingPx = 4,
        )
    }
}

private fun normalizeContentLine(rawLine: String): String {
    val trimmed = rawLine.trimStart()
    return when {
        trimmed.startsWith("# ") -> trimmed.removePrefix("# ")
        trimmed.startsWith("## ") -> trimmed.removePrefix("## ")
        else -> trimmed
    }
}

private fun wrapText(text: String, fontMetrics: java.awt.FontMetrics, maxWidth: Int): List<String> {
    if (text.isBlank()) {
        return listOf("")
    }

    val wrapped = mutableListOf<String>()
    val current = StringBuilder()
    for (character in text) {
        if (character == '\n') {
            wrapped += current.toString().trimEnd()
            current.clear()
            continue
        }

        val candidate = current.toString() + character
        if (current.isNotEmpty() && fontMetrics.stringWidth(candidate) > maxWidth) {
            wrapped += current.toString().trimEnd()
            current.clear()
        }
        current.append(character)
    }

    if (current.isNotEmpty()) {
        wrapped += current.toString().trimEnd()
    }

    return wrapped.ifEmpty { listOf("") }
}

private fun buildPdf(pageImages: List<BufferedImage>): ByteArray {
    val imageBytes = pageImages.map(::toJpeg)
    val output = ByteArrayOutputStream()
    val offsets = mutableMapOf<Int, Int>()
    val totalObjects = 2 + (pageImages.size * 3)
    output.write("%PDF-1.4\n".toByteArray(StandardCharsets.ISO_8859_1))
    output.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

    val pageObjectIds = mutableListOf<Int>()
    val contentObjectIds = mutableListOf<Int>()
    val imageObjectIds = mutableListOf<Int>()
    var nextObjectId = 3
    repeat(pageImages.size) {
        pageObjectIds += nextObjectId++
        contentObjectIds += nextObjectId++
        imageObjectIds += nextObjectId++
    }

    writePdfObject(output, offsets, 1, catalogObject())
    writePdfObject(output, offsets, 2, pagesObject(pageObjectIds))

    pageImages.forEachIndexed { index, image ->
        val imageName = "Im${index + 1}"
        writePdfObject(
            output,
            offsets,
            pageObjectIds[index],
            pageObject(
                pageId = pageObjectIds[index],
                contentId = contentObjectIds[index],
                imageId = imageObjectIds[index],
                imageName = imageName,
            ),
        )
        writePdfObject(
            output,
            offsets,
            contentObjectIds[index],
            contentStreamObject(imageName),
        )
        writePdfObject(
            output,
            offsets,
            imageObjectIds[index],
            imageObject(imageBytes[index], image.width, image.height),
        )
    }

    val xrefOffset = output.size()
    output.write("xref\n".toByteArray(StandardCharsets.ISO_8859_1))
    output.write("0 ${totalObjects + 1}\n".toByteArray(StandardCharsets.ISO_8859_1))
    output.write("0000000000 65535 f \n".toByteArray(StandardCharsets.ISO_8859_1))
    for (objectId in 1..totalObjects) {
        val offset = offsets[objectId] ?: 0
        output.write(String.format("%010d 00000 n \n", offset).toByteArray(StandardCharsets.ISO_8859_1))
    }
    output.write("trailer\n".toByteArray(StandardCharsets.ISO_8859_1))
    output.write("<< /Size ${totalObjects + 1} /Root 1 0 R >>\n".toByteArray(StandardCharsets.ISO_8859_1))
    output.write("startxref\n$xrefOffset\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1))
    return output.toByteArray()
}

private fun writePdfObject(
    output: ByteArrayOutputStream,
    offsets: MutableMap<Int, Int>,
    objectId: Int,
    content: ByteArray,
) {
    offsets[objectId] = output.size()
    output.write("$objectId 0 obj\n".toByteArray(StandardCharsets.ISO_8859_1))
    output.write(content)
    output.write("\nendobj\n".toByteArray(StandardCharsets.ISO_8859_1))
}

private fun catalogObject(): ByteArray {
    return "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(StandardCharsets.ISO_8859_1)
}

private fun pagesObject(pageObjectIds: List<Int>): ByteArray {
    val kids = pageObjectIds.joinToString(" ") { "$it 0 R" }
    return "<< /Type /Pages /Count ${pageObjectIds.size} /Kids [$kids] >>".toByteArray(StandardCharsets.ISO_8859_1)
}

private fun pageObject(pageId: Int, contentId: Int, imageId: Int, imageName: String): ByteArray {
    return (
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $a4WidthPoints $a4HeightPoints] " +
            "/Resources << /ProcSet [/PDF /ImageC] /XObject << /$imageName $imageId 0 R >> >> " +
            "/Contents $contentId 0 R >>"
        ).toByteArray(StandardCharsets.ISO_8859_1)
}

private fun contentStreamObject(imageName: String): ByteArray {
    val commands = buildString {
        append("q\n")
        append("$a4WidthPoints 0 0 $a4HeightPoints 0 0 cm\n")
        append("/$imageName Do\n")
        append("Q\n")
    }.toByteArray(StandardCharsets.ISO_8859_1)

    val header = "<< /Length ${commands.size} >>\nstream\n".toByteArray(StandardCharsets.ISO_8859_1)
    val footer = "endstream".toByteArray(StandardCharsets.ISO_8859_1)
    return header + commands + footer
}

private fun imageObject(jpegBytes: ByteArray, width: Int, height: Int): ByteArray {
    val header = (
        "<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
            "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
        ).toByteArray(StandardCharsets.ISO_8859_1)
    val footer = "\nendstream".toByteArray(StandardCharsets.ISO_8859_1)
    return header + jpegBytes + footer
}

private fun toJpeg(image: BufferedImage): ByteArray {
    val output = ByteArrayOutputStream()
    ImageIO.write(image, "jpg", output)
    return output.toByteArray()
}
