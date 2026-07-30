package com.chat360.chatbot.network.rest

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** Wraps a request body to report write progress - used for upload percentage in POC 6. */
class ProgressRequestBody(
    private val bytes: ByteArray,
    private val mediaType: MediaType?,
    private val onProgress: (percent: Int) -> Unit,
) : RequestBody() {
    override fun contentType() = mediaType
    override fun contentLength() = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                if (total > 0) onProgress(((written * 100) / total).toInt())
            }
        }
        val buffered = countingSink.buffer()
        buffered.write(bytes)
        buffered.flush()
    }
}
