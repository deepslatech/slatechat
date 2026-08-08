package ch.deepslate.slatechat.velocity.update

import ch.deepslate.slatechat.common.update.GitLabReleaseCheck
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

// Console-only by design -- unlike paper's UpdateChecker, there's no player-facing notify()/
// checkNow() here at all, only a log line on the proxy's own terminal.
object UpdateCheck {
    fun check(currentVersion: String, logger: Logger) {
        CompletableFuture.runAsync {
            val remote = GitLabReleaseCheck.fetchLatestTag()
            when {
                remote == null ->
                    logger.warn("Could not check for updates.")

                GitLabReleaseCheck.isNewer(remote, currentVersion) ->
                    logger.warn(
                        "A new SlateChat version ({}) is available! You are running {}. Download: {}",
                        remote,
                        currentVersion,
                        GitLabReleaseCheck.RELEASE_PAGE_URL,
                    )

                else ->
                    logger.info("SlateChat is up to date ({}).", currentVersion)
            }
        }
    }
}
