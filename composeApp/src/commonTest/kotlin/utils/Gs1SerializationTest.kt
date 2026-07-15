package utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Gs1ParserTest {

    @Test
    fun parseParentheticalElementString() {
        val result = Gs1Parser.parse("(01)08906038070010(10)BATCH001(17)251231(21)SN000001")
        assertEquals("08906038070010", result.gtin)
        assertEquals("BATCH001", result.batch)
        assertEquals("251231", result.expiry)
        assertEquals("SN000001", result.serial)
    }

    @Test
    fun parseSsccAi00() {
        val result = Gs1Parser.parse("(00)006141411000000273")
        assertEquals("006141411000000273", result.sscc)
    }

    @Test
    fun parseDigitalLinkGtin() {
        val result = Gs1Parser.parse("https://id.gs1.org/01/08906038070010/10/BATCH001/21/SN1")
        assertEquals("08906038070010", result.gtin)
    }
}

class EpcUriBuilderTest {

    @Test
    fun buildSgtinFrom14DigitGtin() {
        val uri = EpcUriBuilder.buildSgtinEpcUri("08906038070010", "SN000001")
        assertEquals("urn:epc:id:sgtin:8906038.070010.SN000001", uri)
    }

    @Test
    fun buildSgtinFrom13DigitGtinPadsIndicatorZero() {
        val uri = EpcUriBuilder.buildSgtinEpcUri("8906038070010", "ABC")
        assertTrue(uri.startsWith("urn:epc:id:sgtin:"))
        assertTrue(uri.endsWith(".ABC"))
    }

    @Test
    fun buildSsccEpcUri() {
        val uri = EpcUriBuilder.buildSsccEpcUri("006141411000000273")
        assertEquals("urn:epc:id:sscc:0614141.01000000273", uri)
    }

    @Test
    fun encodeEpcUriForPathEncodesColons() {
        val encoded = EpcUriBuilder.encodeEpcUriForPath("urn:epc:id:sgtin:8906038.070010.SN1")
        assertTrue(encoded.contains("%3A"))
        assertNotNull(encoded)
    }

    @Test
    fun sgtinEpcUriFromScan() {
        val uri = EpcUriBuilder.sgtinEpcUriFromScan("(01)08906038070010(21)SN000001")
        assertEquals("urn:epc:id:sgtin:8906038.070010.SN000001", uri)
    }
}
