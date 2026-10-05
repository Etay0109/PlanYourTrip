package com.example.planyourtrip.data.user

private fun nameParts(fullName: String): List<String> =
    fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** "Etay Zahavi" → "Etay". Empty when the name is blank. */
fun firstName(fullName: String): String = nameParts(fullName).firstOrNull().orEmpty()

/** "Etay Zahavi" → "EZ", "Etay" → "E", blank → "?". Uses the first and last word. */
fun initials(fullName: String): String {
    val parts = nameParts(fullName)
    if (parts.isEmpty()) return "?"
    val letters = if (parts.size == 1) listOf(parts.first()) else listOf(parts.first(), parts.last())
    return letters.joinToString("") { it.first().uppercase() }
}
