package androidx.media3.mpvplayer;

/**
 * Rejects HLS ad-filter results that remove a large part of a playlist without any structural ad
 * marker.
 *
 * <p>{@code HlsAdsParser} also classifies segments by the leading part of their URI and can treat a
 * minority group as an ad run. Sources that spread one movie over alternating CDN hosts (for
 * example {@code p16-ad-site...} and {@code p19-ad-site...}) then lose a large share of the movie
 * and the filtered playlist reports a much shorter duration than the real content.
 */
final class HlsAdblockGuard {

    /** Share of the playlist duration that may be dropped without any ad marker in the source. */
    static final double MAX_UNMARKED_REMOVAL_RATIO = 0.25d;

    private static final String[] AD_MARKERS = {
            "#EXT-X-CUE-OUT",
            "#EXT-X-CUE-IN",
            "#EXT-X-DATERANGE",
            "#EXT-X-ASSET",
            "#EXT-X-DISCONTINUITY"
    };

    private HlsAdblockGuard() {
    }

    /**
     * Returns whether the filtered playlist may replace the original one.
     *
     * <p>A playlist that carries no ad marker keeps its original form as soon as the filter drops
     * more than {@link #MAX_UNMARKED_REMOVAL_RATIO} of its duration, because such a removal can only
     * come from filename or grouping heuristics.
     */
    static boolean accepts(String original, String filtered) {
        if (original == null || filtered == null) return true;
        if (original.equals(filtered)) return true;
        if (hasAdMarkers(original)) return true;
        double before = totalDurationSeconds(original);
        if (before <= 0) return true;
        double after = totalDurationSeconds(filtered);
        double removedRatio = (before - Math.max(0d, after)) / before;
        return removedRatio <= MAX_UNMARKED_REMOVAL_RATIO;
    }

    static boolean hasAdMarkers(String playlist) {
        if (playlist == null) return false;
        for (String marker : AD_MARKERS) {
            if (playlist.contains(marker)) return true;
        }
        return false;
    }

    /** Sums the {@code #EXTINF} durations of a playlist in seconds. */
    static double totalDurationSeconds(String playlist) {
        if (playlist == null) return 0d;
        double total = 0d;
        int index = 0;
        while (true) {
            int start = playlist.indexOf("#EXTINF:", index);
            if (start < 0) return total;
            int from = start + 8;
            int limit = Math.min(playlist.length(), from + 32);
            int to = from;
            while (to < limit) {
                char c = playlist.charAt(to);
                if (c == ',' || c == '\n' || c == '\r') break;
                to++;
            }
            try {
                total += Double.parseDouble(playlist.substring(from, to).trim());
            } catch (RuntimeException ignored) {
                // Ignore malformed entries; the guard only needs a duration estimate.
            }
            index = Math.max(to, from);
        }
    }
}
