package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiOfflineArchitecturePolicyTest {
    @Test
    fun productionAiSourcesContainNoNetworkTransportDependency() {
        val roots = sequenceOf(
            File("src/main/java/org/routingplatform/app/ai"),
            File("app/src/main/java/org/routingplatform/app/ai"),
        )
        val root = roots.firstOrNull(File::isDirectory)
            ?: error("Cannot locate production AI source directory.")

        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                SocialAiOfflineArchitecturePolicy.violations(file.readText())
                    .map { token -> "${file.name}: ${token}" }
            }
            .toList()

        assertTrue(
            "Local inference source acquired network-capable dependencies: ${violations.joinToString()}",
            violations.isEmpty(),
        )
    }
}
