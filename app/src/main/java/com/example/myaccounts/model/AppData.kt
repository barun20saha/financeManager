package com.example.myaccounts.model

data class AppData(
    val customers: List<Customer> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val transactions: Map<String, List<Transaction>> = emptyMap(),
    val trips: List<Trip> = emptyList(),
    val logs: List<ActivityLog> = emptyList(),
    val settings: SecuritySettings = SecuritySettings()
)
