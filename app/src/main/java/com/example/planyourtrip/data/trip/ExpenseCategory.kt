package com.example.planyourtrip.data.trip

/**
 * Category of an expense. Persisted by [name], so entries may be reordered or appended freely,
 * but never renamed or removed without a migration (an unknown name falls back to [OTHER]).
 */
enum class ExpenseCategory {
    FLIGHTS, HOTELS, FOOD, SHOPPING, TRANSPORT, ATTRACTIONS, OTHER;

    companion object {
        fun fromName(name: String?): ExpenseCategory = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
