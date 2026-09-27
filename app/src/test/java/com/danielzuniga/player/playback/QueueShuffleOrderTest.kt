package com.danielzuniga.player.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun reallyShuffles() {
        val order = QueueShuffleOrder.startingWith(first = 0, length = 20, random = Random(11)).order()
        assertNotEquals((0 until 20).toList(), order)
        assertNotEquals(order, QueueShuffleOrder.startingWith(first = 0, length = 20, random = Random(12)).order())
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
        assertTrue(order.fresh)
    }

    @Test
    fun onlyANewListIsFresh() {
        val start = QueueShuffleOrder.startingWith(first = 2, length = 6, random = Random(13))
        assertFalse(start.fresh)
        assertFalse((start.cloneAndRemove(2, 3) as QueueShuffleOrder).fresh)
        assertFalse((start.cloneAndInsert(3, 1) as QueueShuffleOrder).fresh)
        assertFalse((start.cloneAndInsert(6, 1) as QueueShuffleOrder).fresh)
        assertFalse((start.cloneAndMove(0, 2, 3) as QueueShuffleOrder).fresh)
    }

    @Test
    fun movingItemsKeepsThePlayOrder() {
        val songs = listOf("a", "b", "c", "d", "e", "f")
        val start = QueueShuffleOrder.startingWith(first = 2, length = 6, random = Random(17))
        // Songs 1 and 2 ("b", "c") move so the first lands at index 3: a d e b c f.
        val moved = start.cloneAndMove(1, 3, 3) as QueueShuffleOrder
        val movedSongs = listOf("a", "d", "e", "b", "c", "f")
        assertEquals(start.order().map { songs[it] }, moved.order().map { movedSongs[it] })
    }
}
