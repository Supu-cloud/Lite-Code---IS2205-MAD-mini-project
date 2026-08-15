package com.texteditor.project.network

import com.google.gson.Gson
import com.texteditor.project.data.CompileRequest
import com.texteditor.project.data.CompileResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class CompilerClient {

    private val gson = Gson()

    suspend fun compile(url: String, code: String, stdin: String, ext: String): CompileResponse = withContext(Dispatchers.IO) {
        val requestBody = gson.toJson(CompileRequest(code, stdin, ext))
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            
            if (responseText.isBlank()) {
                CompileResponse(false, "", "Empty server response")
            } else {
                gson.fromJson(responseText, CompileResponse::class.java)
            }
        } catch (e: Exception) {
            CompileResponse(
                success = false,
                output = "",
                error = "Cannot connect to the LiteCode compiler server. Make sure the laptop server is running and that the phone and laptop are connected to the same Wi-Fi network.\n\nTechnical details: ${e.localizedMessage}"
            )
        } finally {
            conn?.disconnect()
        }
    }
}
