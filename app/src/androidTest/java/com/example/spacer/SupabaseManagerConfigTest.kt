package com.example.spacer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.spacer.network.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@RunWith(AndroidJUnit4::class)
class SupabaseManagerConfigTest {

    @Test
    fun supabaseBuildConfig_hasValidUrlAndKey() {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_KEY.trim()

        assertTrue("SUPABASE_URL must not be blank", url.isNotBlank())
        assertTrue("SUPABASE_KEY must not be blank", key.isNotBlank())

        val parsed = URI(url)
        assertTrue("SUPABASE_URL must be https", parsed.scheme.equals("https", ignoreCase = true))
        assertTrue("SUPABASE_URL host must not be blank", !parsed.host.isNullOrBlank())
    }

    @Test
    fun supabaseClient_hasRequiredPluginsInstalled() {
        val client = SupabaseManager.client

        assertNotNull(client.auth)
        assertNotNull(client.postgrest)
        assertNotNull(client.realtime)
        assertNotNull(client.storage)
    }
}
