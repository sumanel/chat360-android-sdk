package com.chat360.chat360demoapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The test-user form: which employee and dealer the demo chat runs as, and the recent ones to switch between. */
class DemoUsersTest {

    // --- the form ---

    @Test
    fun `both ids are needed`() {
        assertNull(DemoUser.parse(dealer = "", emp = "12345"))
        assertNull(DemoUser.parse(dealer = "D1", emp = ""))
        assertNull(DemoUser.parse(dealer = "  ", emp = "  "))
    }

    @Test
    fun `ids are trimmed`() {
        assertEquals(DemoUser(dealerId = "D1", empId = "12345"), DemoUser.parse(dealer = "  D1 ", emp = "\n12345\t"))
    }

    @Test
    fun `an id with a space or separator inside is rejected`() {
        assertNull(DemoUser.parse(dealer = "D 1", emp = "12345"))
        assertNull(DemoUser.parse(dealer = "D1", emp = "EMP|1"))
    }

    @Test
    fun `numeric and alphanumeric codes are both fine`() {
        assertEquals("273997", DemoUser.parse("A10AA", "273997")!!.empId)
        assertEquals("EMP101", DemoUser.parse("A10AA", "EMP101")!!.empId)
    }

    // --- what the SDK is given ---

    @Test
    fun `the bot flow gets the dealer and employee as meta`() {
        assertEquals(mapOf("dealer_id" to "D1", "emp_id" to "12345"), DemoUser("D1", "12345").meta())
    }

    @Test
    fun `the sales executive check gets the same codes`() {
        assertEquals(mapOf("dealer_code" to "D1", "emp_code" to "12345"), DemoUser("D1", "12345").salesExecutive())
    }

    // --- recent users ---

    @Test
    fun `the newest user is first`() {
        val list = listOf(DemoUser("D1", "1")).withMostRecent(DemoUser("D1", "2"))
        assertEquals(listOf(DemoUser("D1", "2"), DemoUser("D1", "1")), list)
    }

    @Test
    fun `using a user again moves it to the front instead of duplicating it`() {
        val list = listOf(DemoUser("D1", "2"), DemoUser("D1", "1")).withMostRecent(DemoUser("D1", "1"))
        assertEquals(listOf(DemoUser("D1", "1"), DemoUser("D1", "2")), list)
    }

    @Test
    fun `the same employee under another dealer is a different user`() {
        val list = listOf(DemoUser("D1", "1")).withMostRecent(DemoUser("D2", "1"))
        assertEquals(2, list.size)
    }

    @Test
    fun `only the ten most recent are kept`() {
        var list = emptyList<DemoUser>()
        for (i in 1..12) list = list.withMostRecent(DemoUser("D1", "$i"))
        assertEquals(10, list.size)
        assertEquals("12", list.first().empId)
        assertEquals("3", list.last().empId)
    }

    // --- saving ---

    @Test
    fun `a list survives being saved and read back`() {
        val list = listOf(DemoUser("D1", "12345"), DemoUser("A10AA", "273997"))
        assertEquals(list, DemoUser.decodeList(DemoUser.encodeList(list)))
    }

    @Test
    fun `an empty or damaged save reads back as nothing`() {
        assertEquals(emptyList<DemoUser>(), DemoUser.decodeList(""))
        assertEquals(emptyList<DemoUser>(), DemoUser.decodeList("garbage"))
        assertEquals(listOf(DemoUser("D1", "1")), DemoUser.decodeList("garbage\nD1|1\n|\nD2|"))
    }
}
