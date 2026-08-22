package resolver

import resolverModels.AiCategory

/** Name and category for a GS1 Application Identifier. */
data class AiDefinition(
    val name: String,
    val category: String,
)

/**
 * GS1 Application Identifier catalogue used to label parsed Digital Link
 * segments. Codes missing here fall back to `AI <code>` with category
 * [AiCategory.OTHER].
 */
val AI_DEFINITIONS: Map<String, AiDefinition> = mapOf(
    "01" to AiDefinition("GTIN", AiCategory.PRIMARY),
    "02" to AiDefinition("GTIN of Contained Trade Items", AiCategory.LOGISTICS),
    "10" to AiDefinition("Batch/Lot Number", AiCategory.LOGISTICS),
    "11" to AiDefinition("Production Date", AiCategory.ATTRIBUTE),
    "12" to AiDefinition("Due Date", AiCategory.ATTRIBUTE),
    "13" to AiDefinition("Packaging Date", AiCategory.ATTRIBUTE),
    "15" to AiDefinition("Best Before Date", AiCategory.ATTRIBUTE),
    "17" to AiDefinition("Expiration Date", AiCategory.ATTRIBUTE),
    "20" to AiDefinition("Variant", AiCategory.ATTRIBUTE),
    "21" to AiDefinition("Serial Number", AiCategory.LOGISTICS),
    "22" to AiDefinition("Consumer Product Variant", AiCategory.ATTRIBUTE),
    "30" to AiDefinition("Count of Items", AiCategory.LOGISTICS),
    "37" to AiDefinition("Count of Trade Items", AiCategory.LOGISTICS),
    "90" to AiDefinition("Information mutually agreed between trading partners", AiCategory.OTHER),
    "91" to AiDefinition("Company internal information", AiCategory.OTHER),
    "92" to AiDefinition("Company internal information", AiCategory.OTHER),
    "93" to AiDefinition("Company internal information", AiCategory.OTHER),
    "94" to AiDefinition("Company internal information", AiCategory.OTHER),
    "95" to AiDefinition("Company internal information", AiCategory.OTHER),
    "96" to AiDefinition("Company internal information", AiCategory.OTHER),
    "97" to AiDefinition("Company internal information", AiCategory.OTHER),
    "98" to AiDefinition("Company internal information", AiCategory.OTHER),
    "99" to AiDefinition("Company internal information", AiCategory.OTHER),
    "240" to AiDefinition("Additional Product Identification", AiCategory.ATTRIBUTE),
    "241" to AiDefinition("Customer Part Number", AiCategory.ATTRIBUTE),
    "242" to AiDefinition("Made-to-Order Variation Number", AiCategory.ATTRIBUTE),
    "243" to AiDefinition("Packaging Component Number", AiCategory.LOGISTICS),
    "250" to AiDefinition("Secondary Serial Number", AiCategory.LOGISTICS),
    "251" to AiDefinition("Reference to Source Entity", AiCategory.OTHER),
    "253" to AiDefinition("Global Document Type Identifier", AiCategory.OTHER),
    "254" to AiDefinition("GLN Extension Component", AiCategory.OTHER),
    "255" to AiDefinition("Global Coupon Number", AiCategory.OTHER),
    "400" to AiDefinition("Customer's Purchase Order Number", AiCategory.LOGISTICS),
    "401" to AiDefinition("Global Identification Number for Consignment", AiCategory.LOGISTICS),
    "402" to AiDefinition("Global Shipment Identification Number", AiCategory.LOGISTICS),
    "403" to AiDefinition("Routing Code", AiCategory.LOGISTICS),
    "410" to AiDefinition("Ship to - Deliver to Global Location Number", AiCategory.LOGISTICS),
    "411" to AiDefinition("Bill to - Invoice to Global Location Number", AiCategory.LOGISTICS),
    "412" to AiDefinition("Purchased from Global Location Number", AiCategory.LOGISTICS),
    "413" to AiDefinition("Ship for - Deliver for - Forward to Global Location Number", AiCategory.LOGISTICS),
    "414" to AiDefinition("Identification of a physical location - Global Location Number", AiCategory.LOGISTICS),
    "415" to AiDefinition("Global Location Number of the invoicing party", AiCategory.LOGISTICS),
    "416" to AiDefinition("GLN of the production or service location", AiCategory.LOGISTICS),
    "420" to AiDefinition("Ship to - Deliver to Postal Code", AiCategory.LOGISTICS),
    "421" to AiDefinition("Ship to - Deliver to Postal Code with ISO Country Code", AiCategory.LOGISTICS),
    "422" to AiDefinition("Country of Origin", AiCategory.ATTRIBUTE),
    "423" to AiDefinition("Country of Initial Processing", AiCategory.ATTRIBUTE),
    "424" to AiDefinition("Country of Processing", AiCategory.ATTRIBUTE),
    "425" to AiDefinition("Country of Disassembly", AiCategory.ATTRIBUTE),
    "426" to AiDefinition("Country Covering Full Process Chain", AiCategory.ATTRIBUTE),
    "427" to AiDefinition("Country Subdivision of Origin", AiCategory.ATTRIBUTE),
    "7001" to AiDefinition("NATO Stock Number", AiCategory.OTHER),
    "7002" to AiDefinition("UN/ECE Meat Carcasses and Cuts Classification", AiCategory.ATTRIBUTE),
    "7003" to AiDefinition("Expiration Date and Time", AiCategory.ATTRIBUTE),
    "7004" to AiDefinition("Active Potency", AiCategory.ATTRIBUTE),
    "7005" to AiDefinition("Catch Area", AiCategory.ATTRIBUTE),
    "7006" to AiDefinition("First Freeze Date", AiCategory.ATTRIBUTE),
    "7007" to AiDefinition("Harvest Date", AiCategory.ATTRIBUTE),
    "7008" to AiDefinition("Species for Fishery Purposes", AiCategory.ATTRIBUTE),
    "7009" to AiDefinition("Fishing Gear Type", AiCategory.ATTRIBUTE),
    "7010" to AiDefinition("Production Method", AiCategory.ATTRIBUTE),
    "7020" to AiDefinition("Refurbishment Lot ID", AiCategory.LOGISTICS),
    "7021" to AiDefinition("Functional Status", AiCategory.ATTRIBUTE),
    "7022" to AiDefinition("Revision Status", AiCategory.ATTRIBUTE),
    "7023" to AiDefinition("Global Individual Asset Identifier of an Assembly", AiCategory.LOGISTICS),
    "8001" to AiDefinition(
        "Roll Products - Width, Length, Core Diameter, Direction, Splices",
        AiCategory.ATTRIBUTE,
    ),
    "8002" to AiDefinition("Cellular Mobile Telephone Identifier", AiCategory.OTHER),
    "8003" to AiDefinition("Global Returnable Asset Identifier", AiCategory.LOGISTICS),
    "8004" to AiDefinition("Global Individual Asset Identifier", AiCategory.LOGISTICS),
    "8005" to AiDefinition("Price Per Unit of Measure", AiCategory.ATTRIBUTE),
    "8006" to AiDefinition("Identification of the components of an item", AiCategory.LOGISTICS),
    "8007" to AiDefinition("International Bank Account Number", AiCategory.OTHER),
    "8008" to AiDefinition("Date and Time of Production", AiCategory.ATTRIBUTE),
    "8009" to AiDefinition("Optically Readable Sensor Indicator", AiCategory.OTHER),
    "8010" to AiDefinition("Component/Part Identifier", AiCategory.LOGISTICS),
    "8011" to AiDefinition("Component/Part Identifier Serial Number", AiCategory.LOGISTICS),
    "8012" to AiDefinition("Software Version", AiCategory.ATTRIBUTE),
    "8013" to AiDefinition("Global Model Number", AiCategory.ATTRIBUTE),
    "8017" to AiDefinition(
        "Global Service Relation Number to identify the relationship between an " +
            "organisation responsible for providing service and the provider of the service",
        AiCategory.OTHER,
    ),
    "8018" to AiDefinition(
        "Global Service Relation Number to identify the relationship between an " +
            "organisation responsible for providing service and the recipient of the service",
        AiCategory.OTHER,
    ),
    "8019" to AiDefinition("Service Relation Instance Number", AiCategory.OTHER),
    "8020" to AiDefinition("Payment Slip Reference Number", AiCategory.OTHER),
    "8026" to AiDefinition("Identification of pieces of a transport load", AiCategory.LOGISTICS),
    "8110" to AiDefinition("Coupon Code Identification for Use in North America", AiCategory.OTHER),
    "8111" to AiDefinition("Loyalty Points of a Coupon", AiCategory.OTHER),
    "8112" to AiDefinition(
        "Positive Offer File Coupon Code Identification for use in North America",
        AiCategory.OTHER,
    ),
    "8200" to AiDefinition("Extended Packaging URL", AiCategory.OTHER),
)

/** Display name for an AI code, falling back to `AI <code>` like the web resolver. */
fun aiName(code: String): String = AI_DEFINITIONS[code]?.name ?: "AI $code"

fun aiCategory(code: String): String = AI_DEFINITIONS[code]?.category ?: AiCategory.OTHER
