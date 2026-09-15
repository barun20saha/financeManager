package com.example.myaccounts.model

data class ActivityEntry(
    val date: String,
    val count: Int,
    val totalAmount: Double = 0.0,
    val description: String = ""
)
