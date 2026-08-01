package com.bedrud.app.core.instance

private val HOST_PORT_REGEX = Regex("^[A-Za-z0-9.-]+(:\\d+)?$")

/**
 * Normalizes user/pasted input (a bare host, a host[:port], or a full URL with a scheme and/or
 * path) to a canonical `scheme://host[:port]/` server address.
 */
object ServerUrlCanonicalizer {
    /**
     * Strips whitespace, defaults to https (honors an explicit http:// for local/dev servers),
     * and discards any path/query/fragment -- a pasted full URL (e.g. copied from a browser's
     * address bar while looking at the server's login page) resolves to just its server address,
     * not the exact page it was copied from. Returns null for blank or malformed host[:port].
     */
    fun canonicalize(input: String): String? {
        val cleaned = input.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return null
        val scheme = if (cleaned.startsWith("http://", ignoreCase = true)) "http" else "https"
        var rest = cleaned
        listOf("https://", "http://").forEach { prefix ->
            if (rest.startsWith(prefix, ignoreCase = true)) rest = rest.substring(prefix.length)
        }
        rest = rest.trimEnd('/')
        if (rest.isEmpty()) return null
        val hostPort = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (!hostPort.matches(HOST_PORT_REGEX)) return null
        return "$scheme://$hostPort/"
    }
}
