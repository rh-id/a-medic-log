package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MedicineReminderEventHandlerTest {

    private static final long ONE_HOUR_MS = TimeUnit.HOURS.toMillis(1);
    private static final long ONE_DAY_MS = TimeUnit.DAYS.toMillis(1);
    private static final long ONE_MINUTE_MS = TimeUnit.MINUTES.toMillis(1);

    @Test
    public void futureStart_returnsDelayOfApproxOneHour() {
        Date startDate = new Date(System.currentTimeMillis() + ONE_HOUR_MS);

        long delay = MedicineReminderEventHandler.calculateInitialDelayMs(startDate, true);

        assertTrue(delay > 0);
        assertTrue(delay <= ONE_HOUR_MS + ONE_MINUTE_MS);
    }

    @Test
    public void startJustPassed_withinGrace_catchUp_returnsZero() {
        Date startDate = new Date(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(30));

        long delay = MedicineReminderEventHandler.calculateInitialDelayMs(startDate, true);

        assertEquals(0, delay);
    }

    @Test
    public void startJustPassed_catchUpDisabled_rollsToTomorrow() {
        Date startDate = new Date(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(30));

        long delay = MedicineReminderEventHandler.calculateInitialDelayMs(startDate, false);

        assertTrue(delay >= ONE_DAY_MS - ONE_MINUTE_MS);
        assertTrue(delay <= ONE_DAY_MS);
    }

    @Test
    public void startPassedBeyondGrace_catchUp_rollsToTomorrow() {
        Date startDate = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10));

        long delay = MedicineReminderEventHandler.calculateInitialDelayMs(startDate, true);

        // rolled forward one day from a start already 10 minutes in the past
        assertTrue(delay >= ONE_DAY_MS - TimeUnit.MINUTES.toMillis(11));
        assertTrue(delay <= ONE_DAY_MS - TimeUnit.MINUTES.toMillis(9));
    }
}
