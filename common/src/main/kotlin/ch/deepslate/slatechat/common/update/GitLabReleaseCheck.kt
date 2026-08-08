package ch.deepslate.slatechat.common.update

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.regex.Pattern

/**
 * Checks GitLab's releases/permalink/latest endpoint for the newest published SlateChat tag.
 * The CI pipeline already creates a GitLab Release (tag vX.Y.Z) on every version bump, so this
 * endpoint always reflects the latest published release for free.
 *
 * Platform-agnostic (pure java.net.http, no Bukkit/Velocity types) so paper and velocity share
 * one copy instead of two GitLab-API integrations that could drift apart. Fully synchronous and
 * fail-silent -- callers are responsible for running this off their platform's main thread.
 */
object GitLabReleaseCheck {

    const val RELEASE_PAGE_URL =
        "https://gitlab.com/deepslatech/slatechat/-/releases/permalink/latest"
    private const val RELEASE_API_URL =
        "https://gitlab.com/api/v4/projects/deepslatech%2Fslatechat/releases/permalink/latest"

    private val TAG_PATTERN: Pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([0-9.]+)\"")

    /** GET the release API and pull out tag_name, or null on any failure. Never throws. */
    fun fetchLatestTag(): String? {
        return try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // GitLab's permalink/latest endpoint answers with a 302 to the actual
                // release resource, not a direct 200 -- HttpClient doesn't follow
                // redirects by default, so this has to be explicit.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(RELEASE_API_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return null

            val matcher = TAG_PATTERN.matcher(response.body())
            if (!matcher.find()) return null

            matcher.group(1)
        } catch (_: Exception) {
            // Offline server, GitLab unreachable, forked project, etc.
            null
        }
    }

    /** Simple dot-separated numeric comparison — no need for full semver here. */
    fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
