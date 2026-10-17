package com.wendigo.qibla

data class City(val name: String, val lat: Double, val lon: Double)

/** Built-in location presets for quick Qibla lookup. */
object Cities {
    val all: List<City> = listOf(
        City("İstanbul", 41.0082, 28.9784),
        City("Ankara", 39.9334, 32.8597),
        City("İzmir", 38.4237, 27.1428),
        City("Bursa", 40.1885, 29.0610),
        City("Antalya", 36.8969, 30.7133),
        City("Adana", 37.0000, 35.3213),
        City("Konya", 37.8746, 32.4932),
        City("Gaziantep", 37.0662, 37.3833),
        City("Kayseri", 38.7312, 35.4787),
        City("Şanlıurfa", 37.1591, 38.7969),
        City("Diyarbakır", 37.9144, 40.2306),
        City("Trabzon", 41.0015, 39.7178),
        City("Samsun", 41.2867, 36.3300),
        City("Van", 38.4942, 43.3800),
        City("Erzurum", 39.9043, 41.2679),
        City("Mekke", 21.4225, 39.8262),
        City("Medine", 24.4686, 39.6142),
        City("Kudüs", 31.7683, 35.2137),
        City("Londra", 51.5074, -0.1278),
        City("Paris", 48.8566, 2.3522),
        City("Berlin", 52.5200, 13.4050),
        City("New York", 40.7128, -74.0060)
    )
}
