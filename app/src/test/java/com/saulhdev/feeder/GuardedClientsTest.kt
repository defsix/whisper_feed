package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every OkHttpClient this app builds refuses private addresses and plaintext.
 *
 * This one reads the source rather than the behaviour, which is unusual and is
 * the point. The guards were applied by hand at each client and three were
 * missed — the weather client, the Google Reader client, and the one in
 * RssLocalSync that fetches every subscribed feed on every scheduled sync,
 * which is the great majority of the fetching this app does. Each of those was
 * written correctly against its own file; what nobody could see from inside any
 * one file was the set.
 *
 * So the invariant is about the set, and the check has to be too: find every
 * client built anywhere in the app and require the guard on each. A unit test
 * of interceptor behaviour would have passed happily the whole time the gap was
 * open, because the interceptors were never the thing that was wrong.
 */
class GuardedClientsTest {

    private val mainSources = File("src/main/java")

    private fun kotlinFiles(): List<File> =
        mainSources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Source with `//` and block comments removed, so prose never matches. */
    private fun withoutComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

    /** The chain from `OkHttpClient.Builder()` up to and including `.build()`. */
    private fun builderChains(source: String): List<String> {
        val chains = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = source.indexOf("OkHttpClient.Builder()", from)
            if (start < 0) break
            val end = source.indexOf(".build()", start)
            chains += if (end < 0) source.substring(start) else source.substring(start, end + 8)
            from = start + 1
        }
        return chains
    }

    @Test
    fun `the sources are actually being read`() {
        // A path mistake would make every assertion below vacuous.
        assertTrue("no Kotlin sources found under $mainSources", kotlinFiles().size > 50)
    }

    @Test
    fun `every client built in the app refuses private addresses`() {
        // Either name will do. `onlyPublicHttps()` is the usual one and is
        // built on `refusingPrivateNetworks()`; a client that carries a
        // credential takes the narrower one on purpose, because guessing at
        // https is right for a public article and wrong for a password.
        val unguarded = kotlinFiles().flatMap { file ->
            builderChains(withoutComments(file.readText()))
                .filter {
                    !it.contains("onlyPublicHttps()") &&
                            !it.contains("refusingPrivateNetworks()")
                }
                .map { file.path }
        }
        assertEquals(
            "these clients are built with neither guard: $unguarded",
            emptyList<String>(),
            unguarded,
        )
    }

    @Test
    fun `the sync server client is not quietly upgraded to https`() {
        // PRIVACY.md promises that anything carrying a credential is refused
        // rather than upgraded, and this is the client that carries one.
        val greader = withoutComments(
            File(
                mainSources,
                "com/saulhdev/feeder/manager/sync/greader/GoogleReaderApi.kt",
            ).readText()
        )
        assertTrue(greader.contains("refusingPrivateNetworks()"))
        assertTrue(
            "the sync client must not take the https upgrade",
            !greader.contains("onlyPublicHttps()"),
        )
    }

    @Test
    fun `the client that syncs every feed is among them`() {
        // Named on its own because it is the one that was missed, and because a
        // regression here is the expensive one: it fetches unattended, in the
        // background, for every subscription at once.
        val sync = withoutComments(
            File(mainSources, "com/saulhdev/feeder/manager/sync/RssLocalSync.kt").readText()
        )
        assertTrue(sync.contains("onlyPublicHttps()"))
    }

    @Test
    fun `no TLS-disabling trust manager survives anywhere`() {
        // There used to be a `trustAllCerts()` extension — an X509TrustManager
        // whose check methods were empty and a hostname verifier that returned
        // true — reachable through a constructor default of `true`. Nothing
        // called it, which is exactly why it went unnoticed for so long.
        //
        // Comments are stripped before the search, because the file that
        // explains the removal names the thing it removed and would otherwise
        // report itself.
        val offenders = kotlinFiles().filter { file ->
            val code = withoutComments(file.readText())
            code.contains("X509TrustManager") ||
                    code.contains("sslSocketFactory(") ||
                    code.contains("hostnameVerifier")
        }
        assertEquals(emptyList<File>(), offenders)
    }
}
