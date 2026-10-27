package org.routingplatform.app.navigation

enum class NavigationBackgroundPriority {
    Navigation,
    Safety,
    AiAnalysis,
}

data class NavigationBackgroundTask(
    val id: String,
    val priority: NavigationBackgroundPriority,
    val run: () -> Unit,
)

class NavigationBackgroundWorkQueue {
    private val pending = ArrayDeque<NavigationBackgroundTask>()

    fun offer(task: NavigationBackgroundTask) {
        if (task.priority == NavigationBackgroundPriority.Navigation ||
            task.priority == NavigationBackgroundPriority.Safety) {
            pending.removeAll { it.priority == NavigationBackgroundPriority.AiAnalysis }
        }
        pending.addLast(task)
    }

    fun runNext(
        navigationActive: Boolean,
    ): String? {
        val next = pending
            .firstOrNull {
                !navigationActive ||
                    it.priority != NavigationBackgroundPriority.AiAnalysis
            }
            ?: return null
        pending.remove(next)
        next.run()
        return next.id
    }

    fun cancelAiAnalysis() {
        pending.removeAll {
            it.priority == NavigationBackgroundPriority.AiAnalysis
        }
    }

    fun size(): Int = pending.size
}
