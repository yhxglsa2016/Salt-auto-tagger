package com.salt.autotagger.spw

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object JsonSupport {
    private val mapper = jacksonObjectMapper()

    fun parse(text: String): JsonNode? = runCatching { mapper.readTree(text) }.getOrNull()

    fun write(value: Any): String = mapper.writeValueAsString(value)
}

object HttpSupport {
    const val browserAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val connectTimeoutMillis = 5_000
    private const val readTimeoutMillis = 8_000
    private val lastFailureReason = ThreadLocal<String?>()

    fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    fun consumeLastFailureReason(): String? {
        val reason = lastFailureReason.get()
        lastFailureReason.remove()
        return reason
    }

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String? {
        return send(
            method = "GET",
            url = url,
            headers = mapOf("Accept" to "*/*") + headers
        )
    }

    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonNode? {
        val text = getText(url, headers) ?: return null
        val parsed = JsonSupport.parse(text)
        if (parsed == null) {
            lastFailureReason.set("parse_failed")
        }
        return parsed
    }

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): JsonNode? {
        return send(
            method = "POST",
            url = url,
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/json; charset=UTF-8"
            ) + headers,
            body = body.toByteArray(StandardCharsets.UTF_8)
        )?.let { text ->
            val parsed = JsonSupport.parse(text)
            if (parsed == null) {
                lastFailureReason.set("parse_failed")
            }
            parsed
        }
    }

    fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): JsonNode? {
        val encodedForm = form.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return send(
            method = "POST",
            url = url,
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/x-www-form-urlencoded"
            ) + headers,
            body = encodedForm.toByteArray(StandardCharsets.UTF_8)
        )?.let { text ->
            val parsed = JsonSupport.parse(text)
            if (parsed == null) {
                lastFailureReason.set("parse_failed")
            }
            parsed
        }
    }

    private fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray? = null
    ): String? {
        lastFailureReason.remove()
        val connection = openConnection(url) ?: return null
        return try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", browserAgent)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(body)
                }
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                lastFailureReason.set("http_status_$statusCode")
                null
            } else {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        } catch (_: SocketTimeoutException) {
            lastFailureReason.set("timeout")
            null
        } catch (error: IOException) {
            lastFailureReason.set(error.javaClass.simpleName.takeIf { it.isNotBlank() } ?: "io_error")
            null
        } catch (_: IllegalArgumentException) {
            lastFailureReason.set("invalid_url")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection? {
        return try {
            val connection = URI.create(url).toURL().openConnection() as? HttpURLConnection
            if (connection == null) {
                lastFailureReason.set("not_http_url")
            }
            connection
        } catch (_: IllegalArgumentException) {
            lastFailureReason.set("invalid_url")
            null
        } catch (_: IOException) {
            lastFailureReason.set("io_error")
            null
        }
    }
}

object Hashing {
    fun md5(text: String): String = digest("MD5", text.toByteArray(StandardCharsets.UTF_8))
    fun sha1(text: String): String = digest("SHA-1", text.toByteArray(StandardCharsets.UTF_8))

    private fun digest(algorithm: String, bytes: ByteArray): String {
        val md = MessageDigest.getInstance(algorithm)
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

object Randoms {
    fun alphaNumeric(length: Int): String {
        val charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return buildString(length) {
            repeat(length) {
                append(charset.random())
            }
        }
    }

    fun uuid(): String = UUID.randomUUID().toString()
}
