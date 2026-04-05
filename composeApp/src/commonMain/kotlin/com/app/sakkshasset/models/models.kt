package com.app.sakkshasset.models

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val city: String?,
    val state: String?,
    val country: String?
)