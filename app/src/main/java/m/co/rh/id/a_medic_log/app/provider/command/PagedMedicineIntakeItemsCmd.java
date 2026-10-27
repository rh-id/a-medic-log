package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.repository.MedicineRepository;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class PagedMedicineIntakeItemsCmd {
    private static final String TAG = PagedMedicineIntakeItemsCmd.class.getName();
    private ExecutorService mExecutorService;
    private ProviderValue<MedicineRepository> mMedicineRepository;
    private ILogger mLogger;
    private Long mMedicineId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<MedicineIntake>> mItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedMedicineIntakeItemsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mMedicineRepository = provider.lazyGet(MedicineRepository.class);
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
        resetPage();
        mExecutorService.execute(() -> {
            if (!isSearching()) {
                load();
            } else {
                executeSearch();
            }
        });
    }

    private void executeSearch() {
        mIsLoadingSubject.onNext(true);
        try {
            mItemsSubject.onNext(
                    mMedicineRepository.get().searchMedicineIntakes(mMedicineId, mSearch, mLimit));
        } catch (Throwable throwable) {
            mLogger.e(TAG, throwable.getMessage(), throwable);
            mItemsSubject.onNext(new ArrayList<>());
        } finally {
            mIsLoadingSubject.onNext(false);
        }
    }

    public void loadNextPage() {
        if (getAllItems().size() < mLimit) {
            return;
        }
        mLimit += mLimit;
        if (isSearching()) {
            mExecutorService.execute(this::executeSearch);
        } else {
            load();
        }
    }

    public void refresh() {
        if (isSearching()) {
            // re-run the current search keeping the current mLimit
            mExecutorService.execute(this::executeSearch);
        } else {
            load();
        }
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
        return mMedicineRepository.get().findMedicineIntakes(mMedicineId, mLimit);
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
