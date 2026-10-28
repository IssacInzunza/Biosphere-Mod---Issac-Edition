package mx.issacinzunza.biosphereworlds.easteregg;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Pure encounter policy. No engine/config initialization or world-generation random source. */
public final class ObserverRules {
    public static final int MAX_LIGHTS = 5;
    public static final int QUIET_TICKS = 40 * 60 * 20;
    public static final int SEPARATION = 512;
    static final int MIN_INTERVAL_SECONDS = 180 * 60;
    static final int MAX_INTERVAL_SECONDS = 360 * 60;
    static final int MIN_DURATION_SECONDS = 15;
    static final int MAX_DURATION_SECONDS = 40;
    static final int INTERFERENCE_RADIUS = 64;
    static final int BLOCK_SCAN_BUDGET = 8192;
    static final int MAX_DRAIN_FE = 5000;
    static final int EXTERIOR_CLEARANCE = 64;
    static final int INTERIOR_CLEARANCE = 32;
    static final int SHOT_RANGE = 224;
    static final int WARNING_TICKS = 40;
    static final int HOSTILE_TICKS = 200;

    private ObserverRules() { }

    public static int lightCount(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Expected roll 0..99");
        return roll < 35 ? 1 : roll < 65 ? 2 : roll < 85 ? 3 : roll < 95 ? 4 : 5;
    }

    public enum Mode { WATCH, FORMATION, DART, GLASS, FLYBY, ATTACK }

