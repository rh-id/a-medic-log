package m.co.rh.id.a_medic_log.app.ui.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Tests for the pure {@link PromoBannerSV#shouldShow(boolean, long, long)}
 * decision, no Android calls involved (the decision must not depend on the
 * device, only on its inputs).
 */
@RunWith(AndroidJUnit4.class)
public class PromoBannerSVTest {

    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final long NEVER_DISMISSED = 0L;
    // fixed "now" so the tests never depend on the wall clock
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void reshowCooldown_isFourteenDays() {
        assertEquals(14 * DAY_MILLIS, PromoBannerSV.COMPANION_PROMO_RESHOW_MILLIS);
    }

    @Test
    public void notInstalled_neverDismissed_shows() {
        assertTrue(PromoBannerSV.shouldShow(false, NEVER_DISMISSED, NOW));
        // 0 means "never dismissed", not a dismissal at the epoch, so the
        // card shows even with a clock close to the epoch
        assertTrue(PromoBannerSV.shouldShow(false, NEVER_DISMISSED, DAY_MILLIS));
    }

    @Test
    public void notInstalled_dismissed13DaysAgo_staysHidden() {
        // still inside the cooldown
        assertFalse(PromoBannerSV.shouldShow(false, NOW - 13 * DAY_MILLIS, NOW));
    }

    @Test
    public void notInstalled_dismissed15DaysAgo_showsAgain() {
        assertTrue(PromoBannerSV.shouldShow(false, NOW - 15 * DAY_MILLIS, NOW));
    }

    @Test
    public void notInstalled_dismissedExactlyAtCooldown_showsAgain() {
        // the boundary is inclusive: exactly 14 days after the dismissal the
        // card is back, one millisecond earlier it is not
        assertTrue(PromoBannerSV.shouldShow(false,
                NOW - PromoBannerSV.COMPANION_PROMO_RESHOW_MILLIS, NOW));
        assertFalse(PromoBannerSV.shouldShow(false,
                NOW - PromoBannerSV.COMPANION_PROMO_RESHOW_MILLIS + 1, NOW));
    }

    @Test
    public void installed_neverShows() {
        // the companion app is already there, whatever the dismissal state
        assertFalse(PromoBannerSV.shouldShow(true, NEVER_DISMISSED, NOW));
        assertFalse(PromoBannerSV.shouldShow(true, NOW - 13 * DAY_MILLIS, NOW));
        assertFalse(PromoBannerSV.shouldShow(true, NOW - 15 * DAY_MILLIS, NOW));
        assertFalse(PromoBannerSV.shouldShow(true,
                NOW - PromoBannerSV.COMPANION_PROMO_RESHOW_MILLIS, NOW));
    }
}
