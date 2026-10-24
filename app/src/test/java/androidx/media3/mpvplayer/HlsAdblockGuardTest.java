package androidx.media3.mpvplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HlsAdblockGuardTest {

    private static String playlist(int p16Segments, int p19Segments, int adSegments) {
        StringBuilder builder = new StringBuilder();
        builder.append("#EXTM3U\n")
                .append("#EXT-X-TARGETDURATION:10\n")
                .append("#EXT-X-PLAYLIST-TYPE:VOD\n")
                .append("#EXT-X-MEDIA-SEQUENCE:1\n");
        int total = p16Segments + p19Segments + adSegments;
        for (int i = 0; i < total; i++) {
            builder.append("#EXTINF:10.022,\n");
            if (i < p16Segments) {
                builder.append("https://p16-ad-site-sign-sg.tiktokcdn.com/ad-site-i18n-sg/").append(i).append(".image\n");
            } else if (i < p16Segments + p19Segments) {
                builder.append("https://p19-ad-site-sign-sg.tiktokcdn.com/ad-site-i18n-sg/").append(i).append(".image\n");
            } else {
                builder.append("https://ads.example.com/creative/").append(i).append(".ts\n");
            }
        }
        builder.append("#EXT-X-ENDLIST\n");
        return builder.toString();
    }

    private static String withoutHostGroup(String playlist, String rejectedHost) {
        StringBuilder builder = new StringBuilder();
        String[] lines = playlist.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("#EXTINF:") && i + 1 < lines.length && lines[i + 1].contains(rejectedHost)) {
                i++;
                continue;
            }
            builder.append(lines[i]).append('\n');
        }
        return builder.toString();
    }

    @Test
    public void rejectsUnmarkedRemovalOfAlternateCdnHostGroup() {
        String original = playlist(422, 435, 0);
        String filtered = withoutHostGroup(original, "p16-ad-site-sign-sg.tiktokcdn.com");

        assertFalse(HlsAdblockGuard.hasAdMarkers(original));
        assertFalse(HlsAdblockGuard.accepts(original, filtered));
    }

    @Test
    public void acceptsSmallUnmarkedRemoval() {
        String original = playlist(422, 435, 0);
        String filtered = withoutHostGroup(original, "p19-ad-site-sign-sg.tiktokcdn.com");
        String smallRemoval = playlist(422, 434, 0);

        assertTrue(HlsAdblockGuard.accepts(original, smallRemoval));
        assertFalse(HlsAdblockGuard.accepts(original, filtered));
    }

    @Test
    public void acceptsRemovalWhenPlaylistCarriesAdMarkers() {
        String original = playlist(422, 435, 20).replace("#EXTINF:10.022,",
                "#EXT-X-CUE-OUT:30\n#EXTINF:10.022,");
        String filtered = withoutHostGroup(original, "p16-ad-site-sign-sg.tiktokcdn.com");

        assertTrue(HlsAdblockGuard.hasAdMarkers(original));
        assertTrue(HlsAdblockGuard.accepts(original, filtered));
    }

    @Test
    public void keepsPlaylistWhenFilterChangesNothing() {
        String original = playlist(10, 10, 0);

        assertTrue(HlsAdblockGuard.accepts(original, original));
    }

    @Test
    public void totalsEveryExtinf() {
        String original = playlist(2, 2, 0);

        assertTrue(Math.abs(HlsAdblockGuard.totalDurationSeconds(original) - 40.088d) < 0.001d);
    }
}
