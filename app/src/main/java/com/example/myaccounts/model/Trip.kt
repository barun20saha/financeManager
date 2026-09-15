package com.example.myaccounts.model

data class Trip(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val members: List<String> = listOf("Me"),
    val expenses: List<TripExpense> = emptyList()
)
