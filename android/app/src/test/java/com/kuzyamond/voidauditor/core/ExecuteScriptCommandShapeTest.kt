package com.kuzyamond.voidauditor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the ExecuteScript shell-command builder.
 *
 * Regression (found in smoke tour, CLEANER_ANALYZER preset): the first EXECUTE
 * produced `CODE_1: sh: syntax error: unexpected '('` although `sh -n` on the
 * preset body passed. Root cause: ShizukuManager already executes every command
 * through `newProcess("sh","-c", command)` (one shell layer), but
 * capabilityToCommand added a SECOND `sh -c "..."` wrapper and escaped each
 * payload `"` as `\\"`. The outer shell turns `\\` into `\` and the following
 * `"` then CLOSES the outer string, collapsing the quoting — so `(` inside
 * `echo "  HIGH: AppManager (Accessibility ON)"` landed as bare shell syntax.
 *
 * The executor-bound command for a BASH script payload must therefore be the
 * payload verbatim (one `sh -c` layer only, no wrapper, no escaping), exactly
 * like the adjacent ExecuteArbitraryShell case. For PYTHON3 the payload must be
 * passed as a single-quoted `python3 -c '...'` argument so the shell layer can
 * not expand `$`/break quotes inside the program text.
 */
class ExecuteScriptCommandShapeTest {

    private val cleanerLikePayload = """
        #!/bin/bash
        echo "==========================================="
        echo "   ANDROID CLEANER ANALYZER"
        echo "   Date: ${'$'}(date)"
        echo "[3] ACCESSIBILITY (HIGH RISK)"
        echo "  HIGH: AppManager (Accessibility ON)"
        for perm in READ_CONTACTS READ_SMS; do
          echo "--- ${'$'}perm ---"
          dumpsys package | grep -E "android.permission.${'$'}perm" -B 8 | grep "Package"
        done
        echo "   ANALYSIS COMPLETE"
    """.trimIndent()

    @Test
    fun bashScriptPayloadIsSentVerbatimWithoutExtraShWrapper() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.ExecuteScript(Capability.ScriptLanguage.BASH, cleanerLikePayload)
        )

        // The exact line that reaches ShizukuManager/newProcess("sh","-c", …):
        // the payload itself. Any extra "sh -c "…"" wrapper gets parsed by the
        // outer shell and corrupts quotes/parens.
        assertEquals("BASH script must be passed to the single sh -c layer verbatim", cleanerLikePayload, cmd)
        assertFalse("must not wrap payload in a second sh -c", cmd.startsWith("sh -c"))
        // Quote characters must survive untouched — no backslash escaping applied.
        assertFalse("must not escape payload quotes: $cmd", cmd.contains("\\\""))
    }

    @Test
    fun bashPayloadKeepsQuotedParenthesesIntact() {
        val payload = "#!/bin/bash\necho \"  HIGH: AppManager (Accessibility ON)\""
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.ExecuteScript(Capability.ScriptLanguage.BASH, payload)
        )
        // The reproduced corruption turned this into `sh: syntax error: unexpected '('`.
        assertEquals(payload, cmd)
        // The exact CLEANER_ANALYZER line that previously produced the syntax error:
        // parens inside a quoted echo argument must arrive intact.
        assertTrue("cmd=[$cmd] must keep quoted parens", cmd.contains("echo \"  HIGH: AppManager (Accessibility ON)\""))
    }

    @Test
    fun pythonPayloadIsSingleQuotedSoShellCannotExpandIt() {
        val payload = "print('hello')\nprint('\$HOME not expanded')"
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.ExecuteScript(Capability.ScriptLanguage.PYTHON3, payload)
        )

        assertTrue("must invoke python3 -c: $cmd", cmd.startsWith("python3 -c '"))
        assertTrue("payload must appear inside single quotes: $cmd", cmd.endsWith("'"))
        assertFalse("must not wrap python payload in active double quotes: $cmd", cmd.startsWith("python3 -c \""))
        assertTrue("payload dollars must survive literally: $cmd", cmd.contains("\$HOME not expanded"))
        // Every literal single quote in the payload must be escaped for the shell
        // layer ('\'' sequence) so python receives the original text.
        assertTrue("single quotes must be shell-escaped: $cmd", cmd.contains("'\\''"))
    }
}
