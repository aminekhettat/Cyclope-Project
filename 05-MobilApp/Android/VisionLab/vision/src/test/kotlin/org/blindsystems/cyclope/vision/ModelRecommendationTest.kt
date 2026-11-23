package org.blindsystems.cyclope.vision

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ModelRecommendationTest {
    private fun phone(
        gib: Long,
        gpu: Boolean = true,
        nnapi: Boolean = true,
    ) = PhoneCapabilities(gib * ModelAdvisor.BYTES_PER_GIB, ModelAdvisor.BYTES_PER_GIB, gpu, nnapi, 8, "Example SoC", 35)

    @Test fun lowRamAlwaysRecommendsNano() {
        for (ram in 0L..3L) for (gpu in listOf(false, true)) {
            assertEquals(ModelSize.NANO, ModelAdvisor.evaluate(phone(ram, gpu), null).recommended)
        }
    }

    @Test fun intermediateRamRemainsConservative() {
        for (ram in 4L..5L) {
            assertEquals(ModelSize.NANO, ModelAdvisor.evaluate(phone(ram), null).selected)
        }
    }

    @Test fun sixGiBWithGpuRecommendsSmall() {
        val result = ModelAdvisor.evaluate(phone(6), null)
        assertEquals(ModelSize.SMALL, result.recommended)
        assertEquals(result.recommended, result.selected)
        assertFalse(result.hasUserChoice)
        assertFalse(result.mediumPrecisionOption)
        assertTrue(result.explanation.contains("preselected"))
    }

    @Test fun nnapiApiAloneNeverProvesAcceleration() {
        for (ram in listOf(3L, 6L, 8L, 16L)) {
            val result = ModelAdvisor.evaluate(phone(ram, gpu = false), null)
            assertEquals(ModelSize.NANO, result.recommended)
            assertTrue(result.explanation.contains("not proof of hardware acceleration"))
        }
    }

    @Test fun gpuWorksWithoutNnapiApi() {
        val result = ModelAdvisor.evaluate(phone(6, nnapi = false), null)
        assertEquals(ModelSize.SMALL, result.recommended)
        assertTrue(result.explanation.contains("NNAPI API unavailable"))
    }

    @Test fun highRamOffersMediumWithoutAutomaticUpgrade() {
        val result = ModelAdvisor.evaluate(phone(8), null)
        assertEquals(ModelSize.SMALL, result.recommended)
        assertTrue(result.mediumPrecisionOption)
        assertTrue(result.explanation.contains("better-precision option"))
        assertTrue(ModelAdvisor.options(result).single { it.size == ModelSize.MEDIUM }.label.contains("Better-precision option"))
    }

    @Test fun highRamWithoutGpuStillOffersManualMedium() {
        val result = ModelAdvisor.evaluate(phone(16, gpu = false, nnapi = false), null)
        assertEquals(ModelSize.NANO, result.recommended)
        assertTrue(result.mediumPrecisionOption)
        assertTrue(result.explanation.contains("no confirmed LiteRT-compatible GPU"))
    }

    @Test fun thresholdsAreExactBinaryGiB() {
        val smallThreshold = phone(6)
        assertEquals(ModelSize.NANO, ModelAdvisor.evaluate(smallThreshold.copy(totalRamBytes = smallThreshold.totalRamBytes - 1), null).recommended)
        val mediumThreshold = phone(8)
        assertFalse(ModelAdvisor.evaluate(mediumThreshold.copy(totalRamBytes = mediumThreshold.totalRamBytes - 1), null).mediumPrecisionOption)
        assertEquals(4L, ModelAdvisor.LOW_RAM_GIB)
        assertEquals(6L, ModelAdvisor.SMALL_RAM_GIB)
        assertEquals(8L, ModelAdvisor.MEDIUM_OPTION_RAM_GIB)
    }

    @Test fun everySavedModelWinsOnEveryPhoneTier() {
        for (size in ModelSize.entries) for (ram in listOf(2L, 6L, 8L)) {
            val result = ModelAdvisor.evaluate(phone(ram), size.name)
            assertEquals(size, result.selected)
            assertTrue(result.hasUserChoice)
            assertTrue(result.explanation.contains("saved choice, ${size.name.lowercase()}, is retained"))
        }
    }

    @Test fun invalidPreferenceFallsBackToRecommendation() {
        for (saved in listOf(null, "invalid", "", "small")) {
            val result = ModelAdvisor.evaluate(phone(6), saved)
            assertEquals(ModelSize.SMALL, result.selected)
            assertFalse(result.hasUserChoice)
        }
    }

    @Test fun reevaluationUsesFreshCapabilitiesWithoutReplacingSavedChoice() {
        val initial = ModelAdvisor.evaluate(phone(3), "MEDIUM")
        val reevaluated = ModelAdvisor.evaluate(phone(8), initial.selected.name)
        assertEquals(ModelSize.NANO, initial.recommended)
        assertEquals(ModelSize.SMALL, reevaluated.recommended)
        assertEquals(ModelSize.MEDIUM, reevaluated.selected)
        assertTrue(reevaluated.hasUserChoice)
    }

    @Test fun reevaluationWithoutUserChoiceUpdatesPreselection() {
        assertEquals(ModelSize.NANO, ModelAdvisor.evaluate(phone(3), null).selected)
        assertEquals(ModelSize.SMALL, ModelAdvisor.evaluate(phone(8), null).selected)
    }

    @Test fun mediumOnLowRamHasOnlyAnInformativeNote() {
        val result = ModelAdvisor.evaluate(phone(3), "MEDIUM")
        assertEquals(ModelSize.MEDIUM, result.selected)
        assertTrue(result.explanation.contains("remains selectable"))
        assertEquals(3, ModelAdvisor.options(result).size)
        assertFalse(ModelAdvisor.evaluate(phone(4), "MEDIUM").explanation.contains("may use more memory"))
    }

    @Test fun selectorAlwaysOffersAllThreeAssetsWithOneTextualRecommendation() {
        for (ram in listOf(2L, 6L, 8L)) {
            val result = ModelAdvisor.evaluate(phone(ram), null)
            val options = ModelAdvisor.options(result)
            assertEquals(ModelSize.entries.toSet(), options.map { it.size }.toSet())
            assertEquals(3, options.map { it.size.assetName }.toSet().size)
            assertEquals(result.recommended, options.single { it.label.contains("Recommended for your phone") }.size)
            assertTrue(options.all { it.label.startsWith(it.size.name.lowercase()) })
        }
    }

    @Test fun transientMemoryAndSocNamesCannotUpgradeRecommendation() {
        val original = phone(6)
        val changed = original.copy(availableRamBytes = 0, cpuCores = 1, soc = "Unknown", androidApi = 26)
        assertEquals(ModelAdvisor.evaluate(original, null).recommended, ModelAdvisor.evaluate(changed, null).recommended)
        assertTrue(ModelAdvisor.evaluate(changed, null).explanation.contains("0.0 GiB available"))
        assertEquals(1, changed.cpuCores)
        assertEquals("Unknown", changed.soc)
        assertEquals(26, changed.androidApi)
    }

    @Test fun unknownMemoryIsReportedConservatively() {
        val result = ModelAdvisor.evaluate(phone(0).copy(totalRamBytes = -1, availableRamBytes = -1), null)
        assertEquals(ModelSize.NANO, result.recommended)
        assertTrue(result.explanation.contains("0.0 GiB total RAM"))
    }

    @Test fun manifestListsExactlyTheThreeSelectorAssetsAndTensorContracts() {
        val manifest = File("../app/src/main/assets/model-manifest.json").readText()
        val assets = Regex("\"file\": \"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(ModelSize.entries.map { it.assetName }.toSet(), assets.toSet())
        assertEquals(3, assets.size)
        assertEquals(3, Regex("\"sha256\": \"[a-f0-9]{64}\"").findAll(manifest).count())
        assertEquals(3, Regex("\"input\":\\s*\\[\\s*1,\\s*640,\\s*640,\\s*3\\s*]").findAll(manifest).count())
        assertEquals(3, Regex("\"output\":\\s*\\[\\s*1,\\s*300,\\s*6\\s*]").findAll(manifest).count())
    }
}
