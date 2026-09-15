package com.example.myaccounts.model

data class SecuritySettings(
    val appLockEnabled: Boolean = false,
    val biometricsEnabled: Boolean = false,
    val pinSalt: String? = null,
    val pinHash: String? = null
)
