package com.example.myaccounts.model

data class Transaction(
    val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val type: String, // "debit" or "credit"
    val amount: Double,
    val note: String = "",
    val date: String,
    val settlements: List<Settlement> = emptyList()
)
