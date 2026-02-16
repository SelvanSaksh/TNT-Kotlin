package network.models

import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    val id: Int,
    val name: String,
    val tagNo: String,
    val status: String,
    val serialNo: String,
    val assetSubtype: String? = null,
    val assetPurchases: AssetPurchase,
    val capacity: String? = null,
    val modelNo: String? = null,
    val trackingType: String,
    val barcode: String? = null,
    val category: Category,
    val company: Company,
    val currentLocation: Location? = null,
    val assetImages: List<String> = emptyList()
)

@Serializable
data class AssetPurchase(
    val id: Int,
    val vendorName: String,
    val purchasePrice: String,
    val usefulLifeYear: Int,
    val purchaseDate: String,
    val invoiceNo: String,
    val invoiceFile: String? = null,
    val quantity: Int,
    val warrantyExpiryDate: String? = null,
    val warrantyFile: String? = null
)

@Serializable
data class Category(
    val id: Int,
    val name: String
)

@Serializable
data class Company(
    val id: Int,
    val companyName: String,
    val companyEmail: String,
    val companyContact: String,
    val companyLocation: String,
    val lat: String? = null,
    val long: String? = null,
    val gst: String? = null,
    val companyAddress: String,
    val companyDocument: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val companyLogo: String? = null,
    val status: Int,
    val platform: String? = null
)

@Serializable
data class Location(
    val id: Int,
    val parent_location_id: Int? = null,
    val location_address: String? = null,
    val alternative_location_identifiers: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val timestamp: String? = null,
    val area: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    val siteCode: String? = null,
    val status: Int,
    val company_name: String? = null,
    val location_name: String? = null,
    val location_description: String? = null,
    val location_type: String? = null,
    val global_location_number: String? = null,
    val gln_extension: String? = null,
    val sgln: String? = null,
    val location_business_partner_number: String? = null,
    val type: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)
