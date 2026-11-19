package org.blindsystems.cyclope.navigation.core

/** Reads a JSON fixture from src/test/resources. Fixtures are hand-made, in the real API formats. */
fun fixture(name: String): String =
    requireNotNull(Thread.currentThread().contextClassLoader!!.getResource(name)) { "missing fixture $name" }
        .readText(Charsets.UTF_8)

/** Positions every [stepM] metres along a polyline (to simulate a walk). */
fun walk(points: List<GeoPoint>, stepM: Double): List<GeoPoint> = RouteSimulator.walk(points, stepM)
