package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Computes the adherence report purely from the given data, no Android dependency.
 * The report is scoped to a single profile: only medicines passed in the
 * medicineMap belong to it (the command pre-scopes via its profile-scoped
 * queries, the calculator skips any occurrence/intake whose medicineId is
 * missing from the map).
 * Expected doses come from the pre-generated persistent occurrence snapshot
 * (medicine_dose_occurrence), NOT from live reminders. A dose counts as TAKEN
 * if an intake of the same medicine is logged within
 * ±{@link #WINDOW_MILLIS} of the occurrence's scheduled time (nearest intake
 * wins, matching runs over the whole range per medicine, not day-bucketed, so
 * a 23:00 dose taken at 01:00 the next day counts for the scheduled day),
 * otherwise MISSED. Occurrences scheduled after now are not yet due and are
 * excluded from both taken and missed. Intakes of the same medicine that
 * remained unconsumed after matching inside [rangeStart, rangeEnd] count as
 * EXTRA (attributed to the intake's day) but never affect the percentages.
 */
public final class AdherenceCalculator {

    /**
     * Default matching window: a dose counts TAKEN if an intake of the same
     * medicine is logged within ±3 hours of the scheduled time — a common
     * ±2-3h protocol window in adherence literature; fixed constant, matching
     * Consumer reminder-app practice.
     */
    public static final long WINDOW_MILLIS = 3L * 60 * 60 * 1000;

    private AdherenceCalculator() {
    }

    /**
     * @return start-of-day millis of now - (rangeDays - 1) days
     */
    public static long rangeStart(Date now, int rangeDays) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -(rangeDays - 1));
        return calendar.getTimeInMillis();
    }

    /**
     * @return end-of-day (23:59:59.999) millis of now
     */
    public static long rangeEnd(Date now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    /**
     * @return the same calendar-day at 00:00:00.000 in millis
     */
    private static long startOfDayMillis(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static AdherenceReport calculate(long profileId, int rangeDays, Date now,
                                            List<MedicineDoseOccurrence> occurrencesInRange,
                                            List<MedicineIntake> intakesInRangeWidened,
                                            Map<Long, Medicine> medicineMap,
                                            Profile selectedProfile) {
        return calculate(profileId, rangeDays, now, occurrencesInRange,
                intakesInRangeWidened, medicineMap, selectedProfile, WINDOW_MILLIS);
    }

    /**
     * Overload with an explicit matching window for testability
     */
    public static AdherenceReport calculate(long profileId, int rangeDays, Date now,
                                            List<MedicineDoseOccurrence> occurrencesInRange,
                                            List<MedicineIntake> intakesInRangeWidened,
                                            Map<Long, Medicine> medicineMap,
                                            Profile selectedProfile, long windowMillis) {
        AdherenceReport report = new AdherenceReport();
        report.rangeDays = rangeDays;

        // only medicines that belong to the selected profile are part of this
        // report, the command pre-scopes and the map membership is re-checked
        // for every occurrence/intake below
        Map<Long, List<MedicineDoseOccurrence>> occurrenceMap = new HashMap<>();
        if (occurrencesInRange != null) {
            for (MedicineDoseOccurrence occurrence : occurrencesInRange) {
                if (occurrence == null || occurrence.scheduledDateTime == null
                        || occurrence.medicineId <= 0
                        || !medicineMap.containsKey(occurrence.medicineId)) {
                    continue;
                }
                occurrenceMap
                        .computeIfAbsent(occurrence.medicineId, k -> new ArrayList<>())
                        .add(occurrence);
            }
        }

        // intakes grouped by medicineId, the fetch was widened by the matching
        // window so boundary intakes can match; grouping by medicine (instead
        // of pooling) keeps a medicine's intake from marking another medicine's
        // dose taken
        Map<Long, List<MedicineIntake>> intakeMap = new HashMap<>();
        if (intakesInRangeWidened != null) {
            for (MedicineIntake intake : intakesInRangeWidened) {
                if (intake == null || intake.medicineId == null || intake.takenDateTime == null
                        || !medicineMap.containsKey(intake.medicineId)) {
                    continue;
                }
                intakeMap
                        .computeIfAbsent(intake.medicineId, k -> new ArrayList<>())
                        .add(intake);
            }
        }

        // ordered oldest -> newest, key is the day's start-of-day millis
        long rangeStartMillis = rangeStart(now, rangeDays);
        long rangeEndMillis = rangeEnd(now);
        Map<Long, AdherenceReport.DailyCount> dailyMap = new LinkedHashMap<>();
        Calendar dayCursor = Calendar.getInstance();
        dayCursor.setTimeInMillis(rangeStartMillis);
        while (dayCursor.getTimeInMillis() <= rangeEndMillis) {
            AdherenceReport.DailyCount dailyCount = new AdherenceReport.DailyCount();
            dailyCount.date = dayCursor.getTime();
            dailyMap.put(dayCursor.getTimeInMillis(), dailyCount);
            dayCursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        Map<Long, AdherenceReport.MedicineAdherence> adherenceMap = new LinkedHashMap<>();
        for (Medicine medicine : medicineMap.values()) {
            if (medicine == null || medicine.id == null) {
                continue;
            }
            List<MedicineDoseOccurrence> occurrences =
                    occurrenceMap.getOrDefault(medicine.id, Collections.emptyList());
            List<MedicineIntake> intakes =
                    intakeMap.getOrDefault(medicine.id, Collections.emptyList());
            if (occurrences.isEmpty() && intakes.isEmpty()) {
                // nothing expected and nothing logged for this medicine
                continue;
            }
            AdherenceReport.MedicineAdherence medicineAdherence = new AdherenceReport.MedicineAdherence();
            medicineAdherence.medicineId = medicine.id;
            medicineAdherence.medicineName = medicine.name;
            medicineAdherence.profileName = selectedProfile == null ? null : selectedProfile.name;
            matchMedicine(occurrences, intakes, now, windowMillis,
                    rangeStartMillis, rangeEndMillis, medicineAdherence, dailyMap);
            if (medicineAdherence.taken == 0 && medicineAdherence.missed == 0
                    && medicineAdherence.extra == 0) {
                // all-zero row: every occurrence is not yet due and nothing was
                // logged, the previous reminder-derived behavior omitted such
                // medicines entirely so the UI keeps showing no empty rows
                continue;
            }
            adherenceMap.put(medicine.id, medicineAdherence);
        }

        report.perMedicine = new ArrayList<>(adherenceMap.values());
        report.daily = new ArrayList<>(dailyMap.values());
        for (AdherenceReport.DailyCount dailyCount : report.daily) {
            report.totalTaken += dailyCount.taken;
            report.totalMissed += dailyCount.missed;
            report.totalExtra += dailyCount.extra;
        }
        report.adherencePercent = percent(report.totalTaken, report.totalMissed);
        return report;
    }

    /**
     * Matches one medicine's occurrences against its own intakes, sorted by
     * time ascending, each not-yet-due occurrence pairs with the unconsumed
     * intake having the minimum absolute time difference; the pair only counts
     * as TAKEN when the difference is within the matching window, otherwise the
     * occurrence is MISSED. Matching is over the whole range (not day-bucketed)
     * so cross-midnight intakes attribute to the occurrence's day. Remaining
     * unconsumed intakes inside [rangeStartMillis, rangeEndMillis] count as
     * EXTRA on the intake's day; the widened fetch bounds never count as extra.
     */
    private static void matchMedicine(List<MedicineDoseOccurrence> occurrences,
                                      List<MedicineIntake> intakes, Date now, long windowMillis,
                                      long rangeStartMillis, long rangeEndMillis,
                                      AdherenceReport.MedicineAdherence medicineAdherence,
                                      Map<Long, AdherenceReport.DailyCount> dailyMap) {
        List<MedicineDoseOccurrence> sortedOccurrences = new ArrayList<>(occurrences);
        sortedOccurrences.sort(Comparator.comparing(o -> o.scheduledDateTime));
        List<MedicineIntake> sortedIntakes = new ArrayList<>(intakes);
        sortedIntakes.sort(Comparator.comparing(o -> o.takenDateTime));
        boolean[] consumed = new boolean[sortedIntakes.size()];
        for (MedicineDoseOccurrence occurrence : sortedOccurrences) {
            // occurrences after now are not yet due, excluded from taken/missed
            if (occurrence.scheduledDateTime.after(now)) {
                continue;
            }
            int nearestIdx = -1;
            long nearestDiff = Long.MAX_VALUE;
            for (int i = 0; i < sortedIntakes.size(); i++) {
                if (consumed[i]) {
                    continue;
                }
                long diff = Math.abs(occurrence.scheduledDateTime.getTime()
                        - sortedIntakes.get(i).takenDateTime.getTime());
                if (diff < nearestDiff) {
                    nearestDiff = diff;
                    nearestIdx = i;
                }
            }
            AdherenceReport.DailyCount dailyCount = dailyMap.get(
                    startOfDayMillis(occurrence.scheduledDateTime));
            if (nearestIdx != -1 && nearestDiff <= windowMillis) {
                consumed[nearestIdx] = true;
                medicineAdherence.taken++;
                if (dailyCount != null) {
                    dailyCount.taken++;
                }
            } else {
                medicineAdherence.missed++;
                if (dailyCount != null) {
                    dailyCount.missed++;
                }
            }
        }
        // every intake of this medicine that no occurrence consumed and that
        // lies inside the actual report range is an extra intake (e.g. PRN),
        // attributed to the intake's own calendar day
        for (int i = 0; i < sortedIntakes.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            Date takenDateTime = sortedIntakes.get(i).takenDateTime;
            long takenMillis = takenDateTime.getTime();
            if (takenMillis < rangeStartMillis || takenMillis > rangeEndMillis) {
                // present only because the fetch was widened by the window
                continue;
            }
            medicineAdherence.extra++;
            AdherenceReport.DailyCount dailyCount = dailyMap.get(startOfDayMillis(takenDateTime));
            if (dailyCount != null) {
                dailyCount.extra++;
            }
        }
        medicineAdherence.adherencePercent =
                percent(medicineAdherence.taken, medicineAdherence.missed);
    }

    private static int percent(int taken, int missed) {
        return (int) Math.round(taken * 100.0 / Math.max(1, taken + missed));
    }
}
