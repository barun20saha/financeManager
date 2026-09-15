package com.example.myaccounts.model

data class TripExpense(
    val id: String,
    val description: String,
    val amount: Double,
    val paidBy: String,
    val participants: List<String>,
    val splitType: String = "equal", // "equal" or "manual"
    val shares: Map<String, Double> = emptyMap(),
    val date: String,
    val createdAt: Long = System.currentTimeMillis()
)
