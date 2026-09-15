package com.example.myaccounts.model

data class ActivityLog(
    val id: String,
    val type: String,
    val detail: String,
    val status: String = "success",
    val timestamp: Long = System.currentTimeMillis(),
    val device: String = "Android Device"
)
