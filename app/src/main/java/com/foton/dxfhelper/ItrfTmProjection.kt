package com.foton.dxfhelper

import kotlin.math.*

/**
 * ITRF/TUREF 3-degree Transverse Mercator forward projection.
 *
 * Default project:
 * TUREF / TM30 — EPSG:5254
 * Central meridian: 30°
 * GRS80 ellipsoid
 * Scale factor: 1.0
 * False easting: 500000 m
 * False northing: 0 m
 *
 * Android GNSS latitude/longitude is sufficiently aligned for field-display
 * purposes. This feature is visual confirmation only, not survey measurement.
 */
object ItrfTmProjection {

    data class Projected(
        val easting: Double,
        val northing: Double
    )

    fun forward(
        latitudeDeg: Double,
        longitudeDeg: Double,
        centralMeridianDeg: Double = 30.0
    ): Projected {
        val a = 6378137.0
        val invF = 298.257222101
        val f = 1.0 / invF
        val e2 = f * (2.0 - f)
        val ep2 = e2 / (1.0 - e2)

        val lat = Math.toRadians(latitudeDeg)
        val lon = Math.toRadians(longitudeDeg)
        val lon0 = Math.toRadians(centralMeridianDeg)

        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val tanLat = tan(lat)

        val n =
            a / sqrt(
                1.0 - e2 * sinLat * sinLat
            )

        val t =
            tanLat * tanLat

        val c =
            ep2 * cosLat * cosLat

        val aa =
            (lon - lon0) * cosLat

        val e4 = e2 * e2
        val e6 = e4 * e2

        val m =
            a * (
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
            500000.0 +
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
            easting = easting,
            northing = northing
        )
    }
}
