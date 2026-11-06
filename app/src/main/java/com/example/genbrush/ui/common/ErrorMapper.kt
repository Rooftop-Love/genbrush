package com.example.genbrush.ui.common

import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ProviderException
import com.example.genbrush.ui.localization.AppStrings
import com.example.genbrush.ui.localization.resolveAppStrings

fun mapError(e: Throwable): String {
    val s = resolveAppStrings(PreferencesManager.LANGUAGE_SYSTEM)
    return mapError(e, s)
}

fun mapError(e: Throwable, s: AppStrings): String {
    if (e is ProviderException) {
        if (e.isContentSafety) {
            return s.errContentSafety
        }
        return "[${e.code}] ${e.message}"
    }

    val msg = e.message ?: s.errUnknown
    return when {
        msg.contains("API key", ignoreCase = true) -> msg
        msg.contains("InvalidApiKey", ignoreCase = true) -> s.errInvalidKey
        msg.contains("Throttling", ignoreCase = true) || msg.contains("429") -> s.errRateLimit
        msg.contains("DataInspectionFailed", ignoreCase = true) -> s.errContentSafety
        msg.contains("timeout", ignoreCase = true) || msg.contains("connect", ignoreCase = true) -> s.errNetwork
        msg.contains("HTTP 5", ignoreCase = true) -> s.errServer
        msg.contains("cleartext", ignoreCase = true) -> s.errCleartext
        else -> msg
    }
}
