package com.bidzi.app

import android.app.Application
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

import java.io.IOException

class MyApplication : Application() {

    companion object {
        lateinit var instance: MyApplication
            private set

        lateinit var supabase: SupabaseClient
            private set

        private const val TAG = "MyApplication"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeSupabase()
    }

    private fun initializeSupabase() {
        try {
            // Create HTTP client with retry and timeout configuration
            val httpClient = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000L
                    connectTimeoutMillis = 30_000L
                    socketTimeoutMillis = 30_000L
                }
                install(HttpRequestRetry) {
                    maxRetries = 3
                    retryOnExceptionIf { _, cause ->
                        cause is IOException
                    }
                    delayMillis { attempt ->
                        (1000L * Math.pow(2.0, (attempt - 1).toDouble())).toLong()
                    }
                }
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            }

            // Get Supabase credentials from strings.xml
            val supabaseUrl = getString(R.string.supabase_url)
            val supabaseKey = getString(R.string.supabase_key)

            // Create Supabase client
            supabase = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseKey
            ) {
                install(Postgrest)
                install(Realtime) // Optional: For real-time updates
                httpEngine = httpClient.engine
            }

            Log.d(TAG, "SupabaseClient initialized successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase: ${e.message}", e)
        }
    }
}
