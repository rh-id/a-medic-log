package m.co.rh.id.a_medic_log.app.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_medic_log.app.workmanager.Tags;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.a_medic_log.base.state.ProfileState;

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

    @Test
    public void calculateTag_matchesReminderIdTag() {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.id = 42L;

        assertEquals(Tags.MEDICINE_REMINDER_TAG + 42L,
                MedicineReminderEventHandler.calculateTag(medicineReminder));
    }

    @Test
    public void cancelPath_oneUniqueTagPerReminder_fromPreDeletePayload() {
        // mirrors DeleteProfileCmd: the reminders are loaded before the delete
        // transaction and carried in the profileDeleted ProfileState payload,
        // whose reminder tags are what the handler cancels afterwards
        MedicineReminder reminder1 = new MedicineReminder();
        reminder1.id = 1L;
        MedicineReminder reminder2 = new MedicineReminder();
        reminder2.id = 2L;
        List<MedicineReminder> medicineReminders = Arrays.asList(reminder1, reminder2);

        Set<String> tags = new HashSet<>();
        for (MedicineReminder medicineReminder : medicineReminders) {
            tags.add(MedicineReminderEventHandler.calculateTag(medicineReminder));
        }

        assertEquals(2, tags.size());
        assertTrue(tags.contains(Tags.MEDICINE_REMINDER_TAG + 1L));
        assertTrue(tags.contains(Tags.MEDICINE_REMINDER_TAG + 2L));
    }

    private static ProfileState profileStateWith(NoteState... noteStates) {
        ProfileState profileState = new ProfileState(new Profile());
        profileState.updateNoteList(Arrays.asList(noteStates));
        return profileState;
    }

    private static NoteState noteStateWith(MedicineState... medicineStates) {
        NoteState noteState = new NoteState();
        noteState.updateMedicineStates(Arrays.asList(medicineStates));
        return noteState;
    }

    private static MedicineState medicineStateWith(MedicineReminder... medicineReminders) {
        MedicineState medicineState = new MedicineState();
        medicineState.updateMedicineReminderList(Arrays.asList(medicineReminders));
        return medicineState;
    }

    private static MedicineReminder reminder(long id) {
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.id = id;
        return medicineReminder;
    }

    @Test
    public void extractMedicineReminders_nullInput_returnsEmpty() {
        assertTrue(MedicineReminderEventHandler.extractMedicineReminders(null).isEmpty());
    }

    @Test
    public void extractMedicineReminders_emptyProfileState_returnsEmpty() {
        ProfileState profileState = new ProfileState(new Profile());

        assertTrue(MedicineReminderEventHandler.extractMedicineReminders(profileState).isEmpty());
    }

    @Test
    public void extractMedicineReminders_notesWithoutMedicines_returnsEmpty() {
        ProfileState profileState = profileStateWith(new NoteState(), new NoteState());

        assertTrue(MedicineReminderEventHandler.extractMedicineReminders(profileState).isEmpty());
    }

    @Test
    public void extractMedicineReminders_medicinesWithoutReminders_returnsEmpty() {
        ProfileState profileState = profileStateWith(noteStateWith(new MedicineState()));

        assertTrue(MedicineReminderEventHandler.extractMedicineReminders(profileState).isEmpty());
    }

    @Test
    public void extractMedicineReminders_nestedChain_collectsAllRemindersInOrder() {
        ProfileState profileState = profileStateWith(
                noteStateWith(medicineStateWith(reminder(1L)), medicineStateWith(reminder(2L))),
                noteStateWith(medicineStateWith(reminder(3L))));

        List<MedicineReminder> extracted = MedicineReminderEventHandler.extractMedicineReminders(profileState);

        assertEquals(3, extracted.size());
        assertEquals(1L, extracted.get(0).id.longValue());
        assertEquals(2L, extracted.get(1).id.longValue());
        assertEquals(3L, extracted.get(2).id.longValue());
    }

    @Test
    public void extractMedicineReminders_nullMembersInChain_skippedSafely() {
        ProfileState profileState = new ProfileState(new Profile());
        profileState.updateNoteList(Arrays.asList(
                null, noteStateWith(null, medicineStateWith(reminder(7L)))));

        List<MedicineReminder> extracted = MedicineReminderEventHandler.extractMedicineReminders(profileState);

        assertEquals(1, extracted.size());
        assertEquals(7L, extracted.get(0).id.longValue());
    }

    @Test
    public void calculateTag_isAlsoTheLegacyUniqueWorkName() {
        // the one-time WorkManager-to-alarm migration cancels both the tag and
        // the unique work with this same name for every reminder row
        MedicineReminder medicineReminder = new MedicineReminder();
        medicineReminder.id = 7L;

        assertEquals(Tags.MEDICINE_REMINDER_TAG + 7L,
                MedicineReminderEventHandler.calculateTag(medicineReminder));
    }

    @Test
    public void resolveAlarmMethod_api21And22_usesSetExact() {
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.SET_EXACT,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(21, true));
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.SET_EXACT,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(22, false));
    }

    @Test
    public void resolveAlarmMethod_api23To30_usesAlarmClockWithoutPermissionCheck() {
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.ALARM_CLOCK,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(23, false));
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.ALARM_CLOCK,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(29, false));
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.ALARM_CLOCK,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(30, true));
    }

    @Test
    public void resolveAlarmMethod_api31And32_guardedByExactAlarmPermission() {
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.ALARM_CLOCK,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(31, true));
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.ALARM_CLOCK,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(32, true));
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.EXACT_AND_ALLOW_WHILE_IDLE,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(31, false));
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.EXACT_AND_ALLOW_WHILE_IDLE,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(32, false));
    }

    @Test
    public void resolveAlarmMethod_api33AndAbove_alarmClockPermittedByUseExactAlarm() {
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.ALARM_CLOCK,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(33, false));
        assertEquals(MedicineReminderAlarmScheduler.ExactAlarmMethod.ALARM_CLOCK,
                MedicineReminderAlarmScheduler.resolveAlarmMethod(34, false));
    }
}
