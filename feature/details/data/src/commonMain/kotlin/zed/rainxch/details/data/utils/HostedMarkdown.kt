package zed.rainxch.details.data.utils

internal fun preprocessGithubMarkdown(
    markdown: String,
    owner: String,
    repo: String,
    ref: String,
): String =
    preprocessMarkdown(
        markdown = markdown,
        baseUrl = githubRawBase(owner, repo, ref),
        linkBaseUrl = "https://github.com/$owner/$repo/blob/$ref/",
    )

internal fun preprocessForgejoMarkdown(
    markdown: String,
    host: String,
    owner: String,
    repo: String,
    ref: String,
): String =
    preprocessMarkdown(
        markdown = markdown,
        baseUrl = "https://$host/$owner/$repo/raw/branch/$ref/",
        linkBaseUrl = "https://$host/$owner/$repo/src/branch/$ref/",
    )

internal fun githubRawBase(
    owner: String,
    repo: String,
    ref: String,
): String = "https://raw.githubusercontent.com/$owner/$repo/$ref/"
