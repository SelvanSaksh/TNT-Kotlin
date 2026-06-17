package core.network.repository

import core.network.models.CountriesNowCitiesResponse
import core.network.models.CountriesNowCountriesResponse
import core.network.models.CountriesNowStatesResponse
import io.ktor.http.encodeURLParameter
import network.ApiClient

object CountriesNowRepository {

    private const val BASE_URL = "https://countriesnow.space/api/v0.1/countries"

    private var cachedCountries: List<String>? = null
    private val statesCache = mutableMapOf<String, List<String>>()
    private val citiesCache = mutableMapOf<String, List<String>>()

    suspend fun fetchCountries(): Result<List<String>> {
        cachedCountries?.let { return Result.success(it) }
        return try {
            val response = ApiClient.get<CountriesNowCountriesResponse>(BASE_URL)
            if (response.error) {
                return Result.failure(Exception(response.msg.ifBlank { "Failed to load countries" }))
            }
            val countries = response.data.map { it.country }.filter { it.isNotBlank() }.sorted()
            cachedCountries = countries
            Result.success(countries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchStates(country: String): Result<List<String>> {
        if (country.isBlank()) return Result.success(emptyList())
        statesCache[country]?.let { return Result.success(it) }
        return try {
            val url = "$BASE_URL/states/q?country=${country.encodeURLParameter()}"
            val response = ApiClient.get<CountriesNowStatesResponse>(url)
            if (response.error) {
                return Result.failure(Exception(response.msg.ifBlank { "Failed to load states" }))
            }
            val states = response.data?.states?.map { it.name }?.filter { it.isNotBlank() }?.sorted() ?: emptyList()
            statesCache[country] = states
            Result.success(states)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCities(country: String, state: String): Result<List<String>> {
        if (country.isBlank() || state.isBlank()) return Result.success(emptyList())
        val cacheKey = "$country|$state"
        citiesCache[cacheKey]?.let { return Result.success(it) }
        return try {
            val url = "$BASE_URL/state/cities/q" +
                "?country=${country.encodeURLParameter()}" +
                "&state=${state.encodeURLParameter()}"
            val response = ApiClient.get<CountriesNowCitiesResponse>(url)
            if (response.error) {
                return Result.failure(Exception(response.msg.ifBlank { "Failed to load cities" }))
            }
            val cities = response.data.filter { it.isNotBlank() }.sorted()
            citiesCache[cacheKey] = cities
            Result.success(cities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
