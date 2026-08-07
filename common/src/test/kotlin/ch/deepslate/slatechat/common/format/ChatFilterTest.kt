package ch.deepslate.slatechat.common.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatFilterTest {

    @Test
    fun `blocked word is censored with asterisks, case-insensitively`() {
        val result = ChatFilter.apply("this is DAMN annoying", listOf("damn"), emptyList())
        assertEquals("this is **** annoying", result.text)
        assertEquals(1, result.events.size)
        assertEquals("blocked", result.events[0].reason)
        assertEquals("damn", result.events[0].detail)
    }

    @Test
    fun `replace swaps left for right, case-insensitively`() {
        val result = ChatFilter.apply("hello Barney", emptyList(), listOf("barney->swarley"))
        assertEquals("hello swarley", result.text)
        assertEquals(1, result.events.size)
        assertEquals("replace", result.events[0].reason)
        assertEquals("barney->swarley", result.events[0].detail)
    }

    @Test
    fun `malformed replace entry without an arrow is ignored`() {
        val result = ChatFilter.apply("hello barney", emptyList(), listOf("barneyswarley"))
        assertEquals("hello barney", result.text)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `clean text with no matches returns unchanged with no events`() {
        val result = ChatFilter.apply("nothing to see here", listOf("damn"), listOf("barney->swarley"))
        assertEquals("nothing to see here", result.text)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `every event's original reflects the text before any filtering this call, not mid-way through`() {
        val result = ChatFilter.apply("damn barney", listOf("damn"), listOf("barney->swarley"))
        assertEquals("**** swarley", result.text)
        assertEquals(2, result.events.size)
        // Both events report the same pre-filter original, regardless of order applied.
        assertTrue(result.events.all { it.original == "damn barney" })
    }
}
