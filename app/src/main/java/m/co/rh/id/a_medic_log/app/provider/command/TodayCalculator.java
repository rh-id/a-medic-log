package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Computes today's dose status per profile purely from the given data,
 * no Android dependency. Only medicines linked to a profile (via the
 * note's profileId) are considered. Expected doses for today are derived
 * from enabled reminders (day-of-week + time-of-day anchored at
 * startDateTime); unlike the report calculator, upcoming doses of today
 * are included. A dose counts as TAKEN via greedy nearest-time matching
 * of the profile's intakes from today, otherwise it is missed (when its
 * time has passed) or still upcoming.
 */
public final class TodayCalculator {

    /**
     * Sorts MISSED first (higher missedCount first), then IN_PROGRESS,
     * then DONE; name ascending within the same status.
     */
    private static final Comparator<TodayStatus> STATUS_ORDER = (status1, status2) -> {
        int byStatus = Integer.compare(status1.status, status2.status);
        if (byStatus != 0) {
            return byStatus;
        }
        int byMissed = Integer.compare(status2.missedCount, status1.missedCount);
        if (byMissed != 0) {
            return byMissed;
        }
        String name1 = status1.profileName == null ? "" : status1.profileName;
        String name2 = status2.profileName == null ? "" : status2.profileName;
        return String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
    };

    private TodayCalculator() {
    }

    public static List<TodayStatus> compute(Date now, List<MedicineIntake> intakesToday,
                                            List<MedicineReminder> enabledReminders,
                                            Map<Long, Medicine> medicineMap,
                                            Map<Long, Note> noteMap,
                                            List<Profile> profiles) {
        List<TodayStatus> statuses = new ArrayList<>();
        if (now == null || medicineMap == null || noteMap == null || profiles == null) {
            return statuses;
        }

        // occurrences grouped by profileId, unordered here (sorted when matched)
        Map<Long, List<Occurrence>> occurrenceMap = new HashMap<>();
        if (enabledReminders != null) {
            for (MedicineReminder reminder : enabledReminders) {
                collectTodayOccurrences(reminder, now, medicineMap, noteMap, occurrenceMap);
            }
        }

        // intakes from today grouped by profileId
        Map<Long, List<Date>> intakeMap = new HashMap<>();
        if (intakesToday != null) {
            for (MedicineIntake intake : intakesToday) {
                if (intake == null || intake.medicineId == null || intake.takenDateTime == null) {
                    continue;
                }
                Long profileId = profileIdOf(intake.medicineId, medicineMap, noteMap);
                if (profileId == null) {
                    continue;
                }
                intakeMap
                        .computeIfAbsent(profileId, k -> new ArrayList<>())
                        .add(intake.takenDateTime);
            }
        }

        for (Profile profile : profiles) {
            if (profile == null || profile.id == null) {
                continue;
            }
            List<Occurrence> occurrences = occurrenceMap.get(profile.id);
            if (occurrences == null || occurrences.isEmpty()) {
                // nothing scheduled today: the profile is omitted entirely
                continue;
            }
            statuses.add(buildStatus(profile, occurrences,
                    intakeMap.getOrDefault(profile.id, Collections.emptyList()),
                    medicineMap, now));
        }
        statuses.sort(STATUS_ORDER);
        return statuses;
    }

