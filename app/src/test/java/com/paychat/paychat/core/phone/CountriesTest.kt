package com.paychat.paychat.core.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountriesTest {

    @Test
    fun `covers the countries libphonenumber knows`() {
        // Not an exact count: the list comes from the library and grows with it.
        assertTrue(Countries.all.size > 200)
    }

    @Test
    fun `finds a country by its region code`() {
        val bd = Countries.byRegion("BD")
        assertNotNull(bd)
        assertEquals("880", bd!!.dialCode)
    }

    @Test
    fun `dial codes carry no plus so the field can own it`() {
        assertEquals("1", Countries.byRegion("US")!!.dialCode)
        assertEquals("44", Countries.byRegion("GB")!!.dialCode)
    }

    @Test
    fun `flags come from the region letters`() {
        assertEquals("🇧🇩", Countries.byRegion("BD")!!.flag)
        assertEquals("🇺🇸", Countries.byRegion("US")!!.flag)
    }

    @Test
    fun `every country has a name to show`() {
        assertTrue(Countries.all.all { it.name.isNotBlank() })
    }

    @Test
    fun `the list is sorted by name so the picker can be scanned`() {
        val names = Countries.all.map { it.name }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `an unknown region has no country`() {
        assertEquals(null, Countries.byRegion("ZZ"))
    }

    @Test
    fun `an empty search offers everything`() {
        assertEquals(Countries.all.size, Countries.matching("   ").size)
    }

    @Test
    fun `search matches a name regardless of case`() {
        val names = Countries.matching("bangla").map { it.name }
        assertTrue(names.contains("Bangladesh"))
    }

    @Test
    fun `search matches a dial code, typed with or without the plus`() {
        assertTrue(Countries.matching("880").any { it.region == "BD" })
        assertTrue(Countries.matching("+880").any { it.region == "BD" })
    }

    @Test
    fun `search matches a region code`() {
        assertTrue(Countries.matching("bd").any { it.region == "BD" })
    }

    @Test
    fun `a search that matches nothing returns nothing`() {
        assertTrue(Countries.matching("zzzzz").isEmpty())
    }
}
