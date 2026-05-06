package com.remodex.mobile.ui.turn

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnTimelineFollowBottomTest {
    @Test
    fun followsWhenLastVisibleIsNearBottom() {
        assertTrue(
            shouldFollowTimelineBottom(
                totalItemsCount = 20,
                lastVisibleItemIndex = 18,
                thresholdItems = 2,
            ),
        )
    }

    @Test
    fun doesNotFollowWhenUserHasScrolledUp() {
        assertFalse(
            shouldFollowTimelineBottom(
                totalItemsCount = 40,
                lastVisibleItemIndex = 20,
                thresholdItems = 2,
            ),
        )
    }

    @Test
    fun missingLayoutDoesNotForceFollowForExistingItems() {
        assertFalse(shouldFollowTimelineBottom(totalItemsCount = 12, lastVisibleItemIndex = null))
    }

    @Test
    fun emptyTimelineCanFollow() {
        assertTrue(shouldFollowTimelineBottom(totalItemsCount = 0, lastVisibleItemIndex = null))
    }
}
