package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Computes the adherence report purely from the given data, no Android dependency.
 * The report is scoped to a single profile: only medicines linked to that profile
 * (via the note's profileId) are considered.
 * Expected doses are derived from enabled reminders (day-of-week + time-of-day
 * anchored at startDateTime); a dose counts as TAKEN if the medicine has an intake
 * logged on that occurrence's calendar day (greedy nearest-time matching),
 * otherwise MISSED.
 */
public final class AdherenceCalculator {

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
                                            List<MedicineIntake> intakesInRange,
                                            List<MedicineReminder> enabledReminders,
                                            Map<Long, Medicine> medicineMap,
                                            Map<Long, Note> noteMap,
                                            Map<Long, Profile> profileMap) {
        AdherenceReport report = new AdherenceReport();
        report.rangeDays = rangeDays;

        // only medicines that belong to the selected profile (via the note's
        // profileId) are part of this report
        Set<Long> profileMedicineIds = new HashSet<>();
        for (Medicine medicine : medicineMap.values()) {
            if (medicine == null || medicine.id == null || medicine.noteId == null) {
                continue;
            }
            Note note = noteMap.get(medicine.noteId);
            if (note != null && note.profileId != null && note.profileId == profileId) {
                profileMedicineIds.add(medicine.id);
            }
        }
        Profile selectedProfile = profileMap.get(profileId);

        // ordered oldest -> newest, key is the day's start-of-day millis
        Map<Long, AdherenceReport.DailyCount> dailyMap = new LinkedHashMap<>();
        Calendar dayCursor = Calendar.getInstance();
        dayCursor.setTimeInMillis(rangeStart(now, rangeDays));
        long endMillis = rangeEnd(now);
        while (dayCursor.getTimeInMillis() <= endMillis) {
            AdherenceReport.DailyCount dailyCount = new AdherenceReport.DailyCount();
            dailyCount.date = dayCursor.getTime();
            dailyMap.put(dayCursor.getTimeInMillis(), dailyCount);
            dayCursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        // occurrences grouped by medicineId then calendar-day of the occurrence
        Map<Long, Map<Long, List<Date>>> occurrenceMap = new HashMap<>();
        if (enabledReminders != null) {
            for (MedicineReminder reminder : enabledReminders) {
                if (reminder == null || reminder.medicineId == null
                        || !profileMedicineIds.contains(reminder.medicineId)) {
                    continue;
                }
                collectOccurrences(reminder, now, dailyMap, occurrenceMap);
            }
        }

        // intakes grouped by medicineId then calendar-day of takenDateTime
        Map<Long, Map<Long, List<Date>>> intakeMap = new HashMap<>();
        if (intakesInRange != null) {
            for (MedicineIntake intake : intakesInRange) {
                if (intake == null || intake.medicineId == null || intake.takenDateTime == null
                        || !profileMedicineIds.contains(intake.medicineId)) {
                    continue;
                }
                intakeMap
                        .computeIfAbsent(intake.medicineId, k -> new HashMap<>())
                        .computeIfAbsent(startOfDayMillis(intake.takenDateTime), k -> new ArrayList<>())
                        .add(intake.takenDateTime);
            }
        }

        Map<Long, AdherenceReport.MedicineAdherence> adherenceMap = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<Long, List<Date>>> medicineEntry : occurrenceMap.entrySet()) {
            Medicine medicine = medicineMap.get(medicineEntry.getKey());
            Note note = medicine == null || medicine.noteId == null
                    ? null : noteMap.get(medicine.noteId);
            // skip reminders whose medicine/note is missing from the maps
            if (medicine == null || note == null) {
                continue;
            }
            AdherenceReport.MedicineAdherence medicineAdherence = adherenceMap
                    .computeIfAbsent(medicineEntry.getKey(), k -> {
                        AdherenceReport.MedicineAdherence created = new AdherenceReport.MedicineAdherence();
                        created.medicineId = k;
                        created.medicineName = medicine.name;
                        created.profileName = selectedProfile == null ? null : selectedProfile.name;
                        return created;
                    });
            for (Map.Entry<Long, List<Date>> dayEntry : medicineEntry.getValue().entrySet()) {
                List<Date> occurrences = dayEntry.getValue();
                List<Date> intakes = intakeMap
                        .getOrDefault(medicineEntry.getKey(), Collections.emptyMap())
                        .getOrDefault(dayEntry.getKey(), Collections.emptyList());
                matchDay(occurrences, intakes, medicineAdherence,
                        dailyMap.get(dayEntry.getKey()));
            }
        }

        report.perMedicine = new ArrayList<>(adherenceMap.values());
        report.daily = new ArrayList<>(dailyMap.values());
        for (AdherenceReport.DailyCount dailyCount : report.daily) {
            report.totalTaken += dailyCount.taken;
            report.totalMissed += dailyCount.missed;
        }
        report.adherencePercent = percent(report.totalTaken, report.totalMissed);
        return report;
    }

    private static void collectOccurrences(MedicineReminder reminder, Date now,
                                           Map<Long, AdherenceReport.DailyCount> dailyMap,
                                           Map<Long, Map<Long, List<Date>>> occurrenceMap) {
        if (reminder == null || reminder.medicineId == null
                || reminder.startDateTime == null || reminder.reminderDays == null
                || reminder.reminderDays.isEmpty()) {
            return;
        }
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTime(reminder.startDateTime);
        int hourOfDay = startCalendar.get(Calendar.HOUR_OF_DAY);
        int minute = startCalendar.get(Calendar.MINUTE);
        int second = startCalendar.get(Calendar.SECOND);

        Calendar occurrenceCalendar = Calendar.getInstance();
        for (Long dayStartMillis : dailyMap.keySet()) {
            occurrenceCalendar.setTimeInMillis(dayStartMillis);
            int dayOfWeek = occurrenceCalendar.get(Calendar.DAY_OF_WEEK);
            if (!reminder.reminderDays.contains(dayOfWeek)) {
                continue;
            }
            occurrenceCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            occurrenceCalendar.set(Calendar.MINUTE, minute);
            occurrenceCalendar.set(Calendar.SECOND, second);
            occurrenceCalendar.set(Calendar.MILLISECOND, 0);
            Date occurrence = occurrenceCalendar.getTime();
            // occurrence >= startDateTime already covers the reminder's start date,
            // and occurrence <= now excludes future doses of today
            if (occurrence.compareTo(reminder.startDateTime) < 0
                    || occurrence.compareTo(now) > 0
                    || reminder.createdDateTime == null
                    || occurrence.compareTo(reminder.createdDateTime) < 0) {
                continue;
            }
            occurrenceMap
                    .computeIfAbsent(reminder.medicineId, k -> new HashMap<>())
                    .computeIfAbsent(dayStartMillis, k -> new ArrayList<>())
                    .add(occurrence);
        }
    }

    /**
     * Greedy nearest-time matching: occurrences and intakes are sorted by time
     * ascending, each occurrence pairs with the unconsumed intake having the
     * minimum absolute time difference.
     */
    private static void matchDay(List<Date> occurrences, List<Date> intakes,
                                 AdherenceReport.MedicineAdherence medicineAdherence,
                                 AdherenceReport.DailyCount dailyCount) {
        List<Date> sortedOccurrences = new ArrayList<>(occurrences);
        List<Date> sortedIntakes = new ArrayList<>(intakes);
        sortedOccurrences.sort(Comparator.naturalOrder());
        sortedIntakes.sort(Comparator.naturalOrder());
        boolean[] consumed = new boolean[sortedIntakes.size()];
        for (Date occurrence : sortedOccurrences) {
            int nearestIdx = -1;
            long nearestDiff = Long.MAX_VALUE;
            for (int i = 0; i < sortedIntakes.size(); i++) {
                if (consumed[i]) {
                    continue;
                }
                long diff = Math.abs(occurrence.getTime()
                        - sortedIntakes.get(i).getTime());
                if (diff < nearestDiff) {
                    nearestDiff = diff;
                    nearestIdx = i;
                }
            }
            boolean taken = nearestIdx != -1;
            if (taken) {
                consumed[nearestIdx] = true;
                medicineAdherence.taken++;
                dailyCount.taken++;
            } else {
                medicineAdherence.missed++;
                dailyCount.missed++;
            }
        }
        medicineAdherence.adherencePercent =
                percent(medicineAdherence.taken, medicineAdherence.missed);
    }

    private static int percent(int taken, int missed) {
        return (int) Math.round(taken * 100.0 / Math.max(1, taken + missed));
    }
}
