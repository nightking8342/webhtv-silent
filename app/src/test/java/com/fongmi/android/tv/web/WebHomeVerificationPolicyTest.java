package com.fongmi.android.tv.web;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebHomeVerificationPolicyTest {

    @Test
    public void recognisesCloudflareChallengePlatform() {
        assertTrue(WebHomeVerificationPolicy.isVerificationUrl(
                "https://supjav.com/cdn-cgi/challenge-platform/h/b/orchestrate/jsch/v1?ray=8f2a1b3c4d5e"));
    }

    @Test
    public void recognisesTurnstileHost() {
        assertTrue(WebHomeVerificationPolicy.isVerificationUrl(
                "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"));
    }

    @Test
    public void recognisesCfChallengeToken() {
        assertTrue(WebHomeVerificationPolicy.isVerificationUrl(
                "https://supjav.com/zh/?__cf_chl_tk=abc123"));
    }

    @Test
    public void ignoresOrdinarySiteUrls() {
        assertFalse(WebHomeVerificationPolicy.isVerificationUrl("https://supjav.com/zh/"));
        assertFalse(WebHomeVerificationPolicy.isVerificationUrl("https://supjav.com/vod/play/123"));
        assertFalse(WebHomeVerificationPolicy.isVerificationUrl(""));
        assertFalse(WebHomeVerificationPolicy.isVerificationUrl(null));
    }

    @Test
    public void challengeStatusesAreNotFatal() {
        assertTrue(WebHomeVerificationPolicy.isChallengeStatus(401));
        assertTrue(WebHomeVerificationPolicy.isChallengeStatus(403));
        assertTrue(WebHomeVerificationPolicy.isChallengeStatus(429));
        assertTrue(WebHomeVerificationPolicy.isChallengeStatus(503));
        assertFalse(WebHomeVerificationPolicy.isChallengeStatus(404));
        assertFalse(WebHomeVerificationPolicy.isChallengeStatus(500));
    }
}
