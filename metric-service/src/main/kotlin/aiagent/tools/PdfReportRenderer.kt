package aiagent.tools

import com.example.resolveProjectSubdirectory
import com.example.sanitizeFileComponent
import java.awt.AlphaComposite
import java.awt.BasicStroke
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
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val a4WidthPoints = 595
private const val a4HeightPoints = 842
private const val renderScale = 2
private const val pageWidthPx = a4WidthPoints * renderScale
private const val pageHeightPx = a4HeightPoints * renderScale
private const val pageMarginPx = 72
private const val contentWidthPx = pageWidthPx - (pageMarginPx * 2)
private const val reportFigureSectionTitle = "影像高亮附图"

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

private data class ColorLegendLine(
    val color: Color,
    val text: String,
)

internal fun writeReportPdf(reportId: String, patientId: String, content: String): Path {
    return writeReportPdf(
        reportId = reportId,
        patientId = patientId,
        content = content,
        reportPreviewImage = null,
    )
}

internal fun writeReportPdf(
    reportId: String,
    patientId: String,
    content: String,
    reportPreviewImage: BufferedImage?,
): Path {
    val reportDirectory = resolveProjectSubdirectory("reports")
    val safePatientId = sanitizeFileComponent(patientId, fallback = "unknown_patient")
    val reportPath = reportDirectory.resolve("${safePatientId}_${reportId}.pdf")
    val pdfBytes = renderPdfDocument(content, reportPreviewImage)
    Files.write(
        reportPath,
        pdfBytes,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
    )
    return reportPath
}

private fun renderPdfDocument(content: String, reportPreviewImage: BufferedImage?): ByteArray {
    val pageImages = renderPages(content, reportPreviewImage)
    return buildPdf(pageImages)
}

