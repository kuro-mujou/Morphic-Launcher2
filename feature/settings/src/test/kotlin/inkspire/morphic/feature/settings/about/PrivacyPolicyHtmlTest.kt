package inkspire.morphic.feature.settings.about

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps `privacy-policy.html` at the repo root in step with [PrivacyPolicy].
 *
 * **Two renderings of one policy, held together by a derivation rather than by care.** The pane draws the document;
 * this renders the same document to the page Play Console is given a URL for. They are the case the standing rule was
 * written for — the divergence would be *invisible* from inside either one, because each looks complete on its own,
 * and the thing that diverged would be a legal statement about what the app does with people's data.
 *
 * **It rewrites the file rather than only complaining**, then fails. A test that says "these differ" leaves the author
 * to hand-apply a diff to HTML, which is the same by-hand step this exists to remove. So the workflow is: edit
 * [PrivacyPolicy], run the tests once, commit the regenerated page. The failure is what makes the second step
 * unskippable.
 *
 * The HTML rendering lives here, in test sources, deliberately: nothing in the APK renders HTML, and a generator
 * shipped inside the app would be dead weight in every install.
 */
class PrivacyPolicyHtmlTest {

    @Test
    fun `the hosted page matches the policy the app shows`() {
        val page = repoRoot().resolve("privacy-policy.html")
        val rendered = PrivacyPolicy.toHtml()
        val existing = page.takeIf { it.isFile }?.readText()

        if (existing != rendered) {
            page.writeText(rendered)
            assertTrue(
                "privacy-policy.html was out of step with PrivacyPolicy.kt and has been regenerated from it. " +
                    "Review the diff and commit it, then re-run.",
                false,
            )
        }
    }

    /**
     * The repository root, found by walking up for `settings.gradle.kts`.
     *
     * Gradle runs a module's tests with the *module* directory as the working directory, and this file's module is
     * three levels down — but a hard-coded `../../..` breaks silently if the module ever moves, resolving to some
     * other directory and writing a stray HTML file into it. Walking up for the marker cannot do that.
     */
    private fun repoRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { it.resolve("settings.gradle.kts").isFile }
        ?: error("Could not find the repository root from ${File("").absolutePath}")
}

/** The policy as the standalone page. */
private fun PrivacyPolicyDocument.toHtml(): String = buildString {
    appendLine(PageHead.replace("{{title}}", "Morphic Launcher — $title"))
    appendLine("  <header>")
    appendLine("    <h1>$title — Morphic Launcher</h1>")
    val pkg = "<span class=\"pkg\">inkspire.morphic.launcher</span>"
    appendLine("    <p class=\"meta\">Package $pkg · Effective $effective</p>")
    appendLine("  </header>")
    appendLine()
    appendLine("  <div class=\"lede\">")
    appendLine("    <p>${lede.escaped()}</p>")
    appendLine("  </div>")
    blocks.forEach { block -> appendLine(block.toHtml()) }
    appendLine()
    appendLine("  <footer>Morphic Launcher · Last updated $effective</footer>")
    append(PageTail)
}

private fun PolicyBlock.toHtml(): String = when (this) {
    is PolicyBlock.Heading -> "\n  <h2>${text.escaped()}</h2>"
    is PolicyBlock.Subheading -> "\n  <h3>${text.escaped()}</h3>"
    is PolicyBlock.Paragraph -> "  <p>${text.escaped()}</p>"
    is PolicyBlock.Contact -> "  <p>${text.escaped()} <a href=\"mailto:$email\">$email</a></p>"
    is PolicyBlock.Bullets -> items.joinToString("\n", prefix = "  <ul>\n", postfix = "\n  </ul>") { bullet ->
        val term = bullet.term?.let { "<strong>${it.escaped()}</strong> " }.orEmpty()
        "    <li>$term${bullet.text.escaped()}</li>"
    }
}

/**
 * The three characters that would otherwise be markup.
 *
 * Not a general-purpose escaper, and not pretending to be one: the document is prose written in this repository, so
 * the attack surface is a typo rather than an injection. `&` first, or it would double-escape the entities the others
 * introduce.
 */
private fun String.escaped(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/**
 * The page's chrome, which the policy text has no opinion about.
 *
 * Light and dark both, `color-scheme` declared so form controls and scrollbars follow — the same rule the app's own
 * palette follows, for the same reason: a policy page that is a white rectangle at night is one people close.
 */
private val PageHead = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{{title}}</title>
<style>
  :root {
    color-scheme: light dark;
    --bg: #fbfbfa; --fg: #1a1a19; --muted: #5f5f5c; --rule: #e2e2df; --card: #ffffff;
  }
  @media (prefers-color-scheme: dark) {
    :root { --bg: #121211; --fg: #ebebe8; --muted: #9a9a95; --rule: #2b2b29; --card: #1a1a19; }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 0 1.25rem 5rem;
    background: var(--bg); color: var(--fg);
    font: 16px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
  }
  main { max-width: 44rem; margin: 0 auto; }
  header { padding: 4rem 0 2rem; border-bottom: 1px solid var(--rule); margin-bottom: 2.5rem; }
  h1 { font-size: 1.9rem; line-height: 1.2; margin: 0 0 .6rem; letter-spacing: -0.02em; }
  h2 { font-size: 1.15rem; margin: 2.75rem 0 .75rem; letter-spacing: -0.01em; }
  h3 { font-size: 1rem; margin: 1.75rem 0 .4rem; }
  p, li { color: var(--fg); }
  .meta { color: var(--muted); font-size: .9rem; margin: 0; }
  .pkg {
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: .875em; background: var(--card); border: 1px solid var(--rule);
    border-radius: 4px; padding: .1em .35em;
  }
  .lede {
    background: var(--card); border: 1px solid var(--rule); border-radius: 12px;
    padding: 1.25rem 1.4rem; margin: 0 0 1rem;
  }
  .lede p { margin: 0; font-size: 1.05rem; }
  ul { padding-left: 1.25rem; }
  li { margin: .4rem 0; }
  a { color: inherit; text-underline-offset: 3px; }
  footer {
    margin-top: 3.5rem; padding-top: 1.5rem; border-top: 1px solid var(--rule);
    color: var(--muted); font-size: .9rem;
  }
</style>
</head>
<body>
<main>
""".trimStart()

private val PageTail = """
</main>
</body>
</html>
""".trimStart()
