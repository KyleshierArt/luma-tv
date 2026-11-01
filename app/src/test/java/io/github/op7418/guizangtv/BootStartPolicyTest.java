package io.github.op7418.guizangtv;

import org.junit.Test;
import static org.junit.Assert.*;

public class BootStartPolicyTest {
    @Test public void tv6CachedStartupSurvivesEmptySystemTarget() {
        assertTrue(BootStartPolicy.tv6TargetAllowed("","luma.tv"));
        assertTrue(BootStartPolicy.tv6TargetAllowed(null,"luma.tv"));
        assertNull(BootStartPolicy.tv6SkipReason(true,
                BootStartPolicy.tv6TargetAllowed("","luma.tv"),80000,75000));
    }
    @Test public void tv6ExplicitDifferentStartupStillWins() {
        assertTrue(BootStartPolicy.tv6TargetAllowed("luma.tv","luma.tv"));
        assertFalse(BootStartPolicy.tv6TargetAllowed("other.tv","luma.tv"));
        assertNotNull(BootStartPolicy.tv6SkipReason(true,
                BootStartPolicy.tv6TargetAllowed("other.tv","luma.tv"),80000,75000));
    }
    @Test public void tv6CachedTargetCannotOverrideOptOut() {
        assertNotNull(BootStartPolicy.tv6SkipReason(false,
                BootStartPolicy.tv6TargetAllowed("","luma.tv"),80000,75000));
    }
    @Test public void tv6WakeCanEnterAgainInSameBoot() {
        assertNull(BootStartPolicy.tv6SkipReason(true,true,80000,75000));
    }
    @Test public void tv6DuplicateEntryIsDebounced() {
        assertNotNull(BootStartPolicy.tv6SkipReason(true,true,76999,75000));
        assertNull(BootStartPolicy.tv6SkipReason(true,true,77000,75000));
    }
    @Test public void tv6ColdRebootResetsElapsedClock() {
        assertNull(BootStartPolicy.tv6SkipReason(true,true,5000,75000));
    }
    @Test public void tv6DisabledOrChangedTargetCannotTakeFocus() {
        assertNotNull(BootStartPolicy.tv6SkipReason(false,true,80000,75000));
        assertNotNull(BootStartPolicy.tv6SkipReason(true,false,80000,75000));
    }
    @Test public void newBootCanEnter() {
        assertNull(BootStartPolicy.skipReason(true, true, 20, 19, 75000));
    }
    @Test public void disabledCannotEnterEvenDuringBoot() {
        assertNotNull(BootStartPolicy.skipReason(true, false, 20, 19, 75000));
    }
    @Test public void unsupportedModelCannotEnter() {
        assertNotNull(BootStartPolicy.skipReason(false, true, 20, 19, 75000));
    }
    @Test public void duplicateCannotTakeFocusFromHomeOrHdmi() {
        assertNotNull(BootStartPolicy.skipReason(true, true, 20, 20, 76000));
    }
    @Test public void unknownBootIsSkipped() {
        assertNotNull(BootStartPolicy.skipReason(true, true, -1, -1, 75000));
    }
    @Test public void lateDeliveryCannotInterruptUse() {
        assertNull(BootStartPolicy.skipReason(true, true, 20, 19, 600000));
        assertNotNull(BootStartPolicy.skipReason(true, true, 20, 19, 600001));
    }
    @Test public void invalidClockIsSkipped() {
        assertNotNull(BootStartPolicy.skipReason(true, true, 20, 19, -1));
    }
}
