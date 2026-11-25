package org.example.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a monitoring schedule {@code period} parsed from target configuration.
 * Supports:
 * <ul>
 *     <li>5-field cron expressions (e.g., {@code "0 0 * * *"})</li>
 *     <li>Duration notation with units {@code s}, {@code m}, {@code h}, {@code d}, {@code w} (e.g., {@code "1d"}, {@code "1h30m"})</li>
 *     <li>Plain positive integer values, interpreted as minutes (e.g., {@code "60"})</li>
 * </ul>
 */
public final class TargetPeriod {

    public enum Kind {
        CRON,
        DURATION
    }

    private static final Pattern PLAIN_MINUTES_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(?:\\d+[smhdw])+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_TOKEN_PATTERN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private static final int[] CRON_MIN = {0, 0, 1, 1, 0};
    private static final int[] CRON_MAX = {59, 23, 31, 12, 7};

    private final Kind kind;
    private final String raw;
    private final Duration duration;
    private final String cronExpression;

    private TargetPeriod(Kind kind, String raw, Duration duration, String cronExpression) {
        this.kind = kind;
        this.raw = raw;
        this.duration = duration;
        this.cronExpression = cronExpression;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static TargetPeriod fromJson(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return parse(s);
        }
        if (raw instanceof Integer || raw instanceof Long) {
            return parse(raw.toString());
        }
        throw new IllegalArgumentException("Invalid target period value: " + raw);
    }

    /**
     * Parses a raw {@code period} string into a {@link TargetPeriod}.
     *
     * @param raw raw period value from configuration (may be {@code null})
     * @return parsed {@link TargetPeriod}, or {@code null} if {@code raw} is {@code null}
     * @throws IllegalArgumentException if {@code raw} is blank or not a valid cron/duration/minute period
     */
    public static TargetPeriod parse(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Target period must not be blank");
        }

        if (PLAIN_MINUTES_PATTERN.matcher(trimmed).matches()) {
            long minutes = Long.parseLong(trimmed);
            if (minutes <= 0) {
                throw new IllegalArgumentException("Target period in minutes must be positive: " + raw);
            }
            return new TargetPeriod(Kind.DURATION, trimmed, Duration.ofMinutes(minutes), null);
        }

        if (DURATION_PATTERN.matcher(trimmed).matches()) {
            Duration parsed = parseDuration(trimmed);
            return new TargetPeriod(Kind.DURATION, trimmed, parsed, null);
        }

        if (isValidCron(trimmed)) {
            String normalized = trimmed.replaceAll("\\s+", " ");
            return new TargetPeriod(Kind.CRON, normalized, null, normalized);
        }

        throw new IllegalArgumentException("Invalid target period (expected cron, duration, or minutes): " + raw);
    }

    private static Duration parseDuration(String input) {
        Matcher matcher = DURATION_TOKEN_PATTERN.matcher(input);
        Duration total = Duration.ZERO;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            Duration part = switch (unit) {
                case 's' -> Duration.ofSeconds(amount);
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                case 'w' -> Duration.ofDays(Math.multiplyExact(amount, 7L));
                default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
            };
            total = total.plus(part);
        }
        if (total.isZero() || total.isNegative()) {
            throw new IllegalArgumentException("Target period duration must be positive: " + input);
        }
        return total;
    }

    private static boolean isValidCron(String expr) {
        String[] fields = expr.split("\\s+");
        if (fields.length != 5) {
            return false;
        }
        for (int i = 0; i < fields.length; i++) {
            if (!isValidCronField(fields[i], CRON_MIN[i], CRON_MAX[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidCronField(String field, int min, int max) {
        if (field.isEmpty()) {
            return false;
        }
        String[] parts = field.split(",", -1);
        for (String part : parts) {
            if (!isValidCronPart(part, min, max)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidCronPart(String part, int min, int max) {
        if (part.isEmpty()) {
            return false;
        }
        String rangePart = part;
        int slashIdx = part.indexOf('/');
        if (slashIdx >= 0) {
            if (part.indexOf('/', slashIdx + 1) >= 0) {
                return false;
            }
            String stepStr = part.substring(slashIdx + 1);
            if (!PLAIN_MINUTES_PATTERN.matcher(stepStr).matches()) {
                return false;
            }
            int step = Integer.parseInt(stepStr);
            if (step <= 0) {
                return false;
            }
            rangePart = part.substring(0, slashIdx);
        }

        if ("*".equals(rangePart)) {
            return true;
        }

        int dashIdx = rangePart.indexOf('-');
        if (dashIdx >= 0) {
            if (rangePart.indexOf('-', dashIdx + 1) >= 0) {
                return false;
            }
            String startStr = rangePart.substring(0, dashIdx);
            String endStr = rangePart.substring(dashIdx + 1);
            if (!PLAIN_MINUTES_PATTERN.matcher(startStr).matches()
                    || !PLAIN_MINUTES_PATTERN.matcher(endStr).matches()) {
                return false;
            }
            int start = Integer.parseInt(startStr);
            int end = Integer.parseInt(endStr);
            return start >= min && end <= max && start <= end;
        }

        if (!PLAIN_MINUTES_PATTERN.matcher(rangePart).matches()) {
            return false;
        }
        int value = Integer.parseInt(rangePart);
        return value >= min && value <= max;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isCron() {
        return kind == Kind.CRON;
    }

    public boolean isDuration() {
        return kind == Kind.DURATION;
    }

    @JsonValue
    public String getRaw() {
        return raw;
    }

    public Optional<Duration> getDuration() {
        return Optional.ofNullable(duration);
    }

    public Optional<String> getCronExpression() {
        return Optional.ofNullable(cronExpression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetPeriod that)) return false;
        return kind == that.kind && Objects.equals(raw, that.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, raw);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "TargetPeriod{kind=%s, raw='%s'}", kind, raw);
    }
}
