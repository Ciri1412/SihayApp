package com.genesis.sihay.data.server

import android.graphics.BitmapFactory
import android.util.Log
import com.genesis.sihay.AnalyzedFailedException
import com.genesis.sihay.TryAgainException
import com.genesis.sihay.data.analyzer.EggAnalyzer
import com.genesis.sihay.data.model.CaptureSource
import com.genesis.sihay.data.model.EggAnalysisRecord
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.HashMap

/** JSON response for ESP32: egg vs non-egg, fertile/infertile, and sort action. */
data class Esp32Response(
    val status: String,       // FERTILE | INFERTILE | NON_EGG | UNCLEAR | ERROR
    val confidence: Float,
    val action: String,       // LEFT | RIGHT | NONE
    val message: String,
    val objectType: String = "egg"  // "egg" | "non_egg" | "unclear"
)

/**
 * In-app HTTP server using NanoHTTPD. Binds to 0.0.0.0 so the ESP32 on the LAN can reach it.
 * More reliable on Android than Ktor for this use case.
 */
class SihayServer(
    private val analyzer: EggAnalyzer,
    private val onResult: (Esp32Response) -> Unit,
    private val onRecord: (EggAnalysisRecord) -> Unit,
    private val onError: (String) -> Unit
) {
    private var nano: NanoHTTPD? = null
    val port = 8080

    fun start() {
        if (nano != null) return

        nano = object : NanoHTTPD(port) {
            override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
                val uri = session.uri
                val method = session.method

                when {
                    uri == "/ping" && method == Method.GET -> {
                        Log.d("SihayServer", "GET /ping received")
                        return newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK,
                            "application/json",
                            """{"status":"ok","app":"Sihay"}"""
                        )
                    }
                    uri == "/analyze" && method == Method.POST -> {
                        return handleAnalyze(session)
                    }
                    else -> {
                        return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found")
                    }
                }
            }
        }.apply {
            try {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT)
                Log.i("SihayServer", "NanoHTTPD started on 0.0.0.0:$port (ESP32 can POST to http://<this-phone-ip>:$port/analyze)")
            } catch (e: Exception) {
                Log.e("SihayServer", "Failed to start server: ${e.message}", e)
                throw e
            }
        }
    }

    private fun handleAnalyze(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)

            // ESP32 sends multipart with name="image"; temp file path is in files (param name -> path)
            var response: Esp32Response? = null
            for ((_, tempPath) in files) {
                val file = File(tempPath)
                if (!file.exists()) continue
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                file.delete()
                if (bitmap == null) continue

                try {
                    val record = runBlocking { analyzer.analyze(bitmap, CaptureSource.ESP32) }
                    val action = when (record.status.name) {
                        "FERTILE" -> "LEFT"
                        else -> "RIGHT"
                    }
                    response = Esp32Response(
                        status = record.status.name,
                        confidence = record.confidence,
                        action = action,
                        message = "Success",
                        objectType = "egg"
                    )
                    onResult(response)
                    onRecord(record)
                } catch (e: AnalyzedFailedException) {
                    response = Esp32Response(
                        status = "NON_EGG",
                        confidence = 0f,
                        action = "NONE",
                        message = e.message ?: "Object is not a candled egg.",
                        objectType = "non_egg"
                    )
                    onResult(response)
                } catch (e: TryAgainException) {
                    response = Esp32Response(
                        status = "UNCLEAR",
                        confidence = 0f,
                        action = "NONE",
                        message = e.message ?: "Egg not clear. Try again.",
                        objectType = "unclear"
                    )
                    onResult(response)
                } catch (e: Exception) {
                    onError(e.message ?: "Analysis Error")
                    response = Esp32Response(
                        status = "ERROR",
                        confidence = 0f,
                        action = "NONE",
                        message = e.message ?: "Error",
                        objectType = "egg"
                    )
                }
                break
            }

            if (response != null) {
                val json = Gson().toJson(response)
                NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json)
            } else {
                NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "No image received")
            }
        } catch (e: Exception) {
            Log.e("SihayServer", "Analyze error", e)
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "Error")
        }
    }

    fun stop() {
        try {
            nano?.stop()
            nano = null
        } catch (e: Exception) {
            Log.e("SihayServer", "Stop error", e)
        }
    }
}
