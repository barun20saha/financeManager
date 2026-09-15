package com.example.myaccounts.model

data class TripMember(
    val id: String,
    val name: String,
    val joinedAt: Long = System.currentTimeMillis()
)
