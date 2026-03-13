package com.example.spacer.profile

/** Single place for the label shown on Home, session cache, and profile header. */
fun displayLabelFromProfile(profile: ProfileRow): String {
    val u = profile.username?.trim().orEmpty()
    if (u.isNotEmpty()) return u
    val n = profile.fullName?.trim().orEmpty()
    if (n.isNotEmpty()) return n
    val e = profile.email?.trim().orEmpty()
    if (e.isNotEmpty()) return e.substringBefore("@")
    return ""
}
