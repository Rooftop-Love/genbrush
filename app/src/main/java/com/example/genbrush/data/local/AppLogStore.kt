package com.example.genbrush.data.local

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.genbrush.BuildConfig
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a bounded, app-owned log file so users can export useful diagnostics
 * without requiring access to the device-wide Logcat buffer.
 */
object AppLogStore {
    private const val TAG = "GenBrush"
    private const val LOG_FILE_NAME = "recent.log"
    private const val MAX_FILE_BYTES = 512 * 1024
    private const val MAX_MESSAGE_CHARS = 4000
    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (logFile == null) {
                val directory = File(context.applicationContext.filesDir, "logs")
                if (!directory.exists()) directory.mkdirs()
                logFile = File(directory, LOG_FILE_NAME)
            }
        }
        info("Application started, version=${BuildConfig.VERSION_NAME}, sdk=${Build.VERSION.SDK_INT}")
    }

    fun info(message: String) {
        write("I", message)
    }

    fun warning(message: String) {
        write("W", message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        val details = throwable?.let { "\n${Log.getStackTraceString(it)}" }.orEmpty()
        write("E", message + details)
    }

    fun http(message: String) {
        write("HTTP", message)
    }

    /**
     * Writes a snapshot, including metadata helpful for diagnosis, to the
     * caller-provided SAF stream. The caller owns and closes the stream.
     */
    fun exportRecent(outputStream: OutputStream) {
        val snapshot = synchronized(lock) {
            logFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8).orEmpty()
        }
        val exportedAt = synchronized(lock) {
            timestampFormat.format(Date())
        }
        val header = buildString {
            appendLine("GenBrush recent logs")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
            appendLine("exportedAt=$exportedAt")
            appendLine()
        }
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(header)
            writer.write(snapshot.ifBlank { "No application logs recorded.\n" })
        }
    }

    internal fun sanitizeForTest(message: String): String = sanitize(message)

    private fun write(level: String, rawMessage: String) {
        val message = sanitize(rawMessage).take(MAX_MESSAGE_CHARS)
        val line = synchronized(lock) {
            "${timestampFormat.format(Date())} [$level] $message\n"
        }

        Log.println(
            when (level) {
                "E" -> Log.ERROR
                "W" -> Log.WARN
                else -> Log.INFO
            },
            TAG,
            message
        )

        synchronized(lock) {
            val file = logFile ?: return
            runCatching {
                file.appendText(line, Charsets.UTF_8)
                trimIfNeeded(file)
            }
        }
    }

    private fun trimIfNeeded(file: File) {
        if (file.length() <= MAX_FILE_BYTES) return
        val bytes = file.readBytes()
        val start = bytes.size - MAX_FILE_BYTES
        val retained = bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8)
        val firstLineBreak = retained.indexOf('\n')
        file.writeText(
            if (firstLineBreak >= 0) retained.substring(firstLineBreak + 1) else retained,
            Charsets.UTF_8
        )
    }

    private fun sanitize(message: String): String {
        return message
            .replace(Regex("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s,]+")) {
                it.groupValues[1] + "[REDACTED]"
            }
            .replace(Regex("(?i)(\"?api[_-]?key\"?\\s*[:=]\\s*[\"']?)[^\\s,\"'}]+")) {
                it.groupValues[1] + "[REDACTED]"
            }
            .replace(Regex("(?i)(\"?(?:access_token|refresh_token)\"?\\s*[:=]\\s*[\"']?)[^\\s,\"'}]+")) {
                it.groupValues[1] + "[REDACTED]"
            }
    }
}
