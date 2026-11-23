package org.blindsystems.cyclope.vision

import org.junit.Assert.*
import org.junit.Test

class SelectionTest {
    private val person = Detection(0, 0.9f, Box(0f, 0f, 1f, 1f))
    private val car = person.copy(classId = 2)

    @Test fun emptySelectionMeansNoOutput() {
        assertTrue(filterClasses(listOf(person, car), emptySet()).isEmpty())
    }

    @Test fun selectedClassIsKept() {
        assertEquals(listOf(car), filterClasses(listOf(person, car), setOf(2)))
    }

    @Test fun selectionPreservesConfidenceOrder() {
        assertEquals(listOf(person, car), filterClasses(listOf(person, car), setOf(0, 2)))
    }

    @Test fun emptyInputStaysEmpty() {
        assertTrue(filterClasses(emptyList(), setOf(0)).isEmpty())
    }

    @Test fun filteringDoesNotMutateInput() {
        val input = listOf(person, car)
        filterClasses(input, setOf(0))
        assertEquals(2, input.size)
    }

    @Test fun cocoLabelOrderIsStable() {
        assertEquals(80, CocoLabels.english.size)
        assertEquals("person", CocoLabels.english[0])
        assertEquals("toothbrush", CocoLabels.english[79])
        assertEquals(80, CocoLabels.french.size)
    }

    @Test fun smallIsTheDefaultFirstEntry() {
        assertEquals(ModelSize.SMALL, ModelSize.entries.first())
        assertEquals("yolo26s_float16.tflite", ModelSize.SMALL.assetName)
    }

    @Test fun allModelAssetNamesAreDistinct() {
        assertEquals(3, ModelSize.entries.map { it.assetName }.toSet().size)
    }

    @Test fun cpuDoesNotRequireAccelerators() {
        assertEquals(DelegateChoice.CPU, selectDelegate(DelegateChoice.CPU, false, false).actual)
    }

    @Test fun supportedGpuIsSelected() {
        assertEquals(DelegateChoice.GPU, selectDelegate(DelegateChoice.GPU, true, false).actual)
    }

    @Test fun unsupportedGpuFallsBackToCpu() {
        val result = selectDelegate(DelegateChoice.GPU, false, true)
        assertEquals(DelegateChoice.CPU, result.actual)
        assertTrue(result.explanation.contains("unavailable"))
    }

    @Test fun availableNnapiIsSelectedWithoutPromisingNpu() {
        val result = selectDelegate(DelegateChoice.NNAPI, false, true)
        assertEquals(DelegateChoice.NNAPI, result.actual)
        assertTrue(result.explanation.contains("may"))
    }

    @Test fun unavailableNnapiFallsBackToCpu() {
        assertEquals(DelegateChoice.CPU, selectDelegate(DelegateChoice.NNAPI, true, false).actual)
    }
}
