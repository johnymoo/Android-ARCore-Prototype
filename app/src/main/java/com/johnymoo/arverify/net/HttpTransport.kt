package com.johnymoo.arverify.net

/** Minimal HTTP response (status code + body text). */
data class HttpResponse(val code: Int, val body: String)

/** Abstraction over the network so the uploader is unit-testable with a fake. */
interface HttpTransport {
    /** POST [body] to [url] with the given [contentType]; throws on transport failure. */
    fun post(url: String, contentType: String, body: ByteArray): HttpResponse
}
