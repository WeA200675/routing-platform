package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiNativeJniBoundaryTest {
    @Test fun socialAiJniRemainsSeparateAndFailClosedWithoutReviewedLlamaLink() {
        val root = File(System.getProperty("user.dir")).parentFile.parentFile.parentFile
        val source = File(root, "platform/android/app/src/main/cpp/social_ai_llama_jni.cpp").readText()
        val cmake = File(root, "platform/android/app/src/main/cpp/CMakeLists.txt").readText()
        assertTrue(source.contains("return JNI_FALSE"))
        assertTrue(source.contains("SOCIAL_AI_RUNTIME_BUILD_ID"))
        assertTrue(source.contains("SOCIAL_AI_RUNTIME_ARTIFACT_SHA256"))
        assertFalse(cmake.contains("social_ai_llama_jni.cpp"))
        assertFalse(cmake.contains("libllama"))
    }
}
