package com.example.planyourtrip.data.trip

/**
 * Kind of a planned activity. Stored by [name] in Room, so entries can be reordered or appended, but never
 * renamed or removed without a migration (an unknown name falls back to [OTHER]).
 */
enum class ActivityType { ACTIVITY, DINING, TRANSPORT, LODGING, OTHER }
