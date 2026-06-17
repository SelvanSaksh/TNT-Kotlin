package features.app.warehouse

/** Mock datasets aligned with iOS `packing.swift`, `Receiving.swift`, and picking order refs. */
object WarehouseMockData {

    fun mockPickingOrders(): List<PickingAppOrder> = listOf(
        PickingAppOrder(
            id = 456,
            orderNo = "ORD-5678",
            zone = "A-12",
            status = "pending",
            items = listOf(
                PickingAppItem(
                    productId = 501,
                    name = "Surf Excel Easy Wash 1kg",
                    sku = "HUL-SURF-1KG",
                    batch = "SURF240520A",
                    gtin = "08901234560015",
                    bin = "A-12-01",
                    pickedQty = 2,
                    totalQty = 6,
                ),
                PickingAppItem(
                    productId = 502,
                    name = "Aashirvaad Whole Wheat Atta 5kg",
                    sku = "ITC-AATA-5KG",
                    batch = "AATA240520B",
                    gtin = "08907654321098",
                    bin = "A-12-04",
                    pickedQty = 0,
                    totalQty = 6,
                ),
            ),
        ),
        PickingAppOrder(
            id = 457,
            orderNo = "ORD-5679",
            zone = "B-03",
            status = "picking",
            items = listOf(
                PickingAppItem(
                    productId = 601,
                    name = "Colgate Strong Teeth Toothpaste 200g",
                    sku = "COL-TPST-200G",
                    batch = "COL240520C",
                    gtin = "08904561237890",
                    bin = "B-03-02",
                    pickedQty = 4,
                    totalQty = 4,
                ),
                PickingAppItem(
                    productId = 602,
                    name = "Dabur Amla Hair Oil 200ml",
                    sku = "DAB-AMLA-200ML",
                    batch = "DAB240520D",
                    gtin = "08903456781234",
                    bin = "B-03-05",
                    pickedQty = 4,
                    totalQty = 4,
                ),
                PickingAppItem(
                    productId = 603,
                    name = "Amul Fresh Paneer 200g",
                    sku = "AMUL-PNR-200G",
                    batch = "AMUL240520E",
                    gtin = "08901122334455",
                    bin = "B-03-08",
                    pickedQty = 0,
                    totalQty = 8,
                ),
            ),
        ),
    )

    fun mockPackingDataset(): Pair<List<PackingItem>, Map<String, Pair<String, String>>> {
        val orderA = "ORD-5678"
        val orderB = "ORD-5679"
        val pickA = "456"
        val pickB = "457"

        val items = listOf(
            PackingItem(
                id = 1001,
                orderId = orderA,
                productId = 501,
                name = "Surf Excel Easy Wash 1kg",
                code = "08901234560015",
                quantity = 4,
                remaining = 2,
                packed = 2,
                total = 6,
                batch = "SURF240520A",
                sku = "HUL-SURF-1KG",
                gtin = "08901234560015",
                mainOrderId = orderA,
                pickingOrderId = pickA,
                status = "picked",
                orderedQty = 6,
                cartonSSCC = "00876543210000001234",
                dispatched = false,
            ),
            PackingItem(
                id = 1002,
                orderId = orderA,
                productId = 502,
                name = "Aashirvaad Whole Wheat Atta 5kg",
                code = "08907654321098",
                quantity = 4,
                remaining = 2,
                packed = 2,
                total = 6,
                batch = "AATA240520B",
                sku = "ITC-AATA-5KG",
                gtin = "08907654321098",
                mainOrderId = orderA,
                pickingOrderId = pickA,
                status = "picked",
                orderedQty = 6,
                cartonSSCC = "00876543210000005555",
                dispatched = false,
            ),
            PackingItem(
                id = 2001,
                orderId = orderB,
                productId = 601,
                name = "Colgate Strong Teeth Toothpaste 200g",
                code = "08904561237890",
                quantity = 4,
                remaining = 0,
                packed = 4,
                total = 4,
                batch = "COL240520C",
                sku = "COL-TPST-200G",
                gtin = "08904561237890",
                mainOrderId = orderB,
                pickingOrderId = pickB,
                status = "packed",
                orderedQty = 4,
                cartonSSCC = "00876543210000009901",
                dispatched = false,
            ),
            PackingItem(
                id = 2002,
                orderId = orderB,
                productId = 602,
                name = "Dabur Amla Hair Oil 200ml",
                code = "08903456781234",
                quantity = 4,
                remaining = 0,
                packed = 4,
                total = 4,
                batch = "DAB240520D",
                sku = "DAB-AMLA-200ML",
                gtin = "08903456781234",
                mainOrderId = orderB,
                pickingOrderId = pickB,
                status = "packed",
                orderedQty = 4,
                cartonSSCC = "00876543210000009902",
                dispatched = false,
            ),
            PackingItem(
                id = 2003,
                orderId = orderB,
                productId = 603,
                name = "Amul Fresh Paneer 200g",
                code = "08901122334455",
                quantity = 4,
                remaining = 4,
                packed = 0,
                total = 8,
                batch = "AMUL240520E",
                sku = "AMUL-PNR-200G",
                gtin = "08901122334455",
                mainOrderId = orderB,
                pickingOrderId = pickB,
                status = "picked",
                orderedQty = 8,
                cartonSSCC = "00876543210000009903",
                dispatched = false,
            ),
        )

        val map = mapOf(
            orderA to (orderA to pickA),
            orderB to (orderB to pickB),
        )
        return items to map
    }

