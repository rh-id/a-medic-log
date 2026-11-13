package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.app.model.MedicineRow;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineIntakeDao;
import m.co.rh.id.a_medic_log.base.dao.MedicineReminderDao;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Follows the {@link PagedNoteItemsCmd}/{@link PagedMedicineIntakeItemsCmd}
 * family shape (items and isLoading subjects with search/refresh) but loads
 * ALL medicines of the profile in one query without pagination (per-profile
 * medicine counts are small) and publishes them as aggregated medicine rows.
 * Medicines sharing the same name (case/whitespace insensitive) are grouped
 * under the newest entry, the row carries the group's reminder on/off counts,
 * its last intake and its 7-day adherence figures.
 */
public class PagedMedicinesCmd {
    private static final String TAG = PagedMedicinesCmd.class.getName();
    private static final int ADHERENCE_RANGE_DAYS = 7;
    private ExecutorService mExecutorService;
    private ProviderValue<MedicineDao> mMedicineDao;
    private ProviderValue<MedicineIntakeDao> mMedicineIntakeDao;
    private ProviderValue<MedicineReminderDao> mMedicineReminderDao;
    private ProviderValue<GetAdherenceReportCmd> mGetAdherenceReportCmd;
    private ILogger mLogger;
    private Long mProfileId;
    private String mSearch;
    // unfiltered rows of the last load, the in-memory search filters over them
    private ArrayList<MedicineRow> mAllRows;
    private final BehaviorSubject<ArrayList<MedicineRow>> mItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedMedicinesCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mMedicineIntakeDao = provider.lazyGet(MedicineIntakeDao.class);
        mMedicineReminderDao = provider.lazyGet(MedicineReminderDao.class);
        mGetAdherenceReportCmd = provider.lazyGet(GetAdherenceReportCmd.class);
        mLogger = provider.get(ILogger.class);
        mAllRows = new ArrayList<>();
        mItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
    }

    private boolean isSearching() {
        return mSearch != null && !mSearch.isEmpty();
    }

    public void loadWithProfileId(Long profileId) {
        mProfileId = profileId;
        refresh();
    }

    /**
     * In-memory filter over the loaded rows, an empty string resets the filter
     */
    public void search(String search) {
        mSearch = search;
        mExecutorService.execute(() -> {
            if (!isSearching()) {
                mItemsSubject.onNext(mAllRows);
            } else {
                mItemsSubject.onNext(executeSearch());
            }
        });
    }

    private ArrayList<MedicineRow> executeSearch() {
        String search = mSearch.toLowerCase(Locale.ROOT);
        ArrayList<MedicineRow> filteredRows = new ArrayList<>();
        for (MedicineRow medicineRow : mAllRows) {
            String name = medicineRow.medicine.name;
            if (name != null && name.toLowerCase(Locale.ROOT).contains(search)) {
                filteredRows.add(medicineRow);
            }
        }
        return filteredRows;
    }

    public void refresh() {
        mExecutorService.execute(this::load);
    }

    private void load() {
        mIsLoadingSubject.onNext(true);
        try {
            if (mProfileId == null) {
                mAllRows = new ArrayList<>();
                mItemsSubject.onNext(new ArrayList<>());
                return;
            }
            mAllRows = buildRows(mProfileId);
            if (isSearching()) {
                mItemsSubject.onNext(executeSearch());
            } else {
                mItemsSubject.onNext(mAllRows);
            }
        } catch (Throwable throwable) {
            mLogger.e(TAG, throwable.getMessage(), throwable);
            mAllRows = new ArrayList<>();
            mItemsSubject.onNext(new ArrayList<>());
        } finally {
            mIsLoadingSubject.onNext(false);
        }
    }

    private ArrayList<MedicineRow> buildRows(long profileId) {
        // medicines arrive newest-first, so putIfAbsent keeps the newest
        // entry of each name group as the representative
        Map<String, MedicineRow> rowMap = new LinkedHashMap<>();
        List<Medicine> medicines = mMedicineDao.get().findMedicinesByProfileId(profileId);
        for (Medicine medicine : medicines) {
            if (medicine.name == null) {
                continue;
            }
            String key = medicine.name.trim().toLowerCase(Locale.ROOT);
            MedicineRow medicineRow = rowMap.putIfAbsent(key, newRow(medicine));
            if (medicineRow == null) {
                medicineRow = rowMap.get(key);
            }
            if (medicine.id != null) {
                medicineRow.medicineIds.add(medicine.id);
            }
        }
        applyReminders(rowMap, profileId);
        applyLastIntakes(rowMap, profileId);
        applyAdherence(rowMap, profileId);
        return new ArrayList<>(rowMap.values());
    }

    private static MedicineRow newRow(Medicine medicine) {
        MedicineRow medicineRow = new MedicineRow();
        medicineRow.medicine = medicine;
        medicineRow.medicineIds = new ArrayList<>();
        return medicineRow;
    }

    private void applyReminders(Map<String, MedicineRow> rowMap, long profileId) {
        List<MedicineReminder> medicineReminders =
                mMedicineReminderDao.get().findMedicineRemindersByProfileId(profileId);
        for (MedicineReminder medicineReminder : medicineReminders) {
            MedicineRow medicineRow = findRowByMedicineId(rowMap, medicineReminder.medicineId);
            if (medicineRow == null) {
                continue;
            }
            boolean enabled = medicineReminder.reminderEnabled != null
                    && medicineReminder.reminderEnabled;
            if (enabled) {
                medicineRow.remindersOn++;
            } else {
                medicineRow.remindersOff++;
            }
        }
    }

    private void applyLastIntakes(Map<String, MedicineRow> rowMap, long profileId) {
        List<MedicineIntake> lastIntakes =
                mMedicineIntakeDao.get().findLastIntakesByProfileId(profileId);
        for (MedicineIntake medicineIntake : lastIntakes) {
            MedicineRow medicineRow = findRowByMedicineId(rowMap, medicineIntake.medicineId);
            if (medicineRow == null || medicineIntake.takenDateTime == null) {
                continue;
            }
            // the query returns one intake per medicine, the group takes the
            // newest of them
            Date current = medicineRow.lastIntakeDateTime;
            if (current == null || medicineIntake.takenDateTime.after(current)) {
                medicineRow.lastIntakeDateTime = medicineIntake.takenDateTime;
            }
        }
    }

    private void applyAdherence(Map<String, MedicineRow> rowMap, long profileId) {
        AdherenceReport adherenceReport;
        try {
            // fetched once per load, medicines absent from the report stayed
            // inactive in the window and keep a null (no data) percentage
            adherenceReport = mGetAdherenceReportCmd.get()
                    .execute(profileId, ADHERENCE_RANGE_DAYS).blockingGet();
        } catch (Throwable throwable) {
            mLogger.e(TAG, throwable.getMessage(), throwable);
            return;
        }
        for (AdherenceReport.MedicineAdherence medicineAdherence : adherenceReport.perMedicine) {
            MedicineRow medicineRow = findRowByMedicineId(rowMap, medicineAdherence.medicineId);
            if (medicineRow == null) {
                continue;
            }
            medicineRow.takenCount += medicineAdherence.taken;
            medicineRow.missedCount += medicineAdherence.missed;
            medicineRow.adherencePercent = Math.round(medicineRow.takenCount * 100.0f
                    / Math.max(1, medicineRow.takenCount + medicineRow.missedCount));
        }
    }

    private static MedicineRow findRowByMedicineId(Map<String, MedicineRow> rowMap, Long medicineId) {
        if (medicineId == null) {
            return null;
        }
        for (MedicineRow medicineRow : rowMap.values()) {
            if (medicineRow.medicineIds.contains(medicineId)) {
                return medicineRow;
            }
        }
        return null;
    }

    public ArrayList<MedicineRow> getAllItems() {
        return mItemsSubject.getValue();
    }

    public Flowable<ArrayList<MedicineRow>> getItemsFlow() {
        return Flowable.fromObservable(mItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }
}
