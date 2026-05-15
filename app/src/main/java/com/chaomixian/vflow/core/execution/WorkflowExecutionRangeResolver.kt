package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.logic.BlockNavigator

data class WorkflowExecutionRange(
    val startIndex: Int,
    val endExclusive: Int
)

object WorkflowExecutionRangeResolver {

    fun resolveLocalRange(steps: List<ActionStep>, startIndex: Int): WorkflowExecutionRange {
        if (steps.isEmpty()) return WorkflowExecutionRange(0, 0)
        val start = startIndex.coerceIn(0, steps.lastIndex)
        val module = ModuleRegistry.getModule(steps[start].moduleId)
        val behavior = module?.blockBehavior

        val endExclusive = when (behavior?.type) {
            BlockType.BLOCK_START, BlockType.BLOCK_MIDDLE -> {
                val endPosition = BlockNavigator.findEndBlockPosition(steps, start, behavior.pairingId)
                if (endPosition != -1) endPosition + 1 else steps.size
            }
            BlockType.BLOCK_END -> start + 1
            else -> findEnclosingScopeEndExclusive(steps, start)
        }.coerceIn(start + 1, steps.size)

        return WorkflowExecutionRange(startIndex = start, endExclusive = endExclusive)
    }

    private fun findEnclosingScopeEndExclusive(steps: List<ActionStep>, position: Int): Int {
        val blockStartStack = ArrayDeque<Int>()

        for (index in 0..position) {
            val module = ModuleRegistry.getModule(steps[index].moduleId) ?: continue
            when (module.blockBehavior.type) {
                BlockType.BLOCK_START -> blockStartStack.addLast(index)
                BlockType.BLOCK_END -> {
                    if (index < position && blockStartStack.isNotEmpty()) {
                        blockStartStack.removeLast()
                    }
                }
                else -> Unit
            }
        }

        val enclosingStart = blockStartStack.lastOrNull() ?: return steps.size
        val startModule = ModuleRegistry.getModule(steps[enclosingStart].moduleId) ?: return steps.size
        val endPosition = BlockNavigator.findEndBlockPosition(
            steps = steps,
            startPosition = enclosingStart,
            pairingId = startModule.blockBehavior.pairingId
        )
        return if (endPosition != -1) endPosition else steps.size
    }
}
