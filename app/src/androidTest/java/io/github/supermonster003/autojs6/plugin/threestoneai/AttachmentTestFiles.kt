package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.OutputStream

/**
 * Small files the attachment tests generate for themselves (P4.4, D131): nothing is read from the
 * device's pictures or storage. They live in the app cache and go with [close].
 */
internal class AttachmentTestFiles(context: Context) : Closeable {
    val directory = File(context.cacheDir, "p4-attachment-test").apply {
        deleteRecursively()
        mkdirs()
    }

    /** A PNG whose left half is red and right half blue. */
    val image: File = write(IMAGE_NAME) { output -> output.write(imageBytes()) }

    /** One PDF page whose only text names the code word. */
    val pdf: File = write(PDF_NAME) { output -> output.write(pdfBytes()) }

    val text: File = write(TEXT_NAME) { output -> output.write(TEXT.toByteArray(Charsets.UTF_8)) }

    fun uri(file: File): Uri = Uri.fromFile(file)

    private fun write(name: String, block: (OutputStream) -> Unit): File =
        File(directory, name).also { file -> file.outputStream().use(block) }

    override fun close() {
        directory.deleteRecursively()
    }

    companion object {
        const val IMAGE_NAME = "colors.png"
        const val PDF_NAME = "brief.pdf"
        const val TEXT_NAME = "notes.txt"
        const val IMAGE_WIDTH = 320
        const val IMAGE_HEIGHT = 200
        const val CODE_WORD = "PELICAN"
        const val FRUIT = "mango"
        const val TEXT = "The secret fruit is $FRUIT.\n"
        private const val PDF_WIDTH_POINTS = 360
        private const val PDF_HEIGHT_POINTS = 200

        fun imageBytes(): ByteArray {
            val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            try {
                Canvas(bitmap).apply {
                    drawColor(Color.BLUE)
                    drawRect(0f, 0f, IMAGE_WIDTH / 2f, IMAGE_HEIGHT.toFloat(), Paint().apply { color = Color.RED })
                }
                return java.io.ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }

        fun pdfBytes(): ByteArray {
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH_POINTS, PDF_HEIGHT_POINTS, 1).create())
                page.canvas.drawText(
                    "Code word: $CODE_WORD",
                    40f,
                    110f,
                    Paint().apply {
                        color = Color.BLACK
                        textSize = 32f
                        isAntiAlias = true
                    },
                )
                document.finishPage(page)
                return java.io.ByteArrayOutputStream().use { output ->
                    document.writeTo(output)
                    output.toByteArray()
                }
            } finally {
                document.close()
            }
        }
    }
}
