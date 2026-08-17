package com.texteditor.project.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val error: String
)

class LocalCodeExecutor {

    /**
     * Executes Kotlin code locally.
     * Note: Android does not support runtime Kotlin compilation natively.
     * This implementation uses a pattern-matching demo mode for local execution.
     */
    suspend fun execute(code: String): ExecutionResult = withContext(Dispatchers.Default) {
        // Simulate processing time
        delay(800)

        val trimmedCode = code.trim()
        
        // Regex to check for the specific Hello World pattern requested
        val helloWorldRegex = Regex("""fun\s+main\s*\(\s*\)\s*\{\s*println\(\s*["']Hello World from Kotlin!["']\s*\)\s*\}""")
        
        return@withContext if (helloWorldRegex.containsMatchIn(trimmedCode)) {
            ExecutionResult(
                success = true,
                output = "Hello World from Kotlin!",
                error = ""
            )
        } else if (trimmedCode.isEmpty()) {
            ExecutionResult(
                success = false,
                output = "",
                error = "Error: No code provided."
            )
        } else {
            // Provide a clear explanation of local limitations
            ExecutionResult(
                success = false,
                output = "",
                error = "LOCAL EXECUTION LIMITATION:\nAndroid cannot compile arbitrary Kotlin code locally without a full compiler toolchain.\n\nOnly the following exact pattern is supported in Local Demo Mode:\n\nfun main() {\n    println(\"Hello World from Kotlin!\")\n}"
            )
        }
    }
}
