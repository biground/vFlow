package com.chaomixian.vflow.core.workflow

import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionStepConstraintsSerializationTest {

    @Test
    fun `gson defaults missing constraints to empty list`() {
        val step = Gson().fromJson(
            """
            {
              "moduleId": "vflow.trigger.location",
              "parameters": { "event": "enter" },
              "id": "trigger-1"
            }
            """.trimIndent(),
            ActionStep::class.java
        )

        assertEquals("vflow.trigger.location", step.moduleId)
        assertTrue(step.constraints.isEmpty())
    }

    @Test
    fun `workflow import parser preserves trigger constraints`() {
        val parsed = WorkflowJsonImportParser().parse(
            """
            {
              "id": "workflow-constraints",
              "name": "位置约束",
              "triggers": [
                {
                  "moduleId": "vflow.trigger.location",
                  "parameters": { "event": "enter" },
                  "id": "trigger-1",
                  "constraints": [
                    {
                      "moduleId": "vflow.constraint.time_range",
                      "parameters": {
                        "start_time": "00:00",
                        "end_time": "12:00"
                      },
                      "id": "constraint-1"
                    }
                  ]
                }
              ],
              "steps": []
            }
            """.trimIndent()
        )

        val trigger = parsed.workflows.single().triggers.single()
        assertEquals("trigger-1", trigger.id)
        assertEquals(1, trigger.constraints.size)
        assertEquals("vflow.constraint.time_range", trigger.constraints.single().moduleId)
        assertEquals("00:00", trigger.constraints.single().parameters["start_time"])
    }
}
