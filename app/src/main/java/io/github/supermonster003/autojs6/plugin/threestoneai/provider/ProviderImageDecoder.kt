package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.graphics.BitmapFactory
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationImage
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiProviderImagePolicy

/** A Provider independently verifies the bytes received from its authenticated host. */
internal object ProviderImageDecoder {
    fun materialize(part: AiContentPart, readDescriptor: (Int, Long) -> ByteArray): GenerationImage {
        val bytes = AiProviderImagePolicy.materialize(part, readDescriptor)
        try {
            val metadata = requireNotNull(part.image)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outMimeType == part.payload.mimeType && bounds.outWidth == metadata.width && bounds.outHeight == metadata.height) {
                "Decoded image does not match its declaration"
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, maxOf(metadata.width, metadata.height) / 128)
            }
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Invalid image" }
            bitmap.recycle()
            return GenerationImage(bytes, part.payload.mimeType, metadata)
        } finally { bytes.fill(0) }
    }
}
