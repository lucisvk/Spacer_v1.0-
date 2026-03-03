package com.example.spacer.network

import com.example.spacer.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseManager {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Auth) {
            // Must match AndroidManifest intent-filter and Supabase dashboard redirect URLs.
            scheme = "spacer"
            host = "auth"
            // PKCE + deeplink redirect (recommended for mobile OAuth).
            flowType = FlowType.PKCE

            // Android: OAuth opens in Custom Tabs; callback is spacer://auth (see RedirectUrl).
            defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
}