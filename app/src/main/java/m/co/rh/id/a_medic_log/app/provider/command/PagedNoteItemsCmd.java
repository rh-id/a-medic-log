package m.co.rh.id.a_medic_log.app.provider.command;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.repository.NoteRepository;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class PagedNoteItemsCmd {
    private static final String TAG = PagedNoteItemsCmd.class.getName();
    private ExecutorService mExecutorService;
    private ProviderValue<NoteRepository> mNoteRepository;
    private ILogger mLogger;
    private Long mProfileId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<Note>> mItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedNoteItemsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mNoteRepository = provider.lazyGet(NoteRepository.class);
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
                    mNoteRepository.get().searchNotes(mProfileId, mSearch, mLimit));
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

    private ArrayList<Note> loadItems() {
        return mNoteRepository.get().findNotes(mProfileId, mLimit);
    }

    public ArrayList<Note> getAllItems() {
        return mItemsSubject.getValue();
    }

    public Flowable<ArrayList<Note>> getItemsFlow() {
        return Flowable.fromObservable(mItemsSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 20;
    }

    public void loadWithProfileId(Long profileId) {
        mProfileId = profileId;
        refresh();
    }
}
