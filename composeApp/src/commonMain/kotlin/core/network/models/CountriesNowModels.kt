package core.network.models

import kotlinx.serialization.Serializable

@Serializable
data class CountriesNowCountriesResponse(
    val error: Boolean = false,
    val msg: String = "",
    val data: List<CountryWithCities> = emptyList(),
)

@Serializable
data class CountryWithCities(
    val country: String = "",
    val cities: List<String> = emptyList(),
)

@Serializable
data class CountriesNowStatesResponse(
    val error: Boolean = false,
    val msg: String = "",
    val data: CountryStatesData? = null,
)

@Serializable
data class CountryStatesData(
    val name: String = "",
    val states: List<StateItem> = emptyList(),
)

@Serializable
data class StateItem(
    val name: String = "",
    val state_code: String = "",
)

@Serializable
data class CountriesNowCitiesResponse(
    val error: Boolean = false,
    val msg: String = "",
    val data: List<String> = emptyList(),
)
