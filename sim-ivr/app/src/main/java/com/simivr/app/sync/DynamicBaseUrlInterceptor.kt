package com.simivr.app.sync

import com.simivr.app.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Retrofit needs a base URL at construction time, but the CRM base URL is a user-editable Settings
 * value. This interceptor keeps Retrofit wired to a placeholder host and rewrites every outgoing
 * request's scheme/host/port (and prefixes any base path) to whatever is currently configured,
 * plus attaches the API key.
 */
class DynamicBaseUrlInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = runBlocking { settingsRepository.settings.first() }
        val original = chain.request()
        val configuredBase = settings.crmBaseUrl.trim().toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val newUrl = original.url.newBuilder()
            .scheme(configuredBase.scheme)
            .host(configuredBase.host)
            .port(configuredBase.port)
            .encodedPath(configuredBase.encodedPath.trimEnd('/') + original.url.encodedPath)
            .build()

        val requestBuilder = original.newBuilder().url(newUrl)
        if (settings.crmApiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${settings.crmApiKey}")
            requestBuilder.header("X-Api-Key", settings.crmApiKey)
        }
        return chain.proceed(requestBuilder.build())
    }
}
