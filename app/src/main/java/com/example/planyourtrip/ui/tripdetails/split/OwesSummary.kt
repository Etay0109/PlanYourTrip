package com.example.planyourtrip.ui.tripdetails.split

import com.example.planyourtrip.ui.common.split.Transfer

/** The personal line at the top of "Who owes whom", from my own net balance. Display only. */
sealed interface OwesSummary {
    /** "You're owed ₪820.49". */
    data class Owed(val amountAgorot: Long) : OwesSummary

    /** "You owe ₪150.00". */
    data class Owe(val amountAgorot: Long) : OwesSummary

    /** "You're all square": I'm at zero while others still owe each other. */
    data object AllSquare : OwesSummary
}

/**
 * My net balance as the open [transfers] show it (received − paid; the transfers settle every balance exactly, so
 * this is my balance in whole agorot). Null when nothing is open: the card shows "All settled up" instead.
 */
fun owesSummary(transfers: List<Transfer>, me: String): OwesSummary? {
    if (transfers.isEmpty()) return null
    val net = transfers.sumOf {
        when (me) {
            it.toKey -> it.amountAgorot
            it.fromKey -> -it.amountAgorot
            else -> 0L
        }
    }
    return when {
        net > 0 -> OwesSummary.Owed(net)
        net < 0 -> OwesSummary.Owe(-net)
        else -> OwesSummary.AllSquare
    }
}
