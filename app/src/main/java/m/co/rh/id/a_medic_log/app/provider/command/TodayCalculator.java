package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineDoseOccurrence;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.Profile;

/**
 * Computes today's dose status per profile purely from the given data,
 * no Android dependency. Only medicines linked to a profile (via the
 * note's profileId) are considered. Expected doses come from the
 * pre-generated persistent occurrence snapshot (medicine_dose_occurrence),
 * NOT from live reminders; unlike the report calculator, upcoming doses of
 * today are included. Matching is per medicine: a dose counts as TAKEN only
 * via an intake of the SAME medicine within
 * ±{@link AdherenceCalculator#WINDOW_MILLIS} (nearest-intake matching),
 * otherwise it is missed once its take-window has fully elapsed (an untaken
 * dose whose time has passed but whose window is still open is due yet still
 * takeable, so the card counts it in-progress rather than missed) or it is
 * still upcoming.
 * The occurrence table is truncated on disable, so reminders disabled
 * mid-day produce no upcoming doses and PRN-only profiles (no occurrences)
 * remain omitted from the cards.
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

    public static List<TodayStatus> compute(Date now,
                                            List<MedicineDoseOccurrence> occurrencesToday,
                                            List<MedicineIntake> intakesToday,
                                            Map<Long, Medicine> medicineMap,
                                            Map<Long, Note> noteMap,
                                            List<Profile> profiles) {
        List<TodayStatus> statuses = new ArrayList<>();
        if (now == null || medicineMap == null || noteMap == null || profiles == null) {
            return statuses;
        }

        // occurrences grouped by profileId, unordered here (sorted when matched)
        Map<Long, List<Occurrence>> occurrenceMap = new HashMap<>();
        if (occurrencesToday != null) {
            for (MedicineDoseOccurrence doseOccurrence : occurrencesToday) {
                if (doseOccurrence == null || doseOccurrence.scheduledDateTime == null
                        || doseOccurrence.medicineId <= 0) {
                    continue;
                }
                Long profileId = profileIdOf(doseOccurrence.medicineId, medicineMap, noteMap);
                if (profileId == null) {
                    continue;
                }
                occurrenceMap
                        .computeIfAbsent(profileId, k -> new ArrayList<>())
                        .add(new Occurrence(doseOccurrence.scheduledDateTime,
                                doseOccurrence.medicineId));
            }
        }

        // intakes grouped by medicineId: intakes must only mark doses of the
        // SAME medicine, pooling them per profile would let one medicine's
        // intake mark another medicine's dose taken
        Map<Long, List<Date>> intakeMap = new HashMap<>();
        if (intakesToday != null) {
            for (MedicineIntake intake : intakesToday) {
                if (intake == null || intake.medicineId == null || intake.takenDateTime == null) {
                    continue;
                }
                intakeMap
                        .computeIfAbsent(intake.medicineId, k -> new ArrayList<>())
                        .add(intake.takenDateTime);
            }
        }

        for (Profile profile : profiles) {
            if (profile == null || profile.id == null) {
                continue;
            }
            List<Occurrence> occurrences = occurrenceMap.get(profile.id);
            if (occurrences == null || occurrences.isEmpty()) {
                // nothing scheduled today (e.g. PRN-only medicine): the profile
                // is omitted entirely, unchanged behavior
                continue;
            }
            statuses.add(buildStatus(profile, occurrences, intakeMap, medicineMap, now));
        }
        statuses.sort(STATUS_ORDER);
        return statuses;
    }

    /**
     * Per-medicine matching: the profile's occurrences are grouped by
     * medicineId and each group is matched only against that medicine's
     * intakes within ±{@link AdherenceCalculator#WINDOW_MILLIS}
     */
    private static TodayStatus buildStatus(Profile profile, List<Occurrence> occurrences,
                                           Map<Long, List<Date>> intakesByMedicineId,
                                           Map<Long, Medicine> medicineMap, Date now) {
        TodayStatus status = new TodayStatus();
        status.profileId = profile.id;
        status.profileName = profile.name;
        status.totalDoses = occurrences.size();

        Map<Long, List<Occurrence>> occurrencesByMedicineId = new HashMap<>();
        for (Occurrence occurrence : occurrences) {
            occurrencesByMedicineId
                    .computeIfAbsent(occurrence.medicineId, k -> new ArrayList<>())
                    .add(occurrence);
        }

        List<Occurrence> unmatched = new ArrayList<>();
        for (Map.Entry<Long, List<Occurrence>> medicineEntry : occurrencesByMedicineId.entrySet()) {
            matchMedicineOccurrences(medicineEntry.getValue(),
                    intakesByMedicineId.get(medicineEntry.getKey()),
                    status, unmatched);
        }
        // the per-medicine groups came from a HashMap, so their concatenation
        // is unordered: sort the merged list or the earliest-missed/next-dose
        // scan below could pick a non-earliest dose
        unmatched.sort(Comparator.naturalOrder());

        Occurrence missed = null;
        Occurrence nextDose = null;
        boolean hasActiveDose = false;
        for (Occurrence occurrence : unmatched) {
            if (occurrence.time.getTime() + AdherenceCalculator.WINDOW_MILLIS <= now.getTime()) {
                status.missedCount++;
                if (missed == null) {
                    // unmatched is sorted ascending (see above), so the first
                    // hit is the earliest missed
                    missed = occurrence;
                }
            } else if (occurrence.time.compareTo(now) > 0) {
                if (nextDose == null) {
                    nextDose = occurrence;
                }
            } else {
                // due (time <= now) but its take-window is still open: not
                // missed yet and not upcoming, just still takeable
                hasActiveDose = true;
            }
        }
        if (status.missedCount > 0) {
            status.status = TodayStatus.STATUS_MISSED;
            Medicine medicine = medicineMap.get(missed.medicineId);
            status.missedMedicineName = medicine == null ? null : medicine.name;
            status.missedDoseTime = missed.time;
        } else if (nextDose != null || hasActiveDose) {
            status.status = TodayStatus.STATUS_IN_PROGRESS;
            if (nextDose != null) {
                status.nextDoseTime = nextDose.time;
            }
        } else {
            status.status = TodayStatus.STATUS_DONE;
        }
        return status;
    }

    /**
     * Greedy nearest-time matching within ±WINDOW_MILLIS: occurrences and
     * intakes are sorted by time ascending, each occurrence pairs with the
     * unconsumed intake having the minimum absolute time difference; pairs
     * further apart than the window leave the occurrence unmatched
     */
    private static void matchMedicineOccurrences(List<Occurrence> occurrences, List<Date> intakes,
                                                 TodayStatus status, List<Occurrence> unmatchedOut) {
        List<Occurrence> sortedOccurrences = new ArrayList<>(occurrences);
        List<Date> sortedIntakes = intakes == null
                ? new ArrayList<>() : new ArrayList<>(intakes);
        sortedOccurrences.sort(Comparator.naturalOrder());
        sortedIntakes.sort(Comparator.naturalOrder());
        boolean[] consumed = new boolean[sortedIntakes.size()];
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
            if (nearestIdx != -1 && nearestDiff <= AdherenceCalculator.WINDOW_MILLIS) {
                consumed[nearestIdx] = true;
                status.takenDoses++;
            } else {
                unmatchedOut.add(occurrence);
            }
        }
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
