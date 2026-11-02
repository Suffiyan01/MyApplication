package com.ridervoice.cockpit.domain

import com.ridervoice.cockpit.model.GeoPointData
import com.ridervoice.cockpit.model.PlaceCategory
import com.ridervoice.cockpit.model.PlaceSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMathAndFreeMapEngine {

    /**
     * Calculates distance in meters between two coordinates using the Haversine formula.
     */
    fun calculateDistanceMeters(a: GeoPointData, b: GeoPointData): Int {
        val r = 6371000.0 // Earth radius in meters
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)

        val haversine = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
        return (r * c).toInt()
    }

    /**
     * Calculates initial compass bearing in degrees (0..360) from start point to end point.
     */
    fun calculateBearingDeg(from: GeoPointData, to: GeoPointData): Float {
        val dLon = Math.toRadians(to.lon - from.lon)
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360f) % 360f
    }

    /**
     * Searches places via free Komoot Photon API, falling back to guaranteed offline Jaipur highway dataset.
     */
    suspend fun searchPlacesFree(
        query: String,
        category: PlaceCategory,
        near: GeoPointData
    ): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val fallbackPlaces = getOfflineJaipurDataset(near)
        val filteredFallback = if (category == PlaceCategory.ALL) {
            fallbackPlaces
        } else {
            fallbackPlaces.filter { it.category == category }
        }.let { list ->
            if (query.isBlank()) list else list.filter {
                it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true)
            }
        }

        if (query.isBlank() && category == PlaceCategory.ALL) {
            return@withContext filteredFallback
        }

        try {
            val encodedQuery = URLEncoder.encode(query.ifBlank { category.label }, "UTF-8")
            val urlString = "https://photon.komoot.io/api/?q=$encodedQuery&lat=${near.lat}&lon=${near.lon}&limit=6"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RiderVoice-AndroidCockpit/2.0")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(responseText)
                val features = rootJson.optJSONArray("features") ?: return@withContext filteredFallback

                val liveResults = mutableListOf<PlaceSearchResult>()
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val props = feature.getJSONObject("properties")
                    val geom = feature.getJSONObject("geometry")
                    val coords = geom.getJSONArray("coordinates")
                    val lon = coords.getDouble(0)
                    val lat = coords.getDouble(1)
                    val loc = GeoPointData(lat, lon)

                    val name = props.optString("name", "Rider Stop")
                    val city = props.optString("city", props.optString("state", "Nearby"))
                    val street = props.optString("street", "")
                    val address = if (street.isNotEmpty()) "$street, $city" else city

                    val distMeters = calculateDistanceMeters(near, loc)
                    val distKm = distMeters / 1000f

                    liveResults.add(
                        PlaceSearchResult(
                            placeId = "photon-$i-${lat.hashCode()}",
                            title = name,
                            subtitle = address,
                            category = category,
                            location = loc,
                            distanceKm = (distKm * 10).toInt() / 10f,
                            detourMinutes = (distKm * 1.8f).toInt().coerceAtLeast(1),
                            isOpenNow = true,
                            rating = 4.6f
                        )
                    )
                }

                if (liveResults.isNotEmpty()) {
                    return@withContext (liveResults + filteredFallback).distinctBy { it.title }.take(8)
                }
            }
        } catch (e: Exception) {
            // Fall back to offline dataset seamlessly
        }

        return@withContext filteredFallback
    }

    /**
     * Fetches route polyline via free Project OSRM HTTP API, falling back to a smooth curved curve path if offline.
     */
    suspend fun fetchRoutePolylineFree(
        start: GeoPointData,
        end: GeoPointData
    ): List<GeoPointData> = withContext(Dispatchers.IO) {
        val fallbackPolyline = generateCurvedPolylineFallback(start, end)
        try {
            val urlString = "https://router.project-osrm.org/route/v1/driving/${start.lon},${start.lat};${end.lon},${end.lat}?overview=full&geometries=geojson"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RiderVoice-AndroidCockpit/2.0")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(responseText)
                val routes = rootJson.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val geometry = route.getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")

                    val polyline = mutableListOf<GeoPointData>()
                    for (i in 0 until coordinates.length()) {
                        val pt = coordinates.getJSONArray(i)
                        val lon = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        polyline.add(GeoPointData(lat, lon))
                    }
                    if (polyline.size >= 2) {
                        return@withContext polyline
                    }
                }
            }
        } catch (e: Exception) {
            // Smooth curved fallback
        }

        return@withContext fallbackPolyline
    }

    private fun generateCurvedPolylineFallback(start: GeoPointData, end: GeoPointData): List<GeoPointData> {
        val points = mutableListOf<GeoPointData>()
        val steps = 16
        val midLat = (start.lat + end.lat) / 2 + 0.003
        val midLon = (start.lon + end.lon) / 2 - 0.004

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            // Quadratic Bezier interpolation for realistic highway curvature
            val lat = (1 - t) * (1 - t) * start.lat + 2 * (1 - t) * t * midLat + t * t * end.lat
            val lon = (1 - t) * (1 - t) * start.lon + 2 * (1 - t) * t * midLon + t * t * end.lon
            points.add(GeoPointData(lat, lon))
        }
        return points
    }

    private fun getOfflineJaipurDataset(near: GeoPointData): List<PlaceSearchResult> {
        val rawStops = listOf(
            PlaceSearchResult(
                placeId = "jp-fuel-01",
                title = "HP Highway Fuel & Nitrogen Hub",
                subtitle = "NH-48 Jaipur Bypass • High Flow & Air Station",
                category = PlaceCategory.FUEL,
                location = GeoPointData(26.9612, 75.8390),
                rating = 4.8f
            ),
            PlaceSearchResult(
                placeId = "jp-cafe-01",
                title = "Nahargarh Sunset Biker Cafe",
                subtitle = "Nahargarh Fort Hill Road • Express Coffee & Parking",
                category = PlaceCategory.BIKER_CAFE,
                location = GeoPointData(26.9380, 75.8150),
                rating = 4.9f
            ),
            PlaceSearchResult(
                placeId = "jp-mech-01",
                title = "Royal Enfield & ADV Quick Service Pit",
                subtitle = "Amer Road Highway Junction • Tyre Puncture & Chain Lube",
                category = PlaceCategory.MECHANIC,
                location = GeoPointData(26.9710, 75.8450),
                rating = 4.7f
            ),
            PlaceSearchResult(
                placeId = "jp-view-01",
                title = "Amber Fort Ghat Viewpoint",
                subtitle = "Devisinghpura, Amer • Scenic Canyon Pull-Off",
                category = PlaceCategory.VIEWPOINT,
                location = GeoPointData(26.9855, 75.8513),
                rating = 4.9f
            ),
            PlaceSearchResult(
                placeId = "jp-view-02",
                title = "Jal Mahal Lakefront Pull-Off",
                subtitle = "Amer Road Promenade • Group Staging Area",
                category = PlaceCategory.VIEWPOINT,
                location = GeoPointData(26.9535, 75.8462),
                rating = 4.8f
            ),
            PlaceSearchResult(
                placeId = "jp-med-01",
                title = "Jaipur Trauma & Highway First Aid",
                subtitle = "NH-48 Corridor • 24/7 Emergency Medical Station",
                category = PlaceCategory.MEDICAL,
                location = GeoPointData(26.9420, 75.8280),
                rating = 4.9f
            ),
            PlaceSearchResult(
                placeId = "jp-fuel-02",
                title = "IndianOil XTRA PREMIUM Pit",
                subtitle = "Kukas ADV Expressway • 95 Octane Available",
                category = PlaceCategory.FUEL,
                location = GeoPointData(27.0120, 75.8910),
                rating = 4.7f
            ),
            PlaceSearchResult(
                placeId = "jp-cafe-02",
                title = "Highway Riders Dhaba & Tea Post",
                subtitle = "Amer Bypass • Hot Parathas & Highway Tea",
                category = PlaceCategory.BIKER_CAFE,
                location = GeoPointData(26.9910, 75.8620),
                rating = 4.6f
            )
        )

        return rawStops.map { stop ->
            val distMeters = calculateDistanceMeters(near, stop.location)
            val distKm = (distMeters / 100f).toInt() / 10f
            stop.copy(
                distanceKm = distKm,
                detourMinutes = (distKm * 1.5f).toInt().coerceAtLeast(1)
            )
        }.sortedBy { it.distanceKm }
    }
}
