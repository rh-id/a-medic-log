package m.co.rh.id.a_medic_log.base.repository;

import androidx.room.RoomDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_medic_log.base.AppDatabase;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineDoseOccurrenceDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Repository for medicines and their reminders, and the SOLE bookkeeping
 * entry point of medicine_dose_occurrence: every reminder and occurrence
 * write goes through this class (passive FK-cascade deletes excepted, e.g.
 * {@link #deleteMedicineByMedicineId} removes occurrences via the database's
 * ON DELETE CASCADE without bookkeeping).
 * <p>
 * There is no lock. Every occurrence-affecting entry point obeys two rules:
 * <ol>
 * <li>It is EXACTLY ONE {@link RoomDatabase#runInTransaction} block whose
 * decision reads happen inside it. SQLite has a single writer and Room takes
 * the writer lock at BEGIN, so a concurrent transaction on another thread
 * blocks until the first commits, then re-reads fresh state; nested
 * same-thread transactions coalesce into the outermost one. A future entry
 * point that composes two transactions, or that reads its decision state
 * OUTSIDE the transaction, is silently racy — the database only serializes
 * what a single transaction spans.</li>
 * <li>The before-state a decision is based on (enabled transition, latest
 * occurrence) is re-read INSIDE the transaction; a caller-supplied
 * before-state is never trusted.</li>
 * </ol>
 * Insert/update/delete entry points capture their operation time with
 * {@code new Date()} internally; catch-up and backfill take it as a
 * parameter, and every schedule bound in the package-private cores
 * ({@link #generateFor}, {@link #truncateAfter}, {@link #routineCatchUp},
 * {@link #initialBackfill}) is an explicit parameter — no clock reads and no
 * dependency on the app-layer AdherenceCalculator, so fixed-date tests stay
 * deterministic. {@link #stampCreation} is a pure field-mutation helper (no
 * DB access). Covered on real Room by the instrumented
 * MedicineRepositoryTest, including multi-threaded entry-point races.
 */
public class MedicineRepository {

    private final ProviderValue<AppDatabase> mAppDatabase;
    private final ProviderValue<MedicineDao> mMedicineDao;
    private final ProviderValue<MedicineReminderDao> mMedicineReminderDao;
    private final ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;
    private final ProviderValue<MedicineDoseOccurrenceDao> mMedicineDoseOccurrenceDao;

    public MedicineRepository(Provider provider) {
        mAppDatabase = provider.lazyGet(AppDatabase.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
        mMedicineDoseOccurrenceDao = provider.lazyGet(MedicineDoseOccurrenceDao.class);
    }

    /**
     * Test seam, mirrors the lazy provider values with the given instances
     */
    MedicineRepository(AppDatabase appDatabase, MedicineDao medicineDao,
                       MedicineReminderDao medicineReminderDao,
                       MedicineIntakeDao medicineIntakeDao,
                       MedicineDoseOccurrenceDao medicineDoseOccurrenceDao) {
        mAppDatabase = () -> appDatabase;
        mMedicineDao = () -> medicineDao;
        mMedicineReminderDao = () -> medicineReminderDao;
        mMedicineIntakeDao = () -> medicineIntakeDao;
        mMedicineDoseOccurrenceDao = () -> medicineDoseOccurrenceDao;
    }

    /**
     * Loads a fresh medicine state with its reminders; the returned state is never shared
     */
    public MedicineState queryMedicineInfo(long medicineId) {
        MedicineState medicineState = new MedicineState();
        Medicine medicine = mMedicineDao.get().findMedicineById(medicineId);
        List<MedicineReminder> medicineReminders =
                mMedicineReminderDao.get().findMedicineRemindersByMedicineId(medicineId);
        medicineState.updateMedicine(medicine);
        medicineState.updateMedicineReminderList(medicineReminders);
        return medicineState;
    }

    public void insertMedicine(Medicine medicine, List<MedicineReminder> medicineReminders) {
        mAppDatabase.get().runInTransaction(() -> {
            long medsId = mMedicineDao.get().insert(medicine);
            medicine.id = medsId;
            if (medicineReminders != null && !medicineReminders.isEmpty()) {
                for (MedicineReminder medicineReminder : medicineReminders) {
                    medicineReminder.medicineId = medsId;
                    // nested call: the nested transaction coalesces into the
                    // outermost one on the same thread
                    insertMedicineReminder(medicineReminder);
                }
            }
        });
    }

    public void updateMedicine(Medicine medicine, ArrayList<MedicineReminder> medicineReminders) {
        Date now = new Date();
        mAppDatabase.get().runInTransaction(() -> {
            // authoritative before-state for the transition detection, a
            // caller's snapshot may be stale
            MedicineState beforeUpdate = queryMedicineInfo(medicine.id);
            mMedicineDao.get().update(medicine);
            long medsId = medicine.id;
            List<MedicineReminder> newReminders = new ArrayList<>();
            if (medicineReminders != null && !medicineReminders.isEmpty()) {
                for (MedicineReminder medicineReminder : medicineReminders) {
                    medicineReminder.medicineId = medsId;
                    if (medicineReminder.id == null) {
                        // create-time status stamp, these columns are
                        // never cleared once set
                        stampCreation(medicineReminder);
                        newReminders.add(medicineReminder);
                        medicineReminder.id =
                                mMedicineReminderDao.get().insert(medicineReminder);
                        continue;
                    }
                    MedicineReminder beforeReminder =
                            findReminderById(beforeUpdate, medicineReminder.id);
                    boolean wasEnabled = beforeReminder != null
                            && isReminderEnabled(beforeReminder);
                    boolean isEnabled = isReminderEnabled(medicineReminder);
                    // the persisted stamp values are authoritative,
                    // discard anything a stale caller snapshot pre-set
                    if (beforeReminder != null) {
                        medicineReminder.lastEnabledDateTime =
                                beforeReminder.lastEnabledDateTime;
                        medicineReminder.lastDisabledDateTime =
                                beforeReminder.lastDisabledDateTime;
                    }
                    // record the enable/disable transition, these columns
                    // are never cleared once set
                    if (isEnabled && !wasEnabled) {
                        medicineReminder.lastEnabledDateTime = now;
                    } else if (!isEnabled && wasEnabled) {
                        medicineReminder.lastDisabledDateTime = now;
                    }
                    mMedicineReminderDao.get().update(medicineReminder);
                }
            }
            if (medicineReminders != null && !medicineReminders.isEmpty()) {
                // an edit/disable must never leave stale FUTURE occurrences,
                // the history <= now stays immutable; an already-enabled
                // reminder whose schedule fields changed is refreshed by
                // the same truncate + regenerate
                for (MedicineReminder medicineReminder : medicineReminders) {
                    if (medicineReminder.id == null || newReminders.contains(medicineReminder)) {
                        continue;
                    }
                    truncateAfter(medicineReminder.id, now.getTime());
                    if (isReminderEnabled(medicineReminder)) {
                        generateFor(medicineReminder,
                                now.getTime(), endOfTodayMillis(now));
                    }
                }
                // newly inserted reminders record their expected doses of
                // today right away, the generation core skips anything
                // before createdDateTime itself
                for (MedicineReminder newReminder : newReminders) {
                    if (isReminderEnabled(newReminder)) {
                        generateFor(newReminder,
                                newReminder.createdDateTime.getTime(),
                                endOfTodayMillis(now));
                    }
                }
            }
        });
    }

    public void deleteMedicineByMedicineId(long medicineId) {
        mAppDatabase.get().runInTransaction(() -> {
            mMedicineDao.get().deleteMedicineById(medicineId);
        });
    }

    /**
     * Inserts a reminder and records its create-time status stamp plus, when
     * enabled, its expected doses of today. Also called nested by
     * {@link #insertMedicine(Medicine, List)}, safe because nested same-thread
     * Room transactions coalesce into the outermost one.
     */
    public void insertMedicineReminder(MedicineReminder medicineReminder) {
        mAppDatabase.get().runInTransaction(() -> {
            // record the creation transition before the row is written,
            // these columns are never cleared once set
            stampCreation(medicineReminder);
            long id = mMedicineReminderDao.get().insert(medicineReminder);
            medicineReminder.id = id;
            // expected doses of today are recorded right away, the
            // generation core skips anything before createdDateTime itself
            if (isReminderEnabled(medicineReminder)) {
                Date now = new Date();
                generateFor(medicineReminder,
                        medicineReminder.createdDateTime.getTime(),
                        endOfTodayMillis(now));
            }
        });
    }

    /**
     * Updates a reminder and mirrors its bookkeeping: the enable/disable
     * transition is detected against the FRESH row read inside the transaction (a
     * caller-supplied before-state and any stamps pre-set from a stale
     * snapshot are discarded), then the row is written, stale FUTURE
     * occurrences are truncated and, when enabled, today's remaining doses are
     * regenerated from the update time.
     */
    public void updateMedicineReminder(MedicineReminder edited) {
        Date now = new Date();
        mAppDatabase.get().runInTransaction(() -> {
            MedicineReminder before = mMedicineReminderDao.get()
                    .findMedicineReminderById(edited.id);
            if (before == null) {
                // nothing to update (e.g. the row was deleted while
                // editing): silently skipped, parity with the previous
                // raw dao.update which affected 0 rows. The caller's
                // change notifier still fires with the caller-supplied
                // entity, accepted because a notifier event for a row
                // that no longer exists is harmless.
                return;
            }
            boolean wasEnabled = isReminderEnabled(before);
            boolean isEnabled = isReminderEnabled(edited);
            // the persisted stamp values are authoritative, discard
            // anything a stale caller snapshot pre-set on the entity
            edited.lastEnabledDateTime = before.lastEnabledDateTime;
            edited.lastDisabledDateTime = before.lastDisabledDateTime;
            // record the enable/disable transition, these columns are
            // never cleared once set
            if (isEnabled && !wasEnabled) {
                edited.lastEnabledDateTime = now;
            } else if (!isEnabled && wasEnabled) {
                edited.lastDisabledDateTime = now;
            }
            mMedicineReminderDao.get().update(edited);
            // an edit/disable must never leave stale FUTURE occurrences,
            // the history <= now stays immutable
            truncateAfter(edited.id, now.getTime());
            if (isEnabled) {
                generateFor(edited, now.getTime(), endOfTodayMillis(now));
            }
        });
    }

    /**
     * Deletes a reminder and truncates its occurrences scheduled strictly
     * after the (captured-before-the-delete) operation time, rows <= now
     * survive as immutable history: removing a reminder must not change
     * historical adherence.
     */
    public void deleteMedicineReminder(long reminderId) {
        // captured before the delete so the truncation bound can never be
        // a post-delete timestamp
        Date now = new Date();
        mAppDatabase.get().runInTransaction(() -> {
            MedicineReminder medicineReminder = mMedicineReminderDao.get()
                    .findMedicineReminderById(reminderId);
            if (medicineReminder == null) {
                return;
            }
            mMedicineReminderDao.get().delete(medicineReminder);
            // the occurrence rows have no FK to the reminder so they
            // survive the delete; drop the future ones
            truncateAfter(reminderId, now.getTime());
        });
    }

    /**
     * Routine catch-up of every enabled reminder of a profile, generates any
     * occurrences missed since their last known point up to end-of-today of
     * the given now. Used by report commands before they compute.
     */
    public void catchUpEnabledRemindersByProfile(long profileId, Date now) {
        mAppDatabase.get().runInTransaction(() -> {
            List<MedicineReminder> enabledReminders = mMedicineReminderDao.get()
                    .findMedicineRemindersEnabledByProfileId(profileId);
            catchUp(enabledReminders, now);
        });
    }

    /**
     * Routine catch-up of every enabled reminder, generates any occurrences
     * missed since their last known point up to end-of-today of the given now.
     * Used by the today-status command which spans all profiles.
     */
    public void catchUpAllEnabled(Date now) {
        mAppDatabase.get().runInTransaction(() -> {
            List<MedicineReminder> enabledReminders = mMedicineReminderDao.get()
                    .findMedicineRemindersEnabled();
            catchUp(enabledReminders, now);
        });
    }

    /**
     * One-time backfill of every ENABLED reminder's expected doses from its
     * creation to end-of-today of the given now. Disabled reminders are
     * skipped entirely, without a recorded disable time their history is
     * unknowable. The reminder list is re-read INSIDE the transaction (rule 2, a
     * caller-supplied list is never trusted for the enabled/disabled
     * decision). The once-only flag stays with the caller.
     */
    public void backfillOccurrences(Date now) {
        mAppDatabase.get().runInTransaction(() ->
                initialBackfill(mMedicineReminderDao.get().findMedicineRemindersAll(),
                        endOfTodayMillis(now)));
    }

    private void catchUp(List<MedicineReminder> enabledReminders, Date now) {
        long ceiling = endOfTodayMillis(now);
        for (MedicineReminder reminder : enabledReminders) {
            routineCatchUp(reminder, ceiling);
        }
    }
    
    /**
     * Generates occurrences of this reminder with
     * lowerBoundMillis &lt; scheduledDateTime &lt;= upperBoundMillis,
     * on the days in reminder.reminderDays, at the time-of-day
     * (HOUR/MINUTE/SECOND) taken from reminder.startDateTime.
     * Skips occurrences scheduled before reminder.startDateTime and
     * before reminder.createdDateTime. Inserts with IGNORE, making repeated
     * calls idempotent.
     * Day stepping uses Calendar (never millis arithmetic) so daylight saving
     * transitions keep the local time-of-day.
     *
     * @return the number of occurrences actually inserted
     */
    int generateFor(MedicineReminder reminder, long lowerBoundMillis, long upperBoundMillis) {
        if (reminder == null || reminder.id == null || reminder.medicineId == null
                || reminder.startDateTime == null || reminder.createdDateTime == null
                || reminder.reminderDays == null || reminder.reminderDays.isEmpty()) {
            return 0;
        }
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTime(reminder.startDateTime);
        int hourOfDay = startCalendar.get(Calendar.HOUR_OF_DAY);
        int minute = startCalendar.get(Calendar.MINUTE);
        int second = startCalendar.get(Calendar.SECOND);

        // day cursor always holds a start-of-day (00:00:00.000) time so
        // stepping one day at a time is DST safe
        Calendar dayCursor = Calendar.getInstance();
        dayCursor.setTimeInMillis(lowerBoundMillis);
        dayCursor.set(Calendar.HOUR_OF_DAY, 0);
        dayCursor.set(Calendar.MINUTE, 0);
        dayCursor.set(Calendar.SECOND, 0);
        dayCursor.set(Calendar.MILLISECOND, 0);

        Calendar occurrenceCalendar = Calendar.getInstance();
        List<MedicineDoseOccurrence> occurrences = new ArrayList<>();
        while (dayCursor.getTimeInMillis() <= upperBoundMillis) {
            int dayOfWeek = dayCursor.get(Calendar.DAY_OF_WEEK);
            if (reminder.reminderDays.contains(dayOfWeek)) {
                occurrenceCalendar.setTimeInMillis(dayCursor.getTimeInMillis());
                occurrenceCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                occurrenceCalendar.set(Calendar.MINUTE, minute);
                occurrenceCalendar.set(Calendar.SECOND, second);
                occurrenceCalendar.set(Calendar.MILLISECOND, 0);
                long scheduledMillis = occurrenceCalendar.getTimeInMillis();
                if (scheduledMillis > lowerBoundMillis && scheduledMillis <= upperBoundMillis
                        && scheduledMillis >= reminder.startDateTime.getTime()
                        && scheduledMillis >= reminder.createdDateTime.getTime()) {
                    MedicineDoseOccurrence occurrence = new MedicineDoseOccurrence();
                    occurrence.reminderId = reminder.id;
                    occurrence.medicineId = reminder.medicineId;
                    occurrence.scheduledDateTime = occurrenceCalendar.getTime();
                    // row metadata only (when this expected dose was booked),
                    // never a schedule decision bound, so it does not affect
                    // the determinism of the fixed-date core tests
                    occurrence.createdDateTime = new Date();
                    occurrences.add(occurrence);
                }
            }
            dayCursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        if (occurrences.isEmpty()) {
            return 0;
        }
        // IGNORE: rows that already exist come back as -1 and are not counted
        List<Long> rowIds = mMedicineDoseOccurrenceDao.get().insertAll(occurrences);
        int inserted = 0;
        for (Long rowId : rowIds) {
            if (rowId != null && rowId != -1) {
                inserted++;
            }
        }
        return inserted;
    }

    /**
     * Deletes occurrences scheduled strictly after timeMillis for this reminder,
     * used on edit/disable/delete so stale FUTURE occurrences never survive
     * while the history &lt;= timeMillis stays immutable.
     */
    void truncateAfter(long reminderId, long timeMillis) {
        mMedicineDoseOccurrenceDao.get()
                .deleteByReminderIdAndScheduledDateTimeAfter(reminderId, timeMillis);
    }

    /**
     * Generates missed occurrences since the last known point for this
     * reminder, used as a routine catch-up (e.g. before a report is computed).
     * The floor is the latest of the last recorded occurrence,
     * lastEnabledDateTime, lastDisabledDateTime and createdDateTime, which
     * guarantees a disable -&gt; re-enable off-period is never regenerated
     * (phantom-miss prevention) and an edited past is never regenerated.
     * A disabled reminder is a no-op.
     *
     * @param ceilingMillis upper generation bound (end-of-today of the
     *                      operation time), passed in by the entry point
     * @return the number of occurrences actually inserted
     */
    int routineCatchUp(MedicineReminder reminder, long ceilingMillis) {
        if (reminder == null || !isReminderEnabled(reminder)
                || reminder.id == null || reminder.createdDateTime == null) {
            return 0;
        }
        long floor = reminder.createdDateTime.getTime();
        MedicineDoseOccurrence latest = mMedicineDoseOccurrenceDao.get()
                .findLatestMedicineDoseOccurrenceByReminderId(reminder.id);
        if (latest != null && latest.scheduledDateTime != null) {
            floor = Math.max(floor, latest.scheduledDateTime.getTime());
        }
        if (reminder.lastEnabledDateTime != null) {
            floor = Math.max(floor, reminder.lastEnabledDateTime.getTime());
        }
        if (reminder.lastDisabledDateTime != null) {
            floor = Math.max(floor, reminder.lastDisabledDateTime.getTime());
        }
        return generateFor(reminder, floor, ceilingMillis);
    }

    /**
     * One-time backfill for reminders that existed before occurrence tracking,
     * for ENABLED reminders only. Disabled reminders are skipped entirely:
     * without a recorded disable time their history is unknowable and a
     * recorded off-period must never produce expected doses.
     *
     * @param ceilingMillis upper generation bound (end-of-today of the
     *                      operation time), passed in by the entry point
     * @return the number of occurrences actually inserted
     */
    int initialBackfill(List<MedicineReminder> reminders, long ceilingMillis) {
        if (reminders == null || reminders.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (MedicineReminder reminder : reminders) {
            if (reminder == null || !isReminderEnabled(reminder)
                    || reminder.startDateTime == null || reminder.createdDateTime == null) {
                continue;
            }
            // generateFor's own skip rules handle the start/created bounds
            long lowerBound = Math.max(reminder.createdDateTime.getTime(),
                    reminder.startDateTime.getTime());
            inserted += generateFor(reminder, lowerBound, ceilingMillis);
        }
        return inserted;
    }

    /**
     * Records the create-time enable/disable status of a reminder before its
     * row is written: enabled -&gt; lastEnabledDateTime = createdDateTime,
     * disabled -&gt; lastDisabledDateTime = createdDateTime. These columns are
     * never cleared once set, they are the occurrence generation floor.
     * Pure field mutation on the not-yet-inserted entity: no DB access.
     * Public static as the single source of create-stamping, shared with
     * raw-insert call sites outside this class so they cannot silently
     * diverge.
     */
    public static void stampCreation(MedicineReminder medicineReminder) {
        if (isReminderEnabled(medicineReminder)) {
            medicineReminder.lastEnabledDateTime = medicineReminder.createdDateTime;
        } else {
            medicineReminder.lastDisabledDateTime = medicineReminder.createdDateTime;
        }
    }

    /**
     * Counts the records owned by the given medicine; the reminder count
     * comes from the medicine state, intakes are not part of the state
     * (paged history) so they are counted via a DAO query
     * <p>
     * The state is passed by callers that have just read it, so the
     * reminder count stays consistent with what the UI already shows;
     * unlike queryNoteCounts/queryProfileCounts this takes a state instead
     * of an id because the matched reminders are already in it
     */
    public MedicineCounts queryMedicineCounts(MedicineState medicineState) {
        return new MedicineCounts(
                medicineState.getMedicineReminderList().size(),
                mMedicineIntakeDao.get().countMedicineIntakesByMedicineId(medicineState.getMedicineId()));
    }

    private static boolean isReminderEnabled(MedicineReminder medicineReminder) {
        return medicineReminder.reminderEnabled != null && medicineReminder.reminderEnabled;
    }

    private static MedicineReminder findReminderById(MedicineState medicineState, Long reminderId) {
        if (medicineState == null) {
            return null;
        }
        for (MedicineReminder medicineReminder : medicineState.getMedicineReminderList()) {
            if (medicineReminder.id != null && medicineReminder.id.equals(reminderId)) {
                return medicineReminder;
            }
        }
        return null;
    }

    /**
     * @return end-of-day (23:59:59.999) millis of now, the generation ceiling
     * (the occurrence table is maintained through end-of-today); mirrors the
     * report range end so catch-up never falls short of today's report
     */
    private static long endOfTodayMillis(Date now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    /**
     * Counts of the records owned by a medicine
     */
    public static class MedicineCounts {
        public final int reminderCount;
        public final int intakeCount;

        public MedicineCounts(int reminderCount, int intakeCount) {
            this.reminderCount = reminderCount;
            this.intakeCount = intakeCount;
        }
    }
}
