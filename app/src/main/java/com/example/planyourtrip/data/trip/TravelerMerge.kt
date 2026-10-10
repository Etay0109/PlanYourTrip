package com.example.planyourtrip.data.trip

/**
 * Merging a name-only traveler into a member of the shared trip (the traveler was added by hand before that person
 * joined with their account): their expenses, splits and settlements move to the member's uid, settlements between
 * the two are dropped (money one person "paid themselves") and the traveler is removed, in one Room transaction that
 * syncs like any other change. The plan itself is pure (`ui/common/split/TravelerMerging.kt`).
 */
data class TravelerMergeInput(
    /** The name-only traveler's uuid (their key in expenses and settlements). */
    val travelerKey: String,
    /** The member's account uid. */
    val memberKey: String,
    /** The signed-in member (the list order starts from them, see `travelerOrder`). */
    val me: String,
    val memberIds: List<String>,
    val travelers: List<Traveler>,
    val expenses: List<Expense>,
    val settlements: List<Settlement>,
)

/** What a merge writes: the changed expenses and settlements (new keys), and the settlements it deletes. */
data class TravelerMergeChanges(
    val expenses: List<Expense>,
    val settlements: List<Settlement>,
    val deletedSettlementIds: List<Long>,
)

/** Whether the traveler can be merged into the member without changing any balance. */
sealed interface TravelerMergePlan {
    data class Ready(val changes: TravelerMergeChanges) : TravelerMergePlan

    data class Blocked(val reason: MergeBlock) : TravelerMergePlan
}

/** Why a merge would change the numbers, so it isn't offered. */
enum class MergeBlock {
    /** An expense is split between both of them: one person would lose a share, so everyone else's share changes. */
    SHARED_TOGETHER,

    /** An uneven split's extra agorot would move to someone else (the list order changes). */
    BALANCES_WOULD_CHANGE,
}

enum class MergeTravelerResult {
    MERGED,

    /** Something changed since the suggestion was shown and the merge would now change a balance: nothing written. */
    BLOCKED,

    /** The traveler, the trip or the member is gone. */
    NOT_FOUND,
}
