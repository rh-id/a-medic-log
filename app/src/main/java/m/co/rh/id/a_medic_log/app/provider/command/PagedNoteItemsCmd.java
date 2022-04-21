package m.co.rh.id.a_medic_log.app.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_medic_log.base.dao.NoteDao;
import m.co.rh.id.a_medic_log.base.entity.Note;
import m.co.rh.id.a_medic_log.base.entity.NoteTag;
import m.co.rh.id.aprovider.Provider;

public class PagedNoteItemsCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private NoteDao mNoteDao;
    private Long mProfileId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<Note>> mItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedNoteItemsCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mNoteDao = provider.get(NoteDao.class);
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
                    ArrayList<Note> resultList;
                    Future<List<Note>> futureNoteListFromNoteTag = searchNoteTag(mSearch);
                    List<Note> noteList = mNoteDao.searchNote(mSearch);
                    List<Note> noteListFromNoteTag = futureNoteListFromNoteTag.get();
                    if (!noteListFromNoteTag.isEmpty()) {
                        Set<Note> noteResult = new LinkedHashSet<>();
                        noteResult.addAll(noteList);
                        noteResult.addAll(noteListFromNoteTag);
                        resultList = new ArrayList<>(noteResult);
                    } else {
                        resultList = new ArrayList<>(noteList);
                    }
                    mItemsSubject.onNext(resultList);
                } catch (Throwable throwable) {
                    mItemsSubject.onError(throwable);
                } finally {
                    mIsLoadingSubject.onNext(false);
                }
            }
        });
    }

    private Future<List<Note>> searchNoteTag(String search) {
        return mExecutorService.submit(() -> {
            List<NoteTag> noteTagList = mNoteDao.searchNoteTag(search);
            List<Note> noteList = new ArrayList<>();
            Set<Long> noteIds = new LinkedHashSet<>();
            if (!noteTagList.isEmpty()) {
                for (NoteTag noteTag : noteTagList) {
                    noteIds.add(noteTag.noteId);
                }
                if (!noteIds.isEmpty()) {
                    List<Note> notes = mNoteDao.findNoteByIds(noteIds);
                    noteList.addAll(notes);
                }
            }
            return noteList;
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

    private ArrayList<Note> loadItems() {
        List<Note> noteList;
        if (mProfileId != null) {
            noteList = mNoteDao.findNotesByProfileIdWithLimit(mProfileId, mLimit);
        } else {
            noteList = mNoteDao.findNotesWithLimit(mLimit);
        }
        return new ArrayList<>(noteList);
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
