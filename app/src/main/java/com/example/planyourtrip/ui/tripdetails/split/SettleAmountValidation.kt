package com.example.planyourtrip.ui.tripdetails.split

import com.example.planyourtrip.ui.common.Money

/** Null when [input] is a valid payment of at most [openAgorot] (the whole debt or a part of it). */
fun validateSettleAmount(input: String, openAgorot: Long): SettleAmountError? {
    if (input.isBlank()) return SettleAmountError.EMPTY
    val agorot = Money.parsePositiveAgorot(input) ?: return SettleAmountError.INVALID
    return when {
        agorot > openAgorot -> SettleAmountError.TOO_MUCH
        else -> null
    }
}
