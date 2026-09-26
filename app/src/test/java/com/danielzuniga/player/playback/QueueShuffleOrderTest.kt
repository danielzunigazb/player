package com.danielzuniga.player.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class QueueShuffleOrderTest {

    private fun QueueShuffleOrder.order() = toList()

    @Test
    fun startsWithTheGivenItemAndCoversEveryOne() {
        repeat(20) { seed ->
            val order = QueueShuffleOrder.startingWith(first = 4, length = 10, random = Random(seed))
            assertEquals(4, order.firstIndex)
            assertEquals((0 until 10).toList(), order.order().sorted())
        }
    }

    @Test
    fun walksTheOrderBothWays() {
        val order = QueueShuffleOrder.startingWith(first = 2, length = 4, random = Random(1))
        val list = order.order()
        assertEquals(list[1], order.getNextIndex(list[0]))
        assertEquals(list[0], order.getPreviousIndex(list[1]))
        assertEquals(C.INDEX_UNSET, order.getNextIndex(list.last()))
        assertEquals(C.INDEX_UNSET, order.getPreviousIndex(list.first()))
        assertEquals(list.last(), order.lastIndex)
    }

    @Test
    fun playNextGoesRightAfterTheCurrentItem() {
        // Current item is list index 2; "play next" inserts at list index 3.
        val order = QueueShuffleOrder.startingWith(first = 2, length = 6, random = Random(3))
            .cloneAndInsert(3, 2) as QueueShuffleOrder
        assertEquals(listOf(2, 3, 4), order.order().take(3))
        assertEquals((0 until 8).toList(), order.order().sorted())
    }

    @Test
    fun appendedItemsGoLast() {
        val order = QueueShuffleOrder.startingWith(first = 0, length = 5, random = Random(5))
            .cloneAndInsert(5, 2) as QueueShuffleOrder
        assertEquals(listOf(5, 6), order.order().takeLast(2))
    }

    @Test
    fun removingShiftsLaterIndices() {
        val start = QueueShuffleOrder.startingWith(first = 1, length = 5, random = Random(7))
        val removed = start.cloneAndRemove(1, 3) as QueueShuffleOrder
        val expected = start.order().filter { it !in 1..2 }.map { if (it >= 3) it - 2 else it }
        assertEquals(expected, removed.order())
        assertEquals(0, start.cloneAndClear().length)
    }

    @Test
    fun aNewQueueIsShuffledFromEmpty() {
        val order = QueueShuffleOrder(0, Random(9)).cloneAndInsert(0, 8) as QueueShuffleOrder
        assertEquals((0 until 8).toList(), order.order().sorted())
    }
}
