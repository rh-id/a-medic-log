package m.co.rh.id.a_medic_log.app.ui.component.note;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.PagedNoteItemsCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.NoteChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class NoteListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = NoteListSV.class.getName();

    private Long mProfileId;

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient NoteChangeNotifier mNoteChangeNotifier;
    private transient PagedNoteItemsCmd mPagedNoteItemsCmd;
    private transient RxDisposer mRxDisposer;
    private transient PublishSubject<String> mSearchStringSubject;
    private transient TextWatcher mSearchTextWatcher;
    private transient NoteRecyclerViewAdapter mNoteRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mOnNotesScrollListener;

    public NoteListSV(Long profileId) {
        mProfileId = profileId;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mNoteChangeNotifier = mSvProvider.get(NoteChangeNotifier.class);
        mPagedNoteItemsCmd = mSvProvider.get(PagedNoteItemsCmd.class);
        mPagedNoteItemsCmd.loadWithProfileId(mProfileId);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mSearchStringSubject = PublishSubject.create();
        mSearchTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mSearchStringSubject.onNext(editable.toString());
            }
        };
        mOnNotesScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedNoteItemsCmd.loadNextPage();
                }
            }
        };
        mNoteRecyclerViewAdapter = new NoteRecyclerViewAdapter(
                mPagedNoteItemsCmd,
                mNavigator, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.list_note, container, false);
        EditText editTextSearch = rootLayout.findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(mSearchTextWatcher);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mNoteRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        recyclerView.addOnScrollListener(mOnNotesScrollListener);
        mRxDisposer
                .add("createView_onItemSearched",
                        mSearchStringSubject
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(searchString -> mPagedNoteItemsCmd
                                        .search(searchString))
                );
        mRxDisposer
                .add("createView_onItemRefreshed",
                        mPagedNoteItemsCmd.getItemsFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(decks -> mNoteRecyclerViewAdapter.notifyItemRefreshed())
                );
        mRxDisposer
                .add("createView_onItemAdded",
                        mNoteChangeNotifier.getAddedNote()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(noteState -> {
                                    mNoteRecyclerViewAdapter.notifyItemAdded(noteState.getNote());
                                    recyclerView.scrollToPosition(0);
                                }));
        mRxDisposer
                .add("createView_onItemUpdated",
                        mNoteChangeNotifier.getUpdatedNote()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(noteUpdatedEvent -> mNoteRecyclerViewAdapter.notifyItemUpdated(noteUpdatedEvent.getAfter().getNote())));
        mRxDisposer
                .add("createView_onLoadingChanged",
                        mPagedNoteItemsCmd.getLoadingFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(swipeRefreshLayout::setRefreshing)
                );
        mRxDisposer
                .add("createView_onItemDeleted",
                        mNoteChangeNotifier
                                .getDeletedNote().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(noteState -> mNoteRecyclerViewAdapter.notifyItemDeleted(noteState.getNote())));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mSearchStringSubject != null) {
            mSearchStringSubject.onComplete();
            mSearchStringSubject = null;
        }
        mSearchTextWatcher = null;
        if (mNoteRecyclerViewAdapter != null) {
            mNoteRecyclerViewAdapter.dispose(activity);
            mNoteRecyclerViewAdapter = null;
        }
        mOnNotesScrollListener = null;
    }

    @Override
    public void onRefresh() {
        mPagedNoteItemsCmd.refresh();
    }
}
