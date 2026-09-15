package com.example.myaccounts.model

data class Settlement(
    val amount: Double,
    val date: String,
    val note: String = ""
)
