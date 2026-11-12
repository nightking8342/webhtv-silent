package com.fongmi.android.tv.web;

import java.util.Locale;

/**
 * Cloudflare / Turnstile verification pages are part of a healthy login flow, not a load failure.
 *
 * <p>Cloudflare serves its managed challenge with HTTP 403 (and sometimes 429/503), then redirects
 * away from the original document while the user is solving it. The WebHome runtime must keep that
 * document alive and must not treat the status code as fatal.
 */
public final class WebHomeVerificationPolicy {

    private static final String[] HOST_MARKERS = {
            "challenges.cloudflare.com",
            "cloudflarechallenge.com"
    };

    private static final String[] PATH_MARKERS = {
            "/cdn-cgi/challenge-platform",
            "/cdn-cgi/l/chk_",
            "__cf_chl",
            "cf_clearance",
            "/verify"
    };

    private WebHomeVerificationPolicy() {
    }

    public static boolean isVerificationUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String value = url.toLowerCase(Locale.ROOT);
        for (String host : HOST_MARKERS) if (value.contains(host)) return true;
        for (String path : PATH_MARKERS) if (value.contains(path)) return true;
        return false;
    }

    /** Statuses a challenge or a soft rate limit can use instead of a normal 200 document. */
    public static boolean isChallengeStatus(int status) {
        return status == 401 || status == 403 || status == 429 || status == 503;
    }
}
