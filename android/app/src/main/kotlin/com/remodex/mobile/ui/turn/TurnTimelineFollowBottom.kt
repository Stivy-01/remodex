package com.remodex.mobile.ui.turn

internal fun shouldFollowTimelineBottom(
    totalItemsCount: Int,
    lastVisibleItemIndex: Int?,
    thresholdItems: Int = 2,
): Boolean {
    if (totalItemsCount <= 0) return true
    val lastVisible = lastVisibleItemIndex ?: return false
    val threshold = thresholdItems.coerceAtLeast(0)
    return lastVisible >= totalItemsCount - 1 - threshold
}
