package com.example.spacer.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SignupRequest(
    val username: String,
    val email: String,
    val password: String,
    val name: String? = null,
    val phoneNumber: String? = null,
    val dateOfBirth: String? = null,
    val allowUpdates: Boolean = true
)

data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class ProfileDto(
    @SerialName("id")
    val id: String,

    @SerialName("username")
    val username: String,

    @SerialName("email")
    val email: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("phone_number")
    val phoneNumber: String? = null,

    @SerialName("date_of_birth")
    val dateOfBirth: String? = null,

    @SerialName("allow_updates")
    val allowUpdates: Boolean = true
)