private fun renderPages(content: String, reportPreviewImage: BufferedImage?): List<BufferedImage> {
    val pages = mutableListOf<BufferedImage>()
    var page = createPageImage()
    var graphics = createGraphics(page)
    var cursorY = pageMarginPx
    val scaledReportPreview = reportPreviewImage?.let {
        scaleImageToFit(
            image = it,
            maxWidth = contentWidthPx - (24 * renderScale),
            maxHeight = 360 * renderScale,
        )
    }
    var shouldInsertReportPreview = false

    fun startNewPage() {
        graphics.dispose()
        pages += page
        page = createPageImage()
        graphics = createGraphics(page)
        cursorY = pageMarginPx
    }

    fun drawPendingReportPreview() {
        if (!shouldInsertReportPreview) {
            return
        }
        shouldInsertReportPreview = false
        val previewImage = scaledReportPreview ?: return
        val blockHeight = calculateReportPreviewBlockHeight(previewImage)
        if (cursorY + blockHeight > pageHeightPx - pageMarginPx) {
            startNewPage()
        }
        drawReportPreviewBlock(
            graphics = graphics,
            previewImage = previewImage,
            startY = cursorY,
        )
        cursorY += blockHeight
    }

    for (rawLine in content.lines()) {
        drawPendingReportPreview()
        val style = resolveTextStyle(rawLine)
        val legendLine = parseColorLegendLine(rawLine)
        val text = legendLine?.text ?: normalizeContentLine(rawLine)
        val metrics = graphics.getFontMetrics(style.font)
        val legendOffsetPx = if (legendLine != null) 16 * renderScale else 0
        val wrappedLines = if (text.isBlank()) {
            listOf("")
        } else {
            wrapText(text, metrics, contentWidthPx - style.indentPx - legendOffsetPx)
        }
        if (
            scaledReportPreview != null &&
            rawLine.trimStart().startsWith("## ") &&
            normalizeContentLine(rawLine) == reportFigureSectionTitle
        ) {
            val requiredHeight = style.spacingBeforePx +
                metrics.height +
                style.spacingAfterPx +
                calculateReportPreviewBlockHeight(scaledReportPreview)
            if (cursorY + requiredHeight > pageHeightPx - pageMarginPx) {
                startNewPage()
            }
        }

        cursorY += style.spacingBeforePx
        var renderedLegendChip = false
        for (line in wrappedLines) {
            val lineHeight = maxOf(metrics.height + style.lineSpacingPx, if (legendLine != null) 12 * renderScale else 0)
            if (cursorY + lineHeight > pageHeightPx - pageMarginPx) {
                startNewPage()
            }

            graphics.font = style.font
            graphics.color = style.color
            val activeMetrics = graphics.getFontMetrics(style.font)
            if (legendLine != null) {
                val chipSize = 9 * renderScale
                val chipX = pageMarginPx + style.indentPx
                val chipY = cursorY + ((activeMetrics.height - chipSize) / 2).coerceAtLeast(0)
                if (!renderedLegendChip) {
                    graphics.color = legendLine.color
                    graphics.fillRoundRect(chipX, chipY, chipSize, chipSize, 8, 8)
                    graphics.color = legendLine.color.darker()
                    graphics.drawRoundRect(chipX, chipY, chipSize, chipSize, 8, 8)
                    renderedLegendChip = true
                }
                graphics.color = style.color
                graphics.drawString(line, chipX + chipSize + (7 * renderScale), cursorY + activeMetrics.ascent)
            } else {
                graphics.drawString(line, pageMarginPx + style.indentPx, cursorY + activeMetrics.ascent)
            }
            cursorY += activeMetrics.height + style.lineSpacingPx
        }
        cursorY += style.spacingAfterPx

        if (
            scaledReportPreview != null &&
            rawLine.trimStart().startsWith("## ") &&
            normalizeContentLine(rawLine) == reportFigureSectionTitle
        ) {
            shouldInsertReportPreview = true
        }
    }

    drawPendingReportPreview()
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

private fun parseColorLegendLine(rawLine: String): ColorLegendLine? {
    val trimmed = rawLine.trimStart()
    val match = Regex("- \\[(#[0-9A-Fa-f]{6})] (.+)").matchEntire(trimmed) ?: return null
    val color = runCatching { Color.decode(match.groupValues[1]) }.getOrNull() ?: return null
    return ColorLegendLine(
        color = color,
        text = match.groupValues[2].trim(),
    )
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

internal fun buildReportHighlightPreview(
    baseImage: BufferedImage,
    regions: List<HighlightRegion>,
    legend: List<HighlightLegendItem>,
): BufferedImage {
    val preparedBase = scaleImageToFit(
        image = baseImage,
        maxWidth = 1440,
        maxHeight = 960,
    )
    val canvas = BufferedImage(preparedBase.width, preparedBase.height, BufferedImage.TYPE_INT_RGB)
    val graphics = createGraphics(canvas)
    graphics.drawImage(preparedBase, 0, 0, null)

    regions
        .sortedByDescending { it.priority }
        .forEach { region ->
            drawHighlightRegion(
                graphics = graphics,
                region = region,
                canvasWidth = canvas.width,
                canvasHeight = canvas.height,
            )
        }

    drawPreviewLegend(
        graphics = graphics,
        legend = legend,
        canvasWidth = canvas.width,
        canvasHeight = canvas.height,
    )

    graphics.dispose()
    return canvas
}

private fun calculateReportPreviewBlockHeight(previewImage: BufferedImage): Int {
    val padding = 12 * renderScale
    val headerHeight = 20 * renderScale
    val footerSpacing = 12 * renderScale
    return padding + headerHeight + previewImage.height + padding + footerSpacing
}

private fun drawReportPreviewBlock(
    graphics: Graphics2D,
    previewImage: BufferedImage,
    startY: Int,
) {
    val padding = 12 * renderScale
    val headerHeight = 20 * renderScale
    val frameX = pageMarginPx
    val frameY = startY + (4 * renderScale)
    val frameWidth = contentWidthPx
    val frameHeight = calculateReportPreviewBlockHeight(previewImage) - (4 * renderScale)
    val imageX = frameX + padding
    val imageY = frameY + headerHeight + padding

    graphics.color = Color(239, 246, 255)
    graphics.fillRoundRect(frameX, frameY, frameWidth, frameHeight, 22, 22)
    graphics.color = Color(191, 219, 254)
    graphics.stroke = BasicStroke(2f)
    graphics.drawRoundRect(frameX, frameY, frameWidth, frameHeight, 22, 22)

    graphics.font = Font(reportFontFamily, Font.BOLD, 11 * renderScale)
    graphics.color = headingColor
    val titleMetrics = graphics.getFontMetrics(graphics.font)
    graphics.drawString("AI 高亮标注附图", imageX, frameY + padding + titleMetrics.ascent)

    graphics.drawImage(previewImage, imageX, imageY, null)
}

private fun drawHighlightRegion(
    graphics: Graphics2D,
    region: HighlightRegion,
    canvasWidth: Int,
    canvasHeight: Int,
) {
    val regionColor = parseColor(region.colorHex, accentColor)
    val regionPath = buildHighlightPath(region, canvasWidth, canvasHeight)
    val strokeWidth = max(3f, canvasWidth * 0.0042f)

    graphics.composite = AlphaComposite.SrcOver.derive(0.28f)
    graphics.color = regionColor
    graphics.fill(regionPath)

    graphics.composite = AlphaComposite.SrcOver
    graphics.color = withAlpha(regionColor, 88)
    graphics.stroke = BasicStroke(strokeWidth * 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    graphics.draw(regionPath)

    graphics.color = withAlpha(regionColor, 222)
    graphics.stroke = BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    graphics.draw(regionPath)

    graphics.color = withAlpha(Color.WHITE, 124)
    graphics.stroke = BasicStroke(max(1.35f, strokeWidth * 0.26f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    graphics.draw(regionPath)

    drawRegionLabel(
        graphics = graphics,
        region = region,
        regionColor = regionColor,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
    )
}

private fun buildHighlightPath(
    region: HighlightRegion,
    canvasWidth: Int,
    canvasHeight: Int,
): Path2D.Double {
    if (region.contour.size >= 3) {
        val contourPath = Path2D.Double()
        region.contour.forEachIndexed { index, point ->
            val pointX = canvasWidth * point.xPercent / 100.0
            val pointY = canvasHeight * point.yPercent / 100.0
            if (index == 0) {
                contourPath.moveTo(pointX, pointY)
            } else {
                contourPath.lineTo(pointX, pointY)
            }
        }
        contourPath.closePath()
        return contourPath
    }

    val left = canvasWidth * region.boundingBox.leftPercent / 100.0
    val top = canvasHeight * region.boundingBox.topPercent / 100.0
    val width = canvasWidth * region.boundingBox.widthPercent / 100.0
    val height = canvasHeight * region.boundingBox.heightPercent / 100.0
    val baseShape = if (region.shape == "ellipse") {
        Ellipse2D.Double(left, top, width, height)
    } else {
        RoundRectangle2D.Double(
            left,
            top,
            width,
            height,
            max(width, height) * 0.36,
            max(width, height) * 0.36,
        )
    }

    val path = Path2D.Double(baseShape)
    if (region.rotationDegrees != 0.0) {
        val centerX = left + (width / 2.0)
        val centerY = top + (height / 2.0)
        path.transform(AffineTransform.getRotateInstance(Math.toRadians(region.rotationDegrees), centerX, centerY))
    }
    return path
}

private fun drawRegionLabel(
    graphics: Graphics2D,
    region: HighlightRegion,
    regionColor: Color,
    canvasWidth: Int,
    canvasHeight: Int,
) {
    val regionCenterX = (region.boundingBox.leftPercent + (region.boundingBox.widthPercent / 2.0)) / 100.0 * canvasWidth
    val regionCenterY = (region.boundingBox.topPercent + (region.boundingBox.heightPercent / 2.0)) / 100.0 * canvasHeight
    val regionWidth = region.boundingBox.widthPercent / 100.0 * canvasWidth
    val labelTitle = "${region.colorName}${region.label} ${region.annotationTitle}"
    val subtitle = buildRegionLabelSubtitle(region)
    val titleFont = Font(reportFontFamily, Font.BOLD, max(14, (canvasWidth * 0.018).roundToInt()))
    val subtitleFont = Font(reportFontFamily, Font.PLAIN, max(11, (canvasWidth * 0.0135).roundToInt()))

    graphics.font = titleFont
    val titleMetrics = graphics.getFontMetrics(titleFont)
    graphics.font = subtitleFont
    val subtitleMetrics = graphics.getFontMetrics(subtitleFont)

    val horizontalPadding = max(14, (canvasWidth * 0.015).roundToInt())
    val verticalPadding = max(10, (canvasWidth * 0.01).roundToInt())
    val labelWidth = max(titleMetrics.stringWidth(labelTitle), subtitleMetrics.stringWidth(subtitle)) + (horizontalPadding * 2)
    val labelHeight = titleMetrics.height + subtitleMetrics.height + (verticalPadding * 2) + 4
    val anchorRight = regionCenterX < canvasWidth * 0.58
    val preferredX = if (anchorRight) {
        regionCenterX + (regionWidth * 0.42) + (canvasWidth * 0.04)
    } else {
        regionCenterX - labelWidth - (regionWidth * 0.42) - (canvasWidth * 0.04)
    }
    val labelX = clampInt(
        preferredX.roundToInt(),
        minValue = max(16, (canvasWidth * 0.02).roundToInt()),
        maxValue = canvasWidth - labelWidth - max(16, (canvasWidth * 0.02).roundToInt()),
    )
    val labelY = clampInt(
        (regionCenterY - (labelHeight / 2.0)).roundToInt(),
        minValue = max(12, (canvasHeight * 0.02).roundToInt()),
        maxValue = canvasHeight - labelHeight - max(12, (canvasHeight * 0.02).roundToInt()),
    )
    val lineStartX = regionCenterX.roundToInt()
    val lineStartY = regionCenterY.roundToInt()
    val lineMidX = if (anchorRight) {
        clampInt((labelX - canvasWidth * 0.018).roundToInt(), 0, canvasWidth)
    } else {
        clampInt((labelX + labelWidth + canvasWidth * 0.018).roundToInt(), 0, canvasWidth)
    }
    val lineEndX = if (anchorRight) labelX else labelX + labelWidth
    val lineEndY = labelY + (labelHeight / 2)

    graphics.color = withAlpha(regionColor, 204)
    graphics.stroke = BasicStroke(max(2.2f, canvasWidth * 0.0018f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    graphics.drawLine(lineStartX, lineStartY, lineMidX, lineEndY)
    graphics.drawLine(lineMidX, lineEndY, lineEndX, lineEndY)

    graphics.composite = AlphaComposite.SrcOver.derive(0.84f)
    graphics.color = Color(2, 6, 23)
    graphics.fillRoundRect(labelX, labelY, labelWidth, labelHeight, 18, 18)
    graphics.composite = AlphaComposite.SrcOver
    graphics.color = withAlpha(regionColor, 176)
    graphics.stroke = BasicStroke(2f)
    graphics.drawRoundRect(labelX, labelY, labelWidth, labelHeight, 18, 18)

    graphics.font = titleFont
    graphics.color = regionColor
    graphics.drawString(labelTitle, labelX + horizontalPadding, labelY + verticalPadding + titleMetrics.ascent)

    graphics.font = subtitleFont
    graphics.color = Color(241, 245, 249)
    graphics.drawString(
        subtitle,
        labelX + horizontalPadding,
        labelY + verticalPadding + titleMetrics.height + subtitleMetrics.ascent + 2,
    )
}

private fun buildRegionLabelSubtitle(region: HighlightRegion): String {
    val base = buildString {
        append(region.location.ifBlank { "位置待复核" })
        region.severity.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
        if (region.coveragePercent > 0) {
            append(" · 覆盖 ")
            append(region.coveragePercent.roundToInt())
            append('%')
        }
    }
    return if (base.length > 36) "${base.take(35)}…" else base
}

private fun drawPreviewLegend(
    graphics: Graphics2D,
    legend: List<HighlightLegendItem>,
    canvasWidth: Int,
    canvasHeight: Int,
) {
    if (legend.isEmpty()) {
        return
    }

    val visibleLegend = legend.take(4)
    val titleFont = Font(reportFontFamily, Font.BOLD, max(13, (canvasWidth * 0.015).roundToInt()))
    val lineFont = Font(reportFontFamily, Font.PLAIN, max(11, (canvasWidth * 0.012).roundToInt()))
    val boxPadding = max(12, (canvasWidth * 0.012).roundToInt())
    val chipSize = max(10, (canvasWidth * 0.012).roundToInt())
    val rowGap = max(7, (canvasHeight * 0.008).roundToInt())

    graphics.font = titleFont
    val titleMetrics = graphics.getFontMetrics(titleFont)
    graphics.font = lineFont
    val lineMetrics = graphics.getFontMetrics(lineFont)

    val legendLines = visibleLegend.map { item ->
        "${item.colorName}: ${truncateText(item.meaning, 18)}"
    }
    val contentWidth = legendLines.maxOfOrNull { lineMetrics.stringWidth(it) } ?: 0
    val boxWidth = contentWidth + (boxPadding * 2) + chipSize + 18
    val boxHeight = boxPadding + titleMetrics.height + rowGap +
        ((lineMetrics.height + rowGap) * visibleLegend.size) + boxPadding
    val boxX = canvasWidth - boxWidth - max(14, (canvasWidth * 0.02).roundToInt())
    val boxY = max(14, (canvasHeight * 0.024).roundToInt())

    graphics.composite = AlphaComposite.SrcOver.derive(0.72f)
    graphics.color = Color(2, 6, 23)
    graphics.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 22, 22)
    graphics.composite = AlphaComposite.SrcOver
    graphics.color = Color(148, 163, 184)
    graphics.stroke = BasicStroke(2f)
    graphics.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 22, 22)

    graphics.font = titleFont
    graphics.color = Color(248, 250, 252)
    graphics.drawString("颜色图例", boxX + boxPadding, boxY + boxPadding + titleMetrics.ascent)

    var rowY = boxY + boxPadding + titleMetrics.height + rowGap + lineMetrics.ascent
    visibleLegend.forEachIndexed { index, item ->
        val rowColor = parseColor(item.colorHex, accentColor)
        val chipY = rowY - lineMetrics.ascent + ((lineMetrics.height - chipSize) / 2)
        graphics.color = rowColor
        graphics.fillRoundRect(boxX + boxPadding, chipY, chipSize, chipSize, chipSize, chipSize)
        graphics.color = withAlpha(rowColor, 216)
        graphics.drawRoundRect(boxX + boxPadding, chipY, chipSize, chipSize, chipSize, chipSize)

        graphics.font = lineFont
        graphics.color = Color(226, 232, 240)
        graphics.drawString(legendLines[index], boxX + boxPadding + chipSize + 10, rowY)
        rowY += lineMetrics.height + rowGap
    }
}

private fun scaleImageToFit(
    image: BufferedImage,
    maxWidth: Int,
    maxHeight: Int,
): BufferedImage {
    val widthScale = maxWidth.toDouble() / image.width.toDouble()
    val heightScale = maxHeight.toDouble() / image.height.toDouble()
    val scale = min(1.0, min(widthScale, heightScale))
    val targetWidth = max(1, (image.width * scale).roundToInt())
    val targetHeight = max(1, (image.height * scale).roundToInt())
    if (targetWidth == image.width && targetHeight == image.height && image.type == BufferedImage.TYPE_INT_RGB) {
        return image
    }

    val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = createGraphics(resized)
    graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null)
    graphics.dispose()
    return resized
}

private fun parseColor(hex: String, fallback: Color): Color {
    return runCatching { Color.decode(hex) }.getOrElse { fallback }
}

private fun withAlpha(color: Color, alpha: Int): Color {
    return Color(color.red, color.green, color.blue, alpha.coerceIn(0, 255))
}

private fun clampInt(value: Int, minValue: Int, maxValue: Int): Int {
    if (maxValue <= minValue) {
        return minValue
    }
    return value.coerceIn(minValue, maxValue)
}

private fun truncateText(text: String, maxLength: Int): String {
    if (text.length <= maxLength) {
        return text
    }
    return "${text.take(maxLength - 1)}…"
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
