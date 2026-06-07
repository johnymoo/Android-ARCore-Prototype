package com.johnymoo.arverify.net

import java.io.ByteArrayOutputStream

/** Builds a `multipart/form-data` body. Not thread-safe; build() may be called repeatedly. */
class MultipartBuilder(private val boundary: String) {
    private val parts = ByteArrayOutputStream()

    fun contentType(): String = "multipart/form-data; boundary=$boundary"

    fun addFormField(name: String, value: String): MultipartBuilder {
        parts.write(ascii("--$boundary\r\n"))
        parts.write(ascii("Content-Disposition: form-data; name=\"$name\"\r\n\r\n"))
        parts.write(value.toByteArray(Charsets.UTF_8))
        parts.write(ascii("\r\n"))
        return this
    }

    fun addFilePart(name: String, filename: String, contentType: String, bytes: ByteArray): MultipartBuilder {
        parts.write(ascii("--$boundary\r\n"))
        parts.write(ascii("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n"))
        parts.write(ascii("Content-Type: $contentType\r\n\r\n"))
        parts.write(bytes)
        parts.write(ascii("\r\n"))
        return this
    }

    /** Returns the full body including the closing boundary. Does not mutate internal state. */
    fun build(): ByteArray = parts.toByteArray() + ascii("--$boundary--\r\n")

    private fun ascii(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)
}
