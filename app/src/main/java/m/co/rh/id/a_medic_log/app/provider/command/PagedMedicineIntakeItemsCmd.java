package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class PagedMedicineIntakeItemsCmd {
    private static final String TAG = PagedMedicineIntakeItemsCmd.class.getName();
    private ExecutorService mExecutorService;
    private MedicineDao mMedicineDao;
    private ILogger mLogger;
    private Long mMedicineId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<MedicineIntake>> mItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedMedicineIntakeItemsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mMedicineDao = provider.get(MedicineDao.class);
        mLogger = provider.get(ILogger.class);
        mItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        resetPage();
    }

    private boolean isSearching() {
        return mSearch != null && !mSearch.isEmpty();
    }

    public void search(String search) {
        mSearch = search;
        mExecutorService.execute(() -> {
            if (!isSearching()) {
                load();
            } else {
                mIsLoadingSubject.onNext(true);
                try {
                    List<MedicineIntake> dbList = mMedicineDao.searchMedicineIntakeDescription(mSearch);
                    mItemsSubject.onNext(new ArrayList<>(dbList));
                } catch (Throwable throwable) {
                    mLogger.e(TAG, throwable.getMessage(), throwable);
                    mItemsSubject.onNext(new ArrayList<>());
                } finally {
                    mIsLoadingSubject.onNext(false);
                }
            }
        });
    }

    public void loadNextPage() {
        // no pagination for search
        if (isSearching()) return;
        if (getAllItems().size() < mLimit) {
            return;
        }
        mLimit += mLimit;
        load();
    }

    public void refresh() {
        if (isSearching()) {
            doSearch();
        } else {
            load();
        }
    }

    private void doSearch() {
        search(mSearch);
    }

    private void load() {
        mExecutorService.execute(() -> {
            mIsLoadingSubject.onNext(true);
            try {
                mItemsSubject.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mLogger.e(TAG, throwable.getMessage(), throwable);
                mItemsSubject.onNext(new ArrayList<>());
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private ArrayList<MedicineIntake> loadItems() {
        List<MedicineIntake> dbList;
        if (mMedicineId == null) {
            dbList = mMedicineDao.findMedicineIntakesByWithLimit(mLimit);
        } else {
            dbList = mMedicineDao.findMedicineIntakesByMedicineIdWithLimit(mMedicineId, mLimit);
        }
        return new ArrayList<>(dbList);
    }

    public ArrayList<MedicineIntake> getAllItems() {
        return mItemsSubject.getValue();
    }

    public Flowable<ArrayList<MedicineIntake>> getItemsFlow() {
        return Flowable.fromObservable(mItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    public void setMedicineId(long medicineId) {
        mMedicineId = medicineId;
    }

    private void resetPage() {
        mLimit = 20;
    }
}
