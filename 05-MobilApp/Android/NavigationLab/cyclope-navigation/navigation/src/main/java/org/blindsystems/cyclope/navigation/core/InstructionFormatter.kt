package org.blindsystems.cyclope.navigation.core

import kotlin.math.roundToInt

/**
 * Turns an OSRM step into a sentence for a blind pedestrian.
 *
 * OSRM gives us the *data* (type = "turn", modifier = "left", name = "Rue X", distance)
 * but no ready-to-speak French text; this class is the part we add on top of the API.
 */
class InstructionFormatter(private val lang: Lang = Lang.FR) {

    /** Pedestrian-friendly rounding: 5 m steps under 100 m, 10 m under 500 m, 50 m above, km above 1 km. */
    fun distance(m: Double): String {
        if (m >= 1000) {
            val km = (m / 100.0).roundToInt() / 10.0
            val txt = if (km % 1.0 == 0.0) km.toInt().toString() else km.toString()
            return if (lang == Lang.FR) "${txt.replace('.', ',')} kilomètre${if (km >= 2) "s" else ""}"
            else "$txt kilometer${if (km > 1) "s" else ""}"
        }
        val step = when {
            m < 100 -> 5
            m < 500 -> 10
            else -> 50
        }
        val rounded = maxOf(step, (m / step).roundToInt() * step)
        return if (lang == Lang.FR) "$rounded mètres" else "$rounded meters"
    }

    private fun direction(modifier: String?): String = when (lang) {
        Lang.FR -> when (modifier) {
            "left" -> "tournez à gauche"
            "right" -> "tournez à droite"
            "slight left" -> "tournez légèrement à gauche"
            "slight right" -> "tournez légèrement à droite"
            "sharp left" -> "tournez franchement à gauche"
            "sharp right" -> "tournez franchement à droite"
            "uturn" -> "faites demi-tour"
            else -> "continuez tout droit"
        }
        Lang.EN -> when (modifier) {
            "left" -> "turn left"
            "right" -> "turn right"
            "slight left" -> "bear left"
            "slight right" -> "bear right"
            "sharp left" -> "turn sharp left"
            "sharp right" -> "turn sharp right"
            "uturn" -> "make a U-turn"
            else -> "continue straight"
        }
    }

    private fun onto(name: String): String =
        if (name.isBlank()) "" else if (lang == Lang.FR) " sur $name" else " onto $name"

    private fun side(modifier: String?): String = when {
        modifier == null -> ""
        modifier.contains("left") -> if (lang == Lang.FR) ", sur votre gauche" else ", on your left"
        modifier.contains("right") -> if (lang == Lang.FR) ", sur votre droite" else ", on your right"
        else -> ""
    }

    /** The action itself, without distance: "tournez à gauche sur Rue X". */
    fun action(step: RouteStep): String {
        val fr = lang == Lang.FR
        return when (step.type) {
            "depart" -> {
                val c = Geo.cardinal(step.bearingAfter, lang)
                if (fr) {
                    val article = if (c == "est" || c == "ouest") "l'" else "le " // vers l'est, vers le nord-est
                    "dirigez-vous vers $article$c${onto(step.name)}"
                } else {
                    "head $c${onto(step.name)}"
                }
            }
            "arrive" ->
                (if (fr) "vous êtes arrivé à destination" else "you have arrived at your destination") + side(step.modifier)
            "roundabout", "rotary" -> {
                val exit = step.exit
                if (fr) {
                    val n = if (exit == null) "" else if (exit == 1) " la première sortie" else " la ${exit}e sortie"
                    "au rond-point, prenez$n${onto(step.name)}"
                } else {
                    val n = if (exit == null) "" else " exit $exit"
                    "at the roundabout, take$n${onto(step.name)}"
                }
            }
            "end of road" ->
                (if (fr) "au bout de la rue, " else "at the end of the road, ") + direction(step.modifier) + onto(step.name)
            "fork" ->
                (if (fr) "à l'embranchement, " else "at the fork, ") + direction(step.modifier) + onto(step.name)
            "new name", "continue" ->
                if (step.modifier == null || step.modifier == "straight") {
                    (if (fr) "continuez tout droit" else "continue straight") + onto(step.name)
                } else {
                    direction(step.modifier) + onto(step.name)
                }
            else -> direction(step.modifier) + onto(step.name) // "turn", "merge", "notification", ...
        }
    }

    /** "Dans 50 mètres, tournez à gauche sur Rue X." */
    fun prepare(step: RouteStep, distanceM: Double): String = when {
        step.type == "arrive" && lang == Lang.FR -> "Destination dans ${distance(distanceM)}${side(step.modifier)}."
        step.type == "arrive" -> "Destination in ${distance(distanceM)}${side(step.modifier)}."
        lang == Lang.FR -> "Dans ${distance(distanceM)}, ${action(step)}."
        else -> "In ${distance(distanceM)}, ${action(step)}."
    }

    /** "Puis continuez sur 200 mètres." */
    fun thenContinue(distanceM: Double): String =
        if (lang == Lang.FR) "Puis continuez sur ${distance(distanceM)}."
        else "Then continue for ${distance(distanceM)}."

    /** "Maintenant, tournez à gauche sur Rue X." */
    fun now(step: RouteStep): String =
        if (step.type == "arrive") action(step).replaceFirstChar { it.uppercase() } + "."
        else if (lang == Lang.FR) "Maintenant, ${action(step)}."
        else "Now, ${action(step)}."

    fun routeSummary(route: Route): String {
        val minutes = maxOf(1, (route.durationS / 60.0).roundToInt())
        val unit = if (minutes == 1) "minute" else "minutes"
        return if (lang == Lang.FR) "Itinéraire calculé : ${distance(route.distanceM)}, environ $minutes $unit à pied."
        else "Route found: ${distance(route.distanceM)}, about $minutes $unit on foot."
    }

    fun offRoute(): String =
        if (lang == Lang.FR) "Attention, vous vous éloignez de l'itinéraire. Recalcul en cours."
        else "Warning, you are leaving the route. Recalculating."

    fun backOnRoute(): String =
        if (lang == Lang.FR) "Vous êtes de nouveau sur l'itinéraire." else "You are back on the route."

    fun gpsWeak(): String =
        if (lang == Lang.FR) "Signal GPS faible, guidage moins précis." else "Weak GPS signal, guidance is less precise."

    fun gpsRecovered(): String =
        if (lang == Lang.FR) "Signal GPS retrouvé." else "GPS signal recovered."
}