    private static void collectTodayOccurrences(MedicineReminder reminder, Date now,
                                                Map<Long, Medicine> medicineMap,
                                                Map<Long, Note> noteMap,
                                                Map<Long, List<Occurrence>> occurrenceMap) {
        if (reminder == null || reminder.medicineId == null
                || reminder.startDateTime == null || reminder.reminderDays == null
                || reminder.reminderDays.isEmpty()) {
            return;
        }
        Long profileId = profileIdOf(reminder.medicineId, medicineMap, noteMap);
        if (profileId == null) {
            return;
        }
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTime(reminder.startDateTime);
        int hourOfDay = startCalendar.get(Calendar.HOUR_OF_DAY);
        int minute = startCalendar.get(Calendar.MINUTE);
        int second = startCalendar.get(Calendar.SECOND);

        Calendar occurrenceCalendar = Calendar.getInstance();
        occurrenceCalendar.setTime(now);
        int dayOfWeek = occurrenceCalendar.get(Calendar.DAY_OF_WEEK);
        if (!reminder.reminderDays.contains(dayOfWeek)) {
            return;
        }
        occurrenceCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        occurrenceCalendar.set(Calendar.MINUTE, minute);
        occurrenceCalendar.set(Calendar.SECOND, second);
        occurrenceCalendar.set(Calendar.MILLISECOND, 0);
        Date occurrence = occurrenceCalendar.getTime();
        // occurrence >= startDateTime already covers the reminder's start date,
        // unlike the report calculator the occurrence <= now check is omitted so
        // upcoming doses of today stay included
        if (occurrence.compareTo(reminder.startDateTime) < 0
                || reminder.createdDateTime == null
                || occurrence.compareTo(reminder.createdDateTime) < 0) {
            return;
        }
        occurrenceMap
                .computeIfAbsent(profileId, k -> new ArrayList<>())
                .add(new Occurrence(occurrence, reminder.medicineId));
    }

    /**
     * Greedy nearest-time matching: occurrences and intakes are sorted by time
     * ascending, each occurrence pairs with the unconsumed intake having the
     * minimum absolute time difference.
     */
    private static TodayStatus buildStatus(Profile profile, List<Occurrence> occurrences,
                                           List<Date> intakes, Map<Long, Medicine> medicineMap,
                                           Date now) {
        TodayStatus status = new TodayStatus();
        status.profileId = profile.id;
        status.profileName = profile.name;
        status.totalDoses = occurrences.size();

        List<Occurrence> sortedOccurrences = new ArrayList<>(occurrences);
        List<Date> sortedIntakes = new ArrayList<>(intakes);
        sortedOccurrences.sort(Comparator.naturalOrder());
        sortedIntakes.sort(Comparator.naturalOrder());
        boolean[] consumed = new boolean[sortedIntakes.size()];
        List<Occurrence> unmatched = new ArrayList<>();
        for (Occurrence occurrence : sortedOccurrences) {
            int nearestIdx = -1;
            long nearestDiff = Long.MAX_VALUE;
            for (int i = 0; i < sortedIntakes.size(); i++) {
                if (consumed[i]) {
                    continue;
                }
                long diff = Math.abs(occurrence.time.getTime()
                        - sortedIntakes.get(i).getTime());
                if (diff < nearestDiff) {
                    nearestDiff = diff;
                    nearestIdx = i;
                }
            }
            if (nearestIdx != -1) {
                consumed[nearestIdx] = true;
                status.takenDoses++;
            } else {
                unmatched.add(occurrence);
            }
        }

        Occurrence missed = null;
        Occurrence nextDose = null;
        for (Occurrence occurrence : unmatched) {
            if (occurrence.time.compareTo(now) <= 0) {
                status.missedCount++;
                if (missed == null) {
                    // sorted ascending, so the first hit is the earliest missed
                    missed = occurrence;
                }
            } else if (nextDose == null) {
                nextDose = occurrence;
            }
        }
        if (status.missedCount > 0) {
            status.status = TodayStatus.STATUS_MISSED;
            Medicine medicine = medicineMap.get(missed.medicineId);
            status.missedMedicineName = medicine == null ? null : medicine.name;
            status.missedDoseTime = missed.time;
        } else if (nextDose != null) {
            status.status = TodayStatus.STATUS_IN_PROGRESS;
            status.nextDoseTime = nextDose.time;
        } else {
            status.status = TodayStatus.STATUS_DONE;
        }
        return status;
    }

    private static Long profileIdOf(Long medicineId, Map<Long, Medicine> medicineMap,
                                    Map<Long, Note> noteMap) {
        Medicine medicine = medicineMap.get(medicineId);
        if (medicine == null || medicine.noteId == null) {
            return null;
        }
        Note note = noteMap.get(medicine.noteId);
        if (note == null || note.profileId == null) {
            return null;
        }
        return note.profileId;
    }

    private static class Occurrence implements Comparable<Occurrence> {
        private final Date time;
        private final Long medicineId;

        private Occurrence(Date time, Long medicineId) {
            this.time = time;
            this.medicineId = medicineId;
        }

        @Override
        public int compareTo(Occurrence other) {
            return time.compareTo(other.time);
        }
    }
}
