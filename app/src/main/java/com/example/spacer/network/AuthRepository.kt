package com.example.spacer.network

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Must match [io.github.jan.supabase.auth.Auth] scheme/host and Supabase Dashboard → Auth → URL Configuration → Redirect URLs. */
private const val OAUTH_REDIRECT_URL = "spacer://auth"

class AuthRepository {

    private val supabase = SupabaseManager.client

    private fun configErrorOrNull(): Exception? {
        if (com.example.spacer.BuildConfig.SUPABASE_URL.isBlank() ||
            com.example.spacer.BuildConfig.SUPABASE_KEY.isBlank()
        ) {
            return Exception(
                "Supabase config missing. Set SUPABASE_URL and SUPABASE_KEY in local.properties, gradle.properties, or environment variables."
            )
        }
        return null
    }

    suspend fun signup(request: SignupRequest): Result<String> {
        configErrorOrNull()?.let { return Result.failure(it) }
        return try {
            if (request.email.isBlank() || request.password.isBlank()) {
                return Result.failure(Exception("Email and password are required"))
            }
            if (request.username.isBlank()) {
                return Result.failure(Exception("Username is required"))
            }

            if (request.password.length < 6) {
                return Result.failure(Exception("Password must be at least 6 characters"))
            }

            supabase.auth.signUpWith(Email) {
                email = request.email
                password = request.password
                data = buildJsonObject {
                    put("username", request.username)
                    // Display name for profile = chosen username (matches DB `name` + trigger).
                    put("name", request.username.trim())
                    put("full_name", request.username.trim())
                    request.phoneNumber?.takeIf { it.isNotBlank() }?.let { put("phone_number", it) }
                    request.dateOfBirth?.takeIf { it.isNotBlank() }?.let { put("date_of_birth", it) }
                    put("allow_updates", JsonPrimitive(request.allowUpdates))
                }
            }

            // If a session is available immediately after signup, ensure username/full_name are
            // persisted on profiles so Profile screens can fetch them reliably.
            val currentUser = supabase.auth.currentUserOrNull()
            if (currentUser != null) {
                supabase.from("profiles").upsert(
                    mapOf(
                        "id" to currentUser.id,
                        "email" to request.email,
                        "username" to request.username.trim(),
                        "name" to request.username.trim(),
                        "about_me" to ""
                    )
                )
            }

            Result.success("Account created. Check your email to confirm your account if required.")
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("already registered", ignoreCase = true) == true ->
                    "This email is already registered."
                e.message?.contains("password", ignoreCase = true) == true ->
                    "Please choose a stronger password."
                e.message?.contains("signup is disabled", ignoreCase = true) == true ->
                    "New sign-ups are turned off right now. Please try again later."
                else -> "Couldn't create your account. Please try again."
            }
            Result.failure(Exception(message))
        }
    }

    /**
     * Starts the OAuth flow (Custom Tab → provider → deeplink).
     * Enable each provider in Supabase (Authentication → Providers): Google, Discord, GitHub, etc.
     * Add redirect URL [OAUTH_REDIRECT_URL] under Authentication → URL Configuration → Redirect URLs
     * (same value for every provider the app uses).
     */
    suspend fun signInWithOAuth(provider: OAuthProvider): Result<Unit> {
        configErrorOrNull()?.let { return Result.failure(it) }
        return try {
            supabase.auth.signInWith(provider, redirectUrl = OAUTH_REDIRECT_URL)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Email/password signup writes [profiles] explicitly; OAuth users need the same row for RLS queries.
     */
    suspend fun ensureProfileAfterOAuthSignIn(): Result<Unit> {
        configErrorOrNull()?.let { return Result.failure(it) }
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.success(Unit)
            val email = user.email?.trim().orEmpty()
            val metaName = user.userMetadata?.get("full_name")?.toString()?.trim('"')?.trim().orEmpty()
                .ifEmpty {
                    user.userMetadata?.get("name")?.toString()?.trim('"')?.trim().orEmpty()
                }
            val usernameBase = if (email.isNotEmpty()) email.substringBefore("@").lowercase()
            else user.id.take(8)
            val display = metaName.ifEmpty { usernameBase }
            val row = buildMap<String, Any?> {
                put("id", user.id)
                put("username", usernameBase)
                put("name", display)
                put("about_me", "")
                if (email.isNotEmpty()) put("email", email)
            }
            supabase.from("profiles").upsert(row)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(request: LoginRequest): Result<String> {
        configErrorOrNull()?.let { return Result.failure(it) }
        return try {
            if (request.email.isBlank() || request.password.isBlank()) {
                return Result.failure(Exception("Email and password are required"))
            }

            supabase.auth.signInWith(Email) {
                email = request.email
                password = request.password
            }

            Result.success("Login successful")

        } catch (e: Exception) {

            val message = when {
                e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                    "Incorrect email or password"

                e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                    "Please verify your email before logging in"

                e.message?.contains("User not confirmed", ignoreCase = true) == true ->
                    "Please verify your email before logging in"

                else -> "Couldn't sign you in right now. Please try again."
            }

            Result.failure(Exception(message))
        }
    }

    suspend fun resolveCurrentDisplayName(): String? {
        return try {
            val user = supabase.auth.currentUserOrNull() ?: return null

            val metadataName = user.userMetadata
                ?.get("name")
                ?.toString()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }

            val fullName = user.userMetadata
                ?.get("full_name")
                ?.toString()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }

            val emailPrefix = user.email
                ?.substringBefore("@")
                ?.takeIf { it.isNotBlank() }

            metadataName ?: fullName ?: emailPrefix
        } catch (_: Exception) {
            null
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}