package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.dao.ProfileDao;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class PagedProfileItemsCmd {
    private static final String TAG = PagedProfileItemsCmd.class.getName();
    private ExecutorService mExecutorService;
    private ProfileDao mProfileDao;
    private ILogger mLogger;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<Profile>> mItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final BehaviorSubject<Set<Long>> mSelectedIdsSubject;

    public PagedProfileItemsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mProfileDao = provider.get(ProfileDao.class);
        mLogger = provider.get(ILogger.class);
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
        Set<Long> selectedIds = new LinkedHashSet<>();
        selectedIds.add(profile.id);
        mSelectedIdsSubject.onNext(selectedIds);
    }

    /**
     * Add the profile into the current selection without clearing prior selections
     *
     * @param profile the profile to add into the selection
     */
    public void addSelectedProfile(Profile profile) {
        Set<Long> selectedIds = new LinkedHashSet<>(mSelectedIdsSubject.getValue());
        selectedIds.add(profile.id);
        mSelectedIdsSubject.onNext(selectedIds);
    }

    public void unSelectProfile(Profile profile) {
        Set<Long> selectedIds = new LinkedHashSet<>(mSelectedIdsSubject.getValue());
        selectedIds.remove(profile.id);
        mSelectedIdsSubject.onNext(selectedIds);
    }

    /**
     * Select every profile matching the active search, or every profile when no
     * search is active. The displayed item list is left unchanged.
     * Must only be used in multi select mode.
     */
    public void selectAllProfiles() {
        mExecutorService.execute(() -> {
            try {
                List<Profile> profileList = isSearching()
                        ? mProfileDao.searchProfile(mSearch)
                        : mProfileDao.findProfiles();
                Set<Long> selectedIds = new LinkedHashSet<>();
                for (Profile profile : profileList) {
                    selectedIds.add(profile.id);
                }
                mSelectedIdsSubject.onNext(selectedIds);
            } catch (Throwable throwable) {
                mLogger.e(TAG, throwable.getMessage(), throwable);
            }
        });
    }

    /**
     * Clear the whole current selection, must only be used in multi select mode
     */
    public void unSelectAllProfiles() {
        mSelectedIdsSubject.onNext(new LinkedHashSet<>());
    }

    public Flowable<Set<Long>> getSelectedIdsFlow() {
        return Flowable.fromObservable(mSelectedIdsSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * @return the current selected ids, this is the full selection which may
     * include ids hidden by an active search filter
     */
    public Set<Long> getSelectedIds() {
        return mSelectedIdsSubject.getValue();
    }

    /**
     * Resolve the current selection through the database, so profiles selected
     * before a search hid them are still included regardless of the displayed list
     *
     * @return the selected profiles in selection order
     */
    public Single<ArrayList<Profile>> getSelectedProfiles() {
        return Single.fromCallable(() -> {
                    Set<Long> selectedIds = new LinkedHashSet<>(mSelectedIdsSubject.getValue());
                    if (selectedIds.isEmpty()) return new ArrayList<Profile>();
                    List<Profile> profileList = mProfileDao.findProfilesByIds(new ArrayList<>(selectedIds));
                    // preserve the selection order (LinkedHashSet insertion order)
                    ArrayList<Profile> ordered = new ArrayList<>(selectedIds.size());
                    for (Long id : selectedIds) {
                        for (Profile profile : profileList) {
                            if (profile.id.equals(id)) {
                                ordered.add(profile);
                                break;
                            }
                        }
                    }
                    return ordered;
                })
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
