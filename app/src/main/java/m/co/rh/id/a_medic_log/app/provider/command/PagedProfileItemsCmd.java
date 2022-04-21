package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.aprovider.Provider;

public class PagedProfileItemsCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ProfileDao mProfileDao;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<Profile>> mItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final BehaviorSubject<Set<Long>> mSelectedIdsSubject;

    public PagedProfileItemsCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mProfileDao = provider.get(ProfileDao.class);
        mItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        mSelectedIdsSubject = BehaviorSubject.createDefault(new LinkedHashSet<>());
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
                    List<Profile> profileList = mProfileDao.searchProfile(mSearch);
                    mItemsSubject.onNext(new ArrayList<>(profileList));
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
        mExecutorService.execute(() -> {
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

    private ArrayList<Profile> loadItems() {
        List<Profile> profileList = mProfileDao.loadProfilesWithLimit(mLimit);
        return new ArrayList<>(profileList);
    }

    public ArrayList<Profile> getAllItems() {
        return mItemsSubject.getValue();
    }

    public Flowable<ArrayList<Profile>> getItemsFlow() {
        return Flowable.fromObservable(mItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 20;
    }

    public void selectProfile(Profile profile) {
        Set<Long> selectedIds = mSelectedIdsSubject.getValue();
        selectedIds.clear();
        selectedIds.add(profile.id);
        mSelectedIdsSubject.onNext(selectedIds);
    }

    public void unSelectProfile(Profile profile) {
        Set<Long> selectedIds = mSelectedIdsSubject.getValue();
        selectedIds.remove(profile.id);
        mSelectedIdsSubject.onNext(selectedIds);
    }

    public ArrayList<Profile> getSelectedItems() {
        Set<Long> selectedIds = mSelectedIdsSubject.getValue();
        ArrayList<Profile> returnedItems = new ArrayList<>();
        if (!selectedIds.isEmpty()) {
            ArrayList<Profile> items = getAllItems();
            if (!items.isEmpty()) {
                for (Profile profile : items) {
                    if (selectedIds.contains(profile.id)) {
                        returnedItems.add(profile);
                    }
                }
            }
        }
        return returnedItems;
    }
}
