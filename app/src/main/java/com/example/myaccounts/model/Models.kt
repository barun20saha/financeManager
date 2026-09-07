package com.example.myaccounts.model

data class Customer(
    val id: String,
    val name: String,
    val color: String
)

data class Supplier(
    val id: String,
    val name: String,
    val color: String
)

data class Settlement(
    val amount: Double,
    val date: String,
    val note: String = ""
)

data class Transaction(
    val id: String,
    val createdAt: Long,
    val type: String, // "debit" or "credit"
    val amount: Double,
    val note: String = "",
    val date: String,
    val settlements: List<Settlement> = emptyList()
)

data class TripMember(
    val id: String,
    val name: String,
    val joinedAt: Long = System.currentTimeMillis()
)

data class ExpenseShare(
    val memberName: String,
    val amount: Double
)

data class TripExpense(
    val id: String,
    val description: String,
    val amount: Double,
    val paidBy: String,
    val participants: List<String>,
    val splitType: String, // "equal" or "manual"
    val shares: Map<String, Double> = emptyMap(),
    val date: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Trip(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val members: List<String> = listOf("Me"),
    val expenses: List<TripExpense> = emptyList()
)

data class ActivityLog(
    val id: String,
    val type: String,
    val detail: String,
    val status: String = "success",
    val timestamp: Long = System.currentTimeMillis(),
    val device: String = "Android Device"
)
