package org.blindsystems.cyclope.navigation.core

/** How urgent a message is. The voice-feedback module decides what to say first (vision alerts > navigation). */
enum class Priority { INFO, INSTRUCTION, WARNING }

/** Everything the navigation block can ask the voice-feedback module to say. */
sealed class GuidanceEvent {
    abstract val message: String
    abstract val priority: Priority

    data class RouteStarted(override val message: String, override val priority: Priority = Priority.INFO) : GuidanceEvent()
    /** "Dans 50 mètres, tournez à gauche." */
    data class Prepare(val stepIndex: Int, val distanceM: Double, override val message: String,
                       override val priority: Priority = Priority.INSTRUCTION) : GuidanceEvent()
    /** "Maintenant, tournez à gauche." */
    data class Now(val stepIndex: Int, override val message: String,
                   override val priority: Priority = Priority.INSTRUCTION) : GuidanceEvent()
    data class Arrived(override val message: String, override val priority: Priority = Priority.INSTRUCTION) : GuidanceEvent()
    /** The user left the route: the app must ask OSRM for a new route from the current position. */
    data class OffRoute(val crossTrackM: Double, override val message: String,
                        override val priority: Priority = Priority.WARNING) : GuidanceEvent()
    data class BackOnRoute(override val message: String, override val priority: Priority = Priority.INFO) : GuidanceEvent()
    data class GpsWeak(val accuracyM: Double, override val message: String,
                       override val priority: Priority = Priority.WARNING) : GuidanceEvent()
    data class GpsRecovered(override val message: String, override val priority: Priority = Priority.INFO) : GuidanceEvent()
}

/** Tuning values. Defaults are first guesses for a pedestrian; to be validated by field tests. */
data class FollowerConfig(
    /** Fixes with a worse reported accuracy (metres, 68 %) are ignored. */
    val maxAccuracyM: Double = 35.0,
    /** Off-route if farther than max(offRouteM, reported accuracy) from the route... */
    val offRouteM: Double = 25.0,
    /** ...for this many consecutive fixes (avoids false alarms on a single bad fix). */
    val offRouteConsecutiveFixes: Int = 3,
    /** First announcement of a manoeuvre. */
    val prepareDistanceM: Double = 50.0,
    /** "Maintenant, ..." announcement. */
    val nowDistanceM: Double = 12.0,
    val arrivalDistanceM: Double = 12.0,
    /** We never snap the user more than this far *backwards* along the route. */
    val backtrackToleranceM: Double = 30.0,
    /**
     * Two manoeuvres closer than this are announced in ONE sentence
     * ("tournez à gauche, puis 5 mètres plus loin, tournez à droite"). OSRM foot routes often
     * contain such short steps (sidewalk -> crossing -> sidewalk); said separately they sound like
     * "left!" immediately followed by "right!", which is confusing.
     */
    val combineDistanceM: Double = 20.0,
    /** After a weak-GPS warning, "GPS recovered" needs at least this accuracy (hysteresis: no on/off flapping). */
    val gpsRecoveredAccuracyM: Double = 25.0,
)

/** Snapshot for the UI / logs. */
data class FollowerState(
    val alongM: Double,
    val remainingM: Double,
    val crossTrackM: Double,
    val nextStepIndex: Int,
    val distanceToNextStepM: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
)

/**
 * Path tracking: given the route and a stream of positions, decides *what* to say and *when*.
 *
 * This is the main thing OSRM does NOT do for us: OSRM computes the route once, it does not
 * follow the user. Here we snap each GNSS fix to the route, measure the distance to the next
 * manoeuvre, announce it at 50 m and again at 12 m, detect arrival, off-route and weak GPS.
 *
 * Not thread-safe: call [start] and [update] from the same thread.
 */
