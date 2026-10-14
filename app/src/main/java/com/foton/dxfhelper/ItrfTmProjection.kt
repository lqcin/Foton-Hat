package com.foton.dxfhelper

import kotlin.math.*

object ItrfTmProjection {

    data class Projected(
        val easting: Double,
        val northing: Double
    )

    data class Geographic(
        val latitude: Double,
        val longitude: Double
    )

    private const val A = 6378137.0
    private const val INV_F = 298.257222101
    private const val FALSE_EASTING = 500000.0

    private val f = 1.0 / INV_F
    private val e2 = f * (2.0 - f)
    private val ep2 = e2 / (1.0 - e2)

    fun forward(
        latitudeDeg: Double,
        longitudeDeg: Double,
        centralMeridianDeg: Double
    ): Projected {
        val lat = Math.toRadians(latitudeDeg)
        val lon = Math.toRadians(longitudeDeg)
        val lon0 = Math.toRadians(centralMeridianDeg)

        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val tanLat = tan(lat)

        val n =
            A / sqrt(
                1.0 - e2 * sinLat * sinLat
            )

        val t = tanLat * tanLat
        val c = ep2 * cosLat * cosLat
        val aa = (lon - lon0) * cosLat

        val e4 = e2 * e2
        val e6 = e4 * e2

        val m =
            A * (
                (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * lat
                    - (3.0 * e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * sin(2.0 * lat)
                    + (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * sin(4.0 * lat)
                    - (35.0 * e6 / 3072.0) * sin(6.0 * lat)
                )

        val aa2 = aa * aa
        val aa3 = aa2 * aa
        val aa4 = aa2 * aa2
        val aa5 = aa4 * aa
        val aa6 = aa3 * aa3

        val easting =
            FALSE_EASTING +
                n * (
                    aa +
                        (1.0 - t + c) * aa3 / 6.0 +
                        (
                            5.0 -
                                18.0 * t +
                                t * t +
                                72.0 * c -
                                58.0 * ep2
                            ) * aa5 / 120.0
                    )

        val northing =
            m +
                n * tanLat * (
                    aa2 / 2.0 +
                        (
                            5.0 -
                                t +
                                9.0 * c +
                                4.0 * c * c
                            ) * aa4 / 24.0 +
                        (
                            61.0 -
                                58.0 * t +
                                t * t +
                                600.0 * c -
                                330.0 * ep2
                            ) * aa6 / 720.0
                    )

        return Projected(
            easting,
            northing
        )
    }

    fun inverse(
        easting: Double,
        northing: Double,
        centralMeridianDeg: Double
    ): Geographic {
        val x = easting - FALSE_EASTING
        val y = northing

        val e4 = e2 * e2
        val e6 = e4 * e2

        val m = y
        val mu =
            m / (
                A * (
                    1.0 -
                        e2 / 4.0 -
                        3.0 * e4 / 64.0 -
                        5.0 * e6 / 256.0
                    )
                )

        val e1 =
            (1.0 - sqrt(1.0 - e2)) /
                (1.0 + sqrt(1.0 - e2))

        val e1_2 = e1 * e1
        val e1_3 = e1_2 * e1
        val e1_4 = e1_2 * e1_2

        val phi1 =
            mu +
                (3.0 * e1 / 2.0 - 27.0 * e1_3 / 32.0) * sin(2.0 * mu) +
                (21.0 * e1_2 / 16.0 - 55.0 * e1_4 / 32.0) * sin(4.0 * mu) +
                (151.0 * e1_3 / 96.0) * sin(6.0 * mu) +
                (1097.0 * e1_4 / 512.0) * sin(8.0 * mu)

        val sin1 = sin(phi1)
        val cos1 = cos(phi1)
        val tan1 = tan(phi1)

        val n1 =
            A / sqrt(
                1.0 - e2 * sin1 * sin1
            )

        val r1 =
            A * (1.0 - e2) /
                (
                    1.0 - e2 * sin1 * sin1
                    ).pow(1.5)

        val t1 = tan1 * tan1
        val c1 = ep2 * cos1 * cos1
        val d = x / n1

        val d2 = d * d
        val d3 = d2 * d
        val d4 = d2 * d2
        val d5 = d4 * d
        val d6 = d3 * d3

        val lat =
            phi1 -
                (n1 * tan1 / r1) *
                (
                    d2 / 2.0 -
                        (
                            5.0 +
                                3.0 * t1 +
                                10.0 * c1 -
                                4.0 * c1 * c1 -
                                9.0 * ep2
                            ) * d4 / 24.0 +
                        (
                            61.0 +
                                90.0 * t1 +
                                298.0 * c1 +
                                45.0 * t1 * t1 -
                                252.0 * ep2 -
                                3.0 * c1 * c1
                            ) * d6 / 720.0
                    )

        val lon0 =
            Math.toRadians(
                centralMeridianDeg
            )

        val lon =
            lon0 +
                (
                    d -
                        (
                            1.0 +
                                2.0 * t1 +
                                c1
                            ) * d3 / 6.0 +
                        (
                            5.0 -
                                2.0 * c1 +
                                28.0 * t1 -
                                3.0 * c1 * c1 +
                                8.0 * ep2 +
                                24.0 * t1 * t1
                            ) * d5 / 120.0
                    ) / cos1

        return Geographic(
            latitude =
                Math.toDegrees(lat),
            longitude =
                Math.toDegrees(lon)
        )
    }

    /**
     * FOTONMAP'teki meridyen yanlış/eski ise GPS ve proje merkezi
     * arasındaki en küçük mesafeyi veren standart TUREF TM meridyenini seçer.
     */
    fun bestCentralMeridian(
        latitudeDeg: Double,
        longitudeDeg: Double,
        projectEasting: Double,
        projectNorthing: Double,
        preferred: Double
    ): Double {
        val candidates =
            linkedSetOf(
                preferred,
                27.0,
                30.0,
                33.0,
                36.0,
                39.0,
                42.0,
                45.0
            )

        return candidates.minByOrNull { cm ->
            val p =
                forward(
                    latitudeDeg,
                    longitudeDeg,
                    cm
                )

            hypot(
                p.easting -
                    projectEasting,
                p.northing -
                    projectNorthing
            )
        } ?: preferred
    }
}
