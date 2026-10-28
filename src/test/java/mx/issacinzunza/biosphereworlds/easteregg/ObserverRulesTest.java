package mx.issacinzunza.biosphereworlds.easteregg;

import java.time.LocalDate;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObserverRulesTest {
    @Test void formationRarityIsExactAndNeverExceedsFive() {
        int[] counts = new int[5];
        for (int roll = 0; roll < 100; roll++) counts[ObserverRules.lightCount(roll) - 1]++;
        assertArrayEquals(new int[]{35, 30, 20, 10, 5}, counts);
        assertThrows(IllegalArgumentException.class, () -> ObserverRules.lightCount(-1));
        assertThrows(IllegalArgumentException.class, () -> ObserverRules.lightCount(100));
    }

    @Test void attacksStayOnePercentEvenInFlightAndEveryVariantIsReachable() {
        for (boolean flying : new boolean[]{false, true}) {
            var counts = new java.util.EnumMap<ObserverRules.Mode, Integer>(ObserverRules.Mode.class);
            for (int roll = 0; roll < 100; roll++) counts.merge(ObserverRules.mode(roll, flying), 1, Integer::sum);
            assertEquals(1, counts.get(ObserverRules.Mode.ATTACK));
            assertEquals(flying ? 60 : 0, counts.getOrDefault(ObserverRules.Mode.FLYBY, 0));
            if (!flying) for (var mode : java.util.List.of(ObserverRules.Mode.WATCH, ObserverRules.Mode.FORMATION,
                    ObserverRules.Mode.DART, ObserverRules.Mode.GLASS)) assertTrue(counts.containsKey(mode));
        }
        assertThrows(IllegalArgumentException.class, () -> ObserverRules.mode(100, false));
    }

    @Test void flightUsesSustainedWorldMovementAndDamageLeavesAHealthMargin() {
        assertTrue(ObserverRules.movingInAir(12, 12));
        assertFalse(ObserverRules.movingInAir(12, 2));
        assertFalse(ObserverRules.movingInAir(0, 12));
        assertFalse(ObserverRules.movingInAir(1000, 12));
        assertEquals(2, ObserverRules.attackDamage(20));
        assertEquals(1, ObserverRules.attackDamage(3));
        assertEquals(0, ObserverRules.attackDamage(2));
        assertEquals(0, ObserverRules.attackDamage(1));
    }

    @Test void variantsHaveDistinctContinuousTrajectoriesForEveryDuration() {
        var trajectories = new java.util.HashSet<String>();
        for (var mode : ObserverRules.Mode.values()) {
            var signature = new StringBuilder();
            for (int duration : new int[]{300, 800}) {
                for (int count = 1; count <= 5; count++) for (int index = 0; index < count; index++) {
                    var previous = ObserverRules.offset(mode, index, count, 0, duration, 91);
                    for (int age = 1; age < duration; age++) {
                        var next = ObserverRules.offset(mode, index, count, age, duration, 91);
                        double step = Math.sqrt(Math.pow(next.x() - previous.x(), 2) + Math.pow(next.y() - previous.y(), 2)
                                + Math.pow(next.z() - previous.z(), 2));
                        assertTrue(Double.isFinite(step) && step < 4, mode + " jumped by " + step);
                        previous = next;
                    }
                    signature.append(previous);
                }
            }
            assertTrue(trajectories.add(signature.toString()), "Duplicate behavior: " + mode);
        }
    }

    @Test void reactivePathsKeepTheirDistanceInsteadOfDivingAtTheWitness() {
        for (var mode : java.util.List.of(ObserverRules.Mode.FLYBY, ObserverRules.Mode.ATTACK))
            for (int count = 1; count <= 5; count++) for (int index = 0; index < count; index++)
                for (int age = 0; age < 800; age++) {
                    var offset = ObserverRules.offset(mode, index, count, age, 800, 91);
                    double distance = Math.sqrt(offset.x() * offset.x() + offset.y() * offset.y() + offset.z() * offset.z());
                    assertTrue(distance >= 80, mode + " approached the witness");
                    assertTrue(distance * 0.45 >= 32, mode + " interior offset lost its separation");
                }
    }

    @Test void completeDayHasTheAgreedExposureWeightsWithoutChangingItsMeanRate() {
        double day = 0, twilight = 0, night = 0;
        for (int time = 0; time < 24000; time++) {
            double weight = ObserverRules.timeWeight(time);
            if (time >= 1000 && time < 11000) day += weight;
            else if (time >= 13000 && time < 23000) night += weight;
            else twilight += weight;
            assertEquals(weight, ObserverRules.timeWeight(time - 24000));
            assertEquals(weight, ObserverRules.timeWeight(time + 24000));
        }
        assertEquals(24000, day + twilight + night, 1e-7);
        assertEquals(0.05, day / 24000, 1e-10);
        assertEquals(0.15, twilight / 24000, 1e-10);
        assertEquals(0.80, night / 24000, 1e-10);
        assertEquals(4.5, ObserverRules.rate(18000, true, true) / ObserverRules.rate(18000, false, false), 1e-12);
    }

    @Test void halloweenWindowCrossesTheMonthBoundaryEveryYear() {
        for (int year : new int[]{2024, 2026, 2030}) {
            assertFalse(ObserverRules.halloween(LocalDate.of(year, 10, 24)));
            assertTrue(ObserverRules.halloween(LocalDate.of(year, 10, 25)));
            assertTrue(ObserverRules.halloween(LocalDate.of(year, 10, 31)));
            assertTrue(ObserverRules.halloween(LocalDate.of(year, 11, 2)));
            assertFalse(ObserverRules.halloween(LocalDate.of(year, 11, 3)));
            assertFalse(ObserverRules.halloween(LocalDate.of(year, 1, 31)));
        }
    }

    @Test void intervalsRespectBoundsIncludingAFixedInterval() {
        var random = new Random(9927);
        for (int i = 0; i < 10000; i++) {
            int sampled = ObserverRules.sampleTicks(random, 15, 40);
            assertTrue(sampled >= 300 && sampled <= 800);
        }
        assertEquals(300, ObserverRules.sampleTicks(random, 15, 15));
        assertThrows(IllegalArgumentException.class, () -> ObserverRules.sampleTicks(random, 40, 15));
        assertThrows(IllegalArgumentException.class, () -> ObserverRules.sampleTicks(random, -1, 15));
    }

    @Test void allFormationsMoveContinuouslyAndBlinkWithoutLosingTheirEntity() {
        for (long seed : new long[]{0, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (int count = 1; count <= 5; count++) {
                for (int index = 0; index < count; index++) {
                    var previous = ObserverRules.offset(index, count, 0, 800, seed);
                    boolean dark = false, bright = false;
                    for (int age = 1; age < 800; age++) {
                        var next = ObserverRules.offset(index, count, age, 800, seed);
                        double step = Math.sqrt(Math.pow(next.x() - previous.x(), 2) + Math.pow(next.y() - previous.y(), 2) + Math.pow(next.z() - previous.z(), 2));
                        assertTrue(Double.isFinite(step) && step < 1, "Formation jumped: " + step);
                        float light = ObserverRules.brightness(age, index, seed);
                        assertTrue(light >= 0 && light <= 1);
                        dark |= light == 0;
                        bright |= light == 1;
                        assertTrue(java.util.Set.of(-1, 0, 15).contains(ObserverRules.redstonePulse(age, seed)));
                        previous = next;
                    }
                    assertTrue(dark && bright);
                }
            }
        }
    }

    @Test void repeatedAccessToOneNetworkCannotMultiplyItsFivePercentAllowance() {
        var budget = new ObserverRules.DrainBudget(5000);
        Object grid = new Object();
        assertEquals(500, budget.request(grid, 20000));
        budget.consumed(grid, 500);
        assertEquals(500, budget.request(grid, 100000000)); // Does not resample a larger allowance.
        budget.consumed(grid, 500);
        assertEquals(0, budget.request(grid, 20000));
        assertEquals(4000, budget.remaining());
        assertThrows(IllegalArgumentException.class, () -> budget.consumed(grid, 1));
    }

    @Test void wholeEncounterBudgetAndSmallBatteriesCannotBeOverdrawn() {
        var budget = new ObserverRules.DrainBudget(510);
        assertEquals(0, budget.request("tiny", 19));
        assertEquals(500, budget.request("first", Long.MAX_VALUE));
        budget.consumed("first", 499);
        assertEquals(11, budget.request("second", Long.MAX_VALUE));
        budget.consumed("second", 11);
        assertEquals(0, budget.remaining());
        assertEquals(0, budget.request("third", Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> budget.consumed("second", -1));
        assertEquals(0, new ObserverRules.DrainBudget(0).request("none", Long.MAX_VALUE));
    }
}