    /** Aggression is one percent of sightings, independent of flight and calendar bonuses. */
    public static Mode mode(int roll, boolean flying) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Expected roll 0..99");
        if (roll == 0) return Mode.ATTACK;
        if (flying && roll <= 60) return Mode.FLYBY;
        return roll < 35 ? Mode.WATCH : roll < 65 ? Mode.FORMATION : roll < 85 ? Mode.DART : Mode.GLASS;
    }

    public static boolean movingInAir(double distancePerSecond, int clearBlocksBelow) {
        return distancePerSecond >= 4 && distancePerSecond <= 120 && clearBlocksBelow >= 10;
    }

    public static float attackDamage(float health) { return Math.max(0, Math.min(2, health - 2)); }

    public static boolean halloween(LocalDate date) {
        return date.getMonthValue() == 10 && date.getDayOfMonth() >= 25
                || date.getMonthValue() == 11 && date.getDayOfMonth() <= 2;
    }

    /** Integrated over one complete day: 80% night, 15% twilight, 5% day. Not a quota per player. */
    public static double timeWeight(long dayTime) {
        long time = Math.floorMod(dayTime, 24000);
        if (time >= 13000 && time < 23000) return 1.92;
        if (time >= 1000 && time < 11000) return 0.12;
        return 0.90;
    }

    public static double rate(long dayTime, boolean mountain, boolean halloween) {
        return timeWeight(dayTime) * (mountain ? 1.5 : 1) * (halloween ? 3 : 1);
    }

    public static int sampleTicks(RandomGenerator random, int minSeconds, int maxSeconds) {
        if (minSeconds < 0 || minSeconds > maxSeconds || maxSeconds > Integer.MAX_VALUE / 20)
            throw new IllegalArgumentException("Invalid encounter interval");
        return random.nextInt(minSeconds * 20, maxSeconds * 20 + 1);
    }

    public record Offset(double x, double y, double z) { }

    public static Offset offset(Mode mode, int index, int count, double age, int duration, long seed) {
        if (index < 0 || index >= count || count > MAX_LIGHTS || duration <= 0)
            throw new IllegalArgumentException("Invalid formation");
        if (mode == Mode.FORMATION) return offset(index, count, age, duration, seed);
        double progress = Math.clamp(age / duration, 0, 1);
        double side = (index - (count - 1) / 2.0) * 8;
        double bob = Math.sin(age * 0.025 + index) * 1.2;
        double retreat = smooth((progress - 0.75) / 0.25);
        return switch (mode) {
            case WATCH -> new Offset(side + retreat * 45, bob + retreat * 18, Math.sin(age * 0.008) * 3);
            case DART -> {
                double first = smooth((progress - 0.15) / 0.12);
                double second = smooth((progress - 0.50) / 0.10);
                yield new Offset(side + first * 35 - second * 55 + retreat * 70, bob + second * 10 + retreat * 20, first * 18 - second * 30);
            }
            case GLASS -> new Offset(side + Math.sin(age * 0.012) * 4, bob - smooth(progress / 0.4) * 24 + retreat * 48, retreat * 45);
            // These offsets accompany a server-side moving focus; no vehicle classes are referenced.
            case FLYBY -> new Offset(side + 112, bob + 48 + retreat * 24, 80 - smooth(progress / 0.55) * 120 - retreat * 65);
            case ATTACK -> new Offset(side + 112 + Math.sin(age * 0.012) * 8, bob + 40 + retreat * 25, retreat * 60);
            default -> throw new IllegalArgumentException("Unknown mode");
        };
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Coherent formations slowly rotate, pause, change shape and finally disperse. */
    public static Offset offset(int index, int count, double age, int duration, long seed) {
        if (index < 0 || index >= count || count > MAX_LIGHTS || duration <= 0)
            throw new IllegalArgumentException("Invalid formation");
        double travel = age < 80 ? age * 0.09 : age < duration * 0.65 ? 7.2 : 7.2 + (age - duration * 0.65) * 0.12;
        double angle = index * Math.PI * 2 / count + (seed & 255) * Math.PI / 128 + age * 0.002;
        double spread = count == 1 ? 0 : 6 + Math.max(0, age - duration * 0.8) * 0.25;
        // Shift gradually from a horizontal line into a polygon in a tilted plane.
        double blend = Math.clamp((age - 80) / 120, 0, 1);
        double line = (index - (count - 1) / 2.0) * 6;
        double x = line * (1 - blend) + Math.cos(angle) * spread * blend;
        double y = Math.sin(angle) * spread * blend * 0.6;
        return new Offset(x + travel, y + Math.sin(age * 0.025) * 0.5, Math.sin(angle) * spread * blend * 0.4);
    }

    /** Short question/answer bursts, offset between lights; the entity remains detectable while dark. */
    public static float brightness(Mode mode, double age, int index, long seed) {
        return switch (mode) {
            case WATCH -> 0.85f + (float) Math.sin(age * 0.018 + index) * 0.10f;
            case FLYBY -> 0.95f;
            case DART -> Math.floorMod((int) age + (int) (seed & 63), 150) < 10 ? 0.15f : 1;
            default -> brightness(age, index, seed);
        };
    }

    public static float brightness(double age, int index, long seed) {
        int phase = Math.floorMod((int) age - index * 14 + (int) (seed & 31), 180);
        if (phase >= 60 && phase < 110) return phase % 16 < 7 ? 1 : 0;
        return 0.75f + (float) Math.sin(age * 0.06 + index) * 0.12f;
    }

    /** -1 restores ordinary circuit behavior between communication bursts. */
    public static int redstonePulse(int age, long seed) {
        int phase = Math.floorMod(age + (int) (seed & 31), 180);
        return phase >= 60 && phase < 110 ? (phase % 16 < 7 ? 15 : 0) : -1;
    }

    /** One budget for all lights. Repeated faces/blocks on the same network cannot multiply its allowance. */
    public static final class DrainBudget {
        private int remaining;
        private final Map<Object, Integer> allowances = new HashMap<>();

        public DrainBudget(int total) {
            if (total < 0) throw new IllegalArgumentException("Negative energy budget");
            remaining = total;
        }

        public int remaining() { return remaining; }

        public int request(Object target, long initialStored) {
            int allowance = allowances.computeIfAbsent(target, ignored -> (int) Math.min(Integer.MAX_VALUE, Math.max(0, initialStored) / 20));
            return Math.min(500, Math.min(remaining, allowance));
        }

        public void consumed(Object target, int actual) {
            int allowed = Math.min(remaining, allowances.getOrDefault(target, 0));
            if (actual < 0 || actual > allowed) throw new IllegalArgumentException("Energy extraction exceeds budget");
            remaining -= actual;
            allowances.put(target, allowances.getOrDefault(target, 0) - actual);
        }
    }
}
