package org.blindsystems.cyclope.navigation.core

import kotlin.math.sqrt

/**
 * Source n°5 of the positioning study: our own "custom fusion".
 *
 * PDR = Pedestrian Dead Reckoning: between two GNSS fixes, we move the estimated position
 * by one step length in the direction given by the compass (rotation-vector sensor =
 * accelerometer + gyroscope + magnetometer). When a GNSS fix arrives, we correct the estimate
 * with a 1-D Kalman-style weight:
 *
 *     k = sigma_pdr^2 / (sigma_pdr^2 + accuracy_gnss^2)
 *     estimate = estimate + k * (gnss - estimate)
 *
 * -> if GNSS is precise (small accuracy) we trust GNSS; if GNSS is bad (urban canyon) we trust the steps.
 * Kept deliberately simple so it can be explained and tuned with field data.
 */
class PdrFusion(
    /** Average step length of an adult; to calibrate per user. */
    private val stepLengthM: Double = 0.7,
    /** Uncertainty added at each step (heading error + step length error). */
    private val stepNoiseM: Double = 0.35,
    /** Upper bound of the uncertainty, so a long GNSS outage doesn't make the filter ignore GNSS forever. */
    private val maxSigmaM: Double = 50.0,
    /**
     * Lower bound of the uncertainty. Successive GNSS errors are correlated (same multipath), so averaging
     * many fixes while standing still does NOT make us centimetre-precise. Without this floor the filter
     * became over-confident (field test 19/11: "±0.4 m" indoors) and then ignored GNSS.
     */
    private val minSigmaM: Double = 3.0,
) {
    var estimate: GeoPoint? = null
        private set

    /** 1-sigma uncertainty of [estimate], in metres. */
    var sigmaM: Double = Double.NaN
        private set

    var stepsSinceLastFix = 0
        private set

    fun onGnssFix(p: GeoPoint, accuracyM: Double) {
        val est = estimate
        if (est == null) {
            estimate = p
            sigmaM = maxOf(minSigmaM, accuracyM)
        } else {
            val s2 = sigmaM * sigmaM
            val a2 = accuracyM * accuracyM
            val k = s2 / (s2 + a2)
            val local = Geo.toLocal(est, p) // metres east/north from estimate to the fix
            estimate = Geo.fromLocal(est, k * local[0], k * local[1])
            sigmaM = maxOf(minSigmaM, sqrt((1 - k) * s2))
        }
        stepsSinceLastFix = 0
    }

    /** Call on each Android TYPE_STEP_DETECTOR event, with the current true heading in degrees. */
    fun onStep(headingDeg: Double) {
        val est = estimate ?: return // no starting point yet
        estimate = Geo.destination(est, headingDeg, stepLengthM)
        sigmaM = minOf(maxSigmaM, sqrt(sigmaM * sigmaM + stepNoiseM * stepNoiseM))
        stepsSinceLastFix++
    }
}
