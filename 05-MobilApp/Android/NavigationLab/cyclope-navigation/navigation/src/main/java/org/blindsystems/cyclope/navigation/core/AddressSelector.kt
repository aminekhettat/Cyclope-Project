package org.blindsystems.cyclope.navigation.core

/**
 * Button events coming from the glasses (firmware -> phone, over the Wi-Fi link).
 * The exact mapping must be agreed with the firmware teammate; this is our proposal.
 */
enum class ButtonEvent { SHORT_PRESS, LONG_PRESS, DOUBLE_PRESS }

/**
 * Requirement from the kick-off meeting:
 *  "Nominatim may return several addresses (54 rue Jean Jaurès exists in many towns).
 *   The list must be read aloud and the user must choose with the glasses buttons,
 *   and must be able to say that none is correct."
 *
 * Proposed mapping while choosing:
 *   SHORT_PRESS  = read the next candidate
 *   LONG_PRESS   = choose the candidate that was just read
 *   DOUBLE_PRESS = none is correct (dictate again)
 */
class AddressSelector(
    private val candidates: List<Place>,
    private val lang: Lang = Lang.FR,
    private val formatter: InstructionFormatter = InstructionFormatter(lang),
) {
    sealed class Result {
        /** Still choosing; [message] must be spoken. */
        data class Speak(val message: String) : Result()
        data class Selected(val place: Place, val message: String) : Result()
        data class NoneMatches(val message: String) : Result()
    }

    private var index = 0
    var finished = false
        private set

    private fun describe(i: Int): String {
        val p = candidates[i]
        val dist = p.distanceFromUserM?.let { d ->
            if (lang == Lang.FR) ", à ${formatter.distance(d)}" else ", ${formatter.distance(d)} away"
        }.orEmpty()
        return if (lang == Lang.FR) "Adresse ${i + 1} : ${p.spokenName}$dist."
        else "Address ${i + 1}: ${p.spokenName}$dist."
    }

    /** First message, to speak right after the Nominatim search. */
    fun start(): Result {
        if (candidates.isEmpty()) {
            finished = true
            return Result.NoneMatches(
                if (lang == Lang.FR) "Aucune adresse trouvée. Veuillez dicter l'adresse à nouveau."
                else "No address found. Please dictate the address again."
            )
        }
        val intro = if (lang == Lang.FR) {
            val n = candidates.size
            "J'ai trouvé $n adresse${if (n > 1) "s" else ""}. Appui court : suivante. Appui long : choisir. Double appui : aucune ne convient."
        } else {
            "I found ${candidates.size} address${if (candidates.size > 1) "es" else ""}. Short press: next. Long press: choose. Double press: none is correct."
        }
        return Result.Speak("$intro ${describe(0)}")
    }

    fun onButton(event: ButtonEvent): Result {
        check(!finished) { "Selection already finished" }
        return when (event) {
            ButtonEvent.SHORT_PRESS -> {
                index = (index + 1) % candidates.size
                val wrapped = index == 0 && candidates.size > 1
                val prefix = if (!wrapped) "" else if (lang == Lang.FR) "Retour au début. " else "Back to the first one. "
                Result.Speak(prefix + describe(index))
            }
            ButtonEvent.LONG_PRESS -> {
                finished = true
                val p = candidates[index]
                Result.Selected(
                    p,
                    if (lang == Lang.FR) "Destination choisie : ${p.spokenName}. Calcul de l'itinéraire."
                    else "Destination selected: ${p.spokenName}. Computing the route."
                )
            }
            ButtonEvent.DOUBLE_PRESS -> {
                finished = true
                Result.NoneMatches(
                    if (lang == Lang.FR) "D'accord, aucune adresse choisie. Veuillez dicter l'adresse à nouveau."
                    else "OK, no address selected. Please dictate the address again."
                )
            }
        }
    }
}
