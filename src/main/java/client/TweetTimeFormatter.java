package client;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formats tweet timestamps the way X/Twitter does in the feed.
 *
 * <ul>
 *   <li>&lt; 1s → {@code now}</li>
 *   <li>&lt; 60s → {@code Ns}</li>
 *   <li>&lt; 60m → {@code Nm}</li>
 *   <li>&lt; 24h → {@code Nh}</li>
 *   <li>same calendar year → {@code MMM d} (e.g. {@code Jul 17})</li>
 *   <li>older years → {@code MMM d, yyyy}</li>
 * </ul>
 */
public final class TweetTimeFormatter {

    private static final DateTimeFormatter SAME_YEAR =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter OTHER_YEAR =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ABSOLUTE =
            DateTimeFormatter.ofPattern("h:mm a · MMM d, yyyy", Locale.ENGLISH);

    private TweetTimeFormatter() {}

    /** Short relative label shown next to the handle (e.g. {@code 3m}, {@code Jul 17}). */
    public static String formatRelative(Instant createdAt) {
        if (createdAt == null) {
            return "";
        }

        Instant now = Instant.now();
        if (createdAt.isAfter(now)) {
            return "now";
        }

        long seconds = Duration.between(createdAt, now).getSeconds();
        if (seconds < 1) {
            return "now";
        }
        if (seconds < 60) {
            return seconds + "s";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h";
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime created = createdAt.atZone(zone);
        ZonedDateTime nowZoned = now.atZone(zone);

        if (created.getYear() == nowZoned.getYear()) {
            return SAME_YEAR.format(created);
        }
        return OTHER_YEAR.format(created);
    }

    /**
     * Full absolute time for tooltips / detail (e.g. {@code 3:45 PM · Jul 17, 2026}).
     */
    public static String formatAbsolute(Instant createdAt) {
        if (createdAt == null) {
            return "";
        }
        return ABSOLUTE.format(createdAt.atZone(ZoneId.systemDefault()));
    }

    /** Feed header form: {@code · 3m}. */
    public static String formatFeedDot(Instant createdAt) {
        return "· " + formatRelative(createdAt);
    }
}
