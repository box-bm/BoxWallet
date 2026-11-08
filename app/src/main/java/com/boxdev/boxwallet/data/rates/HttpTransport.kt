package com.boxdev.boxwallet.data.rates

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface HttpResult {
    data class Ok(val body: String) : HttpResult
    data class Status(val code: Int) : HttpResult
    data class Failure(val error: IOException) : HttpResult
}

fun interface HttpTransport {
    fun get(url: String): HttpResult
}

class UrlConnectionTransport : HttpTransport {
    override fun get(url: String): HttpResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                HttpResult.Ok(conn.inputStream.bufferedReader().use { it.readText() })
            } else {
                HttpResult.Status(code)
            }
        } catch (e: IOException) {
            HttpResult.Failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