    fun mockReceivingOrders(): List<DispatchedOrder> {
        val order1Items = listOf(
            DispatchedItem(
                id = 101,
                productName = "Case pack — fast movers",
                status = "dispatched",
                gtin = "10614141123456",
                batch = "LOT-A1",
                pickedQuantity = 25,
                packedQuantity = 25,
                receivedQuantity = 15,
                quantity = 25,
            ),
            DispatchedItem(
                id = 102,
                productName = "Case pack — slow movers",
                status = "dispatched",
                gtin = "06141411234562",
                batch = "LOT-A2",
                pickedQuantity = 20,
                packedQuantity = 20,
                receivedQuantity = 8,
                quantity = 20,
            ),
        )

        val order2Items = listOf(
            DispatchedItem(
                id = 201,
                productName = "Bulk shipment",
                status = "dispatched",
                gtin = "08901234560015",
                batch = "BULK-01",
                pickedQuantity = 120,
                packedQuantity = 120,
                receivedQuantity = 0,
                quantity = 120,
            ),
        )

        val order3Items = listOf(
            DispatchedItem(
                id = 301,
                productName = "Mixed SKU pallet",
                status = "dispatched",
                gtin = "08907654321098",
                batch = "MIX-9",
                pickedQuantity = 30,
                packedQuantity = 30,
                receivedQuantity = 12,
                quantity = 30,
            ),
        )

        return listOf(
            DispatchedOrder(
                id = 1,
                packageType = "pallet",
                ssc = "00614141234567890123",
                orderId = 2024001,
                status = "dispatched",
                quantity = 45,
                items = order1Items,
                vendorName = "ACME SUPPLIES INC.",
                purchaseOrderRef = "PO-2024-001",
                receivingLocation = "RCV-DOCK-A",
                dueSummary = null,
            ),
            DispatchedOrder(
                id = 2,
                packageType = "pallet",
                ssc = "00614149998887776655",
                orderId = 2024002,
                status = "dispatched",
                quantity = 120,
                items = order2Items,
                vendorName = "GLOBAL DISTRIBUTORS",
                purchaseOrderRef = "PO-2024-002",
                receivingLocation = "RCV-DOCK-B",
                dueSummary = "Due today",
            ),
            DispatchedOrder(
                id = 3,
                packageType = "carton",
                ssc = "00876543210000009901",
                orderId = 2024003,
                status = "dispatched",
                quantity = 30,
                items = order3Items,
                vendorName = "NORTH LOGISTICS LLC",
                purchaseOrderRef = "PO-2024-003",
                receivingLocation = "RCV-DOCK-A",
                dueSummary = null,
            ),
        )
    }
}
