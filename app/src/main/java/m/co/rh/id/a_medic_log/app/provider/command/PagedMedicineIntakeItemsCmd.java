package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.dao.MedicineDao;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class PagedMedicineIntakeItemsCmd {
    private Context mAppContext;
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<MedicineDao> mMedicineDao;
    private Long mMedicineId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<MedicineIntake>> mItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedMedicineIntakeItemsCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mMedicineDao = provider.lazyGet(MedicineDao.class);
        mItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        resetPage();
    }

    private boolean isSearching() {
        return mSearch != null && !mSearch.isEmpty();
    }

    public void search(String search) {
        mSearch = search;
        mExecutorService.get().execute(() -> {
            if (!isSearching()) {
                load();
            } else {
                mIsLoadingSubject.onNext(true);
                try {
                    List<MedicineIntake> dbList = mMedicineDao.get().searchMedicineIntakeDescription(mSearch);
                    mItemsSubject.onNext(new ArrayList<>(dbList));
                } catch (Throwable throwable) {
                    mItemsSubject.onError(throwable);
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
        mExecutorService.get().execute(() -> {
            mIsLoadingSubject.onNext(true);
            try {
                mItemsSubject.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mItemsSubject.onError(throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private ArrayList<MedicineIntake> loadItems() {
        List<MedicineIntake> dbList;
        if (mMedicineId == null) {
            dbList = mMedicineDao.get().findMedicineIntakesByWithLimit(mLimit);
        } else {
            dbList = mMedicineDao.get().findMedicineIntakesByMedicineIdWithLimit(mMedicineId, mLimit);
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
