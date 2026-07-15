package core.network.epcis

import kotlin.test.Test
import kotlin.test.assertEquals

class EpcisCommissionSerialTest {

    @Test
    fun prefersExplicitSerial() {
        val key = EpcisFlowService.resolveCommissionSerial(
            gtin = "08906038070010",
            gs1Payload = "(01)08906038070010(10)BATCH1",
            explicitSerial = "SN001",
        )
        assertEquals("SN001", key)
    }

    @Test
    fun usesAi21WhenPresent() {
        val key = EpcisFlowService.resolveCommissionSerial(
            gtin = "08906038070010",
            gs1Payload = "(01)08906038070010(21)SN001(10)BATCH1",
        )
        assertEquals("SN001", key)
    }

    @Test
    fun gtinOnlyUsesGtinDigitsAsCommissionKey() {
        val key = EpcisFlowService.resolveCommissionSerial(
            gtin = "08906038070010",
            gs1Payload = "(01)08906038070010",
        )
        assertEquals("08906038070010", key)
    }

    @Test
    fun gtinWithBatchUsesBatchAsCommissionKey() {
        val key = EpcisFlowService.resolveCommissionSerial(
            gtin = "08906038070010",
            gs1Payload = "(01)08906038070010(10)P240401",
        )
        assertEquals("P240401", key)
    }
}