class RouteFollower(
    val route: Route,
    private val formatter: InstructionFormatter = InstructionFormatter(),
    private val config: FollowerConfig = FollowerConfig(),
) {
    private val geometry = RouteGeometry(route.geometry)
    private val steps = route.steps

    /** Distance along the route at which each step's manoeuvre happens. */
    val stepAlongM: DoubleArray = DoubleArray(steps.size).also { arr ->
        var prev = 0.0
        for (i in steps.indices) {
            arr[i] = when (i) {
                0 -> 0.0
                steps.lastIndex -> geometry.totalLengthM
                else -> geometry.locate(steps[i].location, minAlongM = prev - 1.0).alongM
            }
            prev = arr[i]
        }
    }

    private var lastAlong = 0.0
    private var lastCrossTrack = 0.0
    private var nextStep = if (steps.size > 1) 1 else 0
    private val prepared = HashSet<Int>()
    /** Manoeuvres already said inside the previous sentence and too close to get their own "Maintenant". */
    private val announcedEarly = HashSet<Int>()
    private var offRouteCount = 0
    private var offRoute = false
    private var gpsWeak = false
    private var arrived = false

    val state: FollowerState
        get() = FollowerState(
            alongM = lastAlong,
            remainingM = (geometry.totalLengthM - lastAlong).coerceAtLeast(0.0),
            crossTrackM = lastCrossTrack,
            nextStepIndex = nextStep,
            distanceToNextStepM = (stepAlongM.getOrElse(nextStep) { geometry.totalLengthM } - lastAlong).coerceAtLeast(0.0),
            offRoute = offRoute,
            arrived = arrived,
        )

    /** Call once when guidance begins. */
    fun start(): List<GuidanceEvent> {
        val events = mutableListOf<GuidanceEvent>(GuidanceEvent.RouteStarted(formatter.routeSummary(route)))
        if (steps.isNotEmpty()) {
            val text = nowMessage(0).removePrefix("Maintenant, ").removePrefix("Now, ")
                .replaceFirstChar { it.uppercase() }
            events += GuidanceEvent.Now(0, text)
        }
        return events
    }

    /**
     * Feed one position fix.
     * @param accuracyM Android Location.getAccuracy(): radius in metres with 68 % confidence.
     */
    fun update(p: GeoPoint, accuracyM: Double): List<GuidanceEvent> {
        if (arrived) return emptyList()
        val events = mutableListOf<GuidanceEvent>()

        // 1. GNSS quality gate
        if (accuracyM > config.maxAccuracyM) {
            if (!gpsWeak) {
                gpsWeak = true
                events += GuidanceEvent.GpsWeak(accuracyM, formatter.gpsWeak())
            }
            return events
        }
        if (gpsWeak && accuracyM <= config.gpsRecoveredAccuracyM) {
            gpsWeak = false
            events += GuidanceEvent.GpsRecovered(formatter.gpsRecovered())
        }

        // 2. Snap the fix to the route (never far backwards)
        val pos = geometry.locate(p, minAlongM = lastAlong - config.backtrackToleranceM)
        lastCrossTrack = pos.crossTrackM

        // 3. Off-route detection
        val threshold = maxOf(config.offRouteM, accuracyM)
        if (pos.crossTrackM > threshold) {
            offRouteCount++
            if (offRouteCount >= config.offRouteConsecutiveFixes && !offRoute) {
                offRoute = true
                events += GuidanceEvent.OffRoute(pos.crossTrackM, formatter.offRoute())
            }
            return events
        }
        offRouteCount = 0
        if (offRoute) {
            offRoute = false
            events += GuidanceEvent.BackOnRoute(formatter.backOnRoute())
        }
        lastAlong = pos.alongM

        // 4. Announcements (loop: several manoeuvres can be close together)
        var guard = 0
        while (nextStep < steps.size && guard++ < steps.size) {
            val step = steps[nextStep]
            val toNext = stepAlongM[nextStep] - lastAlong
            val isLast = nextStep == steps.lastIndex

            if (isLast) {
                if (toNext <= config.arrivalDistanceM) {
                    arrived = true
                    events += GuidanceEvent.Arrived(formatter.now(step))
                } else if (toNext <= config.prepareDistanceM && prepared.add(nextStep)) {
                    events += GuidanceEvent.Prepare(nextStep, toNext, formatter.prepare(step, toNext))
                }
                break
            }
            if (toNext <= config.nowDistanceM) {
                // Skipped if it was already said together with the previous manoeuvre.
                if (nextStep !in announcedEarly) events += GuidanceEvent.Now(nextStep, nowMessage(nextStep))
                nextStep++
                continue // maybe the following manoeuvre is also close
            }
            if (toNext <= config.prepareDistanceM && prepared.add(nextStep)) {
                events += GuidanceEvent.Prepare(nextStep, toNext, prepareMessage(nextStep, toNext))
            }
            break
        }
        return events
    }

    /** The manoeuvre right after [i] if it is so close that both must be said together, else null. */
    private fun closePartner(i: Int): Int? {
        val j = i + 1
        if (j >= steps.lastIndex) return null // never merge with the arrival
        return if (stepAlongM[j] - stepAlongM[i] < config.combineDistanceM) j else null
    }

    private fun prepareMessage(i: Int, toNext: Double): String {
        val j = closePartner(i) ?: return formatter.prepare(steps[i], toNext)
        prepared.add(j) // no separate "Dans 10 mètres, ..." for it
        return formatter.prepare(steps[i], toNext, steps[j], stepAlongM[j] - stepAlongM[i])
    }

    private fun nowMessage(i: Int): String {
        val j = closePartner(i) ?: return withContinuation(formatter.now(steps[i]), i)
        val gap = stepAlongM[j] - stepAlongM[i]
        prepared.add(j)
        // Closer than the "Maintenant" distance: its own "Maintenant" would come right after this one.
        if (gap <= config.nowDistanceM) announcedEarly.add(j)
        return withContinuation(formatter.now(steps[i], steps[j], gap), j)
    }

    /** After a manoeuvre, if the next one is far, tell the user how long to keep going. */
    private fun withContinuation(text: String, stepIndex: Int): String {
        val next = stepIndex + 1
        if (next >= steps.size) return text
        val stretch = stepAlongM[next] - stepAlongM[stepIndex]
        return if (stretch > config.prepareDistanceM + 20) "$text ${formatter.thenContinue(stretch)}" else text
    }
}
