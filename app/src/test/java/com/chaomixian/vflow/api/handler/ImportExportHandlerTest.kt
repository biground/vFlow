package com.chaomixian.vflow.api.handler

import com.chaomixian.vflow.api.model.SimpleActionStepDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportExportHandlerTest {

    @Test
    fun `parse imported action step normalizes nested parameters`() {
        val step = parseImportedActionStep(
            mapOf(
                "moduleId" to "vflow.test.module",
                "parameters" to mapOf(
                    "headers" to mapOf(
                        "Authorization" to "Bearer token"
                    ),
                    "items" to listOf(
                        mapOf("name" to "first"),
                        2,
                        true
                    )
                ),
                "isDisabled" to true,
                "indentationLevel" to 2,
                "id" to "step-1"
            ),
            "step"
        )

        assertEquals("vflow.test.module", step.moduleId)
        assertTrue(step.isDisabled)
        assertEquals(2, step.indentationLevel)
        assertEquals("step-1", step.id)
        val headers = step.parameters["headers"] as Map<*, *>
        assertEquals("Bearer token", headers["Authorization"])
        val items = step.parameters["items"] as List<*>
        assertTrue(items.first() is Map<*, *>)
        assertEquals("first", (items.first() as Map<*, *>)["name"])
    }

    @Test
    fun `parse imported action step preserves constraints`() {
        val step = parseImportedActionStep(
            mapOf(
                "moduleId" to "vflow.trigger.location",
                "parameters" to mapOf("event" to "enter"),
                "id" to "trigger-1",
                "constraints" to listOf(
                    mapOf(
                        "moduleId" to "vflow.constraint.time_range",
                        "parameters" to mapOf("start_time" to "00:00", "end_time" to "12:00"),
                        "id" to "constraint-1"
                    )
                )
            ),
            "trigger"
        )

        assertEquals(1, step.constraints.size)
        assertEquals("vflow.constraint.time_range", step.constraints.single().moduleId)
        assertEquals("12:00", step.constraints.single().parameters["end_time"])
    }

    @Test
    fun `parse imported action step rejects non object parameters`() {
        val error = runCatching {
            parseImportedActionStep(
                mapOf(
                    "moduleId" to "vflow.test.module",
                    "parameters" to "bad"
                ),
                "step"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Invalid step format: parameters must be an object", error?.message)
    }

    @Test
    fun `simple action step dto normalizes nested parameters`() {
        val step = SimpleActionStepDto(
            id = "step-2",
            moduleId = "vflow.test.module",
            indentationLevel = 1,
            isDisabled = true,
            parameters = mapOf(
                "body" to mapOf(
                    "items" to listOf(
                        mapOf("value" to "nested")
                    )
                )
            )
        ).toActionStep()

        assertTrue(step.isDisabled)
        val body = step.parameters["body"] as Map<*, *>
        val items = body["items"] as List<*>
        assertTrue(items.first() is Map<*, *>)
        assertEquals("nested", (items.first() as Map<*, *>)["value"])
    }

    @Test
    fun `simple action step dto preserves constraints`() {
        val step = SimpleActionStepDto(
            id = "trigger-2",
            moduleId = "vflow.trigger.location",
            parameters = mapOf("event" to "enter"),
            constraints = listOf(
                SimpleActionStepDto(
                    id = "constraint-2",
                    moduleId = "vflow.constraint.weekday",
                    parameters = mapOf("days" to listOf(1, 2, 3))
                )
            )
        ).toActionStep()

        assertEquals(1, step.constraints.size)
        assertEquals("vflow.constraint.weekday", step.constraints.single().moduleId)
        assertEquals(listOf(1, 2, 3), step.constraints.single().parameters["days"])
    }
}
