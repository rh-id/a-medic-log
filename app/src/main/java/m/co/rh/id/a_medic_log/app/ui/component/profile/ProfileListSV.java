package m.co.rh.id.a_medic_log.app.ui.component.profile;

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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.PagedProfileItemsCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.util.SimpleTextWatcher;
import m.co.rh.id.a_medic_log.base.entity.Profile;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ProfileListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = ProfileListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient PagedProfileItemsCmd mPagedProfileItemsCmd;
    private transient PublishSubject<String> mSearchStringSubject;
    private transient TextWatcher mSearchTextWatcher;
    private transient ProfileRecyclerViewAdapter mProfileRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mOnScrollListener;

    private ListMode mListMode;

    public ProfileListSV() {
        this(null);
    }

    public ProfileListSV(ListMode listMode) {
        mListMode = listMode;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mPagedProfileItemsCmd = mSvProvider.get(PagedProfileItemsCmd.class);
        mPagedProfileItemsCmd.refresh();
        if (mSearchStringSubject == null) {
            mSearchStringSubject = PublishSubject.create();
        }
        mSearchTextWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                mSearchStringSubject.onNext(editable.toString());
            }
        };
        mOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedProfileItemsCmd.loadNextPage();
                }
            }
        };
        ProfileItemSV.ListMode listMode = null;
        if (mListMode != null) {
            if (mListMode.mSelectMode == ListMode.SELECT_MODE) {
                listMode = ProfileItemSV.ListMode.selectMode();
            } else if (mListMode.mSelectMode == ListMode.MULTI_SELECT_MODE) {
                listMode = ProfileItemSV.ListMode.multiSelectMode();
            }
        }
        mProfileRecyclerViewAdapter = new ProfileRecyclerViewAdapter(
                mPagedProfileItemsCmd,
                mNavigator, this, listMode);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.list_profile, container, false);
        EditText editTextSearch = rootLayout.findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(mSearchTextWatcher);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mProfileRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        recyclerView.addOnScrollListener(mOnScrollListener);
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemSearched",
                        mSearchStringSubject
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mSvProvider.get(ExecutorService.class)))
                                .subscribe(searchString -> mPagedProfileItemsCmd
                                        .search(searchString))
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemRefreshed",
                        mPagedProfileItemsCmd.getItemsFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(decks -> mProfileRecyclerViewAdapter.notifyItemRefreshed())
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onSelectionChanged",
                        mPagedProfileItemsCmd.getSelectedIdsFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(selectedIds -> mProfileRecyclerViewAdapter.notifyItemRefreshed())
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemAdded",
                        mSvProvider.get(ProfileChangeNotifier.class).getAddedProfile()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(profile -> {
                                    mProfileRecyclerViewAdapter.notifyItemAdded(profile);
                                    recyclerView.scrollToPosition(0);
                                }));
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemUpdated",
                        mSvProvider.get(ProfileChangeNotifier.class).getUpdatedProfile()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mProfileRecyclerViewAdapter::notifyItemUpdated));
        mSvProvider.get(RxDisposer.class)
                .add("createView_onLoadingChanged",
                        mPagedProfileItemsCmd.getLoadingFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(swipeRefreshLayout::setRefreshing)
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemDeleted",
                        mSvProvider.get(ProfileChangeNotifier.class)
                                .getDeletedProfile().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mProfileRecyclerViewAdapter::notifyItemDeleted));

        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mPagedProfileItemsCmd = null;
        if (mSearchStringSubject != null) {
            mSearchStringSubject.onComplete();
            mSearchStringSubject = null;
        }
        mSearchTextWatcher = null;
        if (mProfileRecyclerViewAdapter != null) {
            mProfileRecyclerViewAdapter.dispose(activity);
            mProfileRecyclerViewAdapter = null;
        }
        mOnScrollListener = null;
    }

    @Override
    public void onRefresh() {
        mPagedProfileItemsCmd.refresh();
    }

    /**
     * Resolve the current selection through the database, the returned profiles
     * preserve the selection order and may include profiles hidden by an
     * active search filter
     */
    public Single<ArrayList<Profile>> getSelectedProfiles() {
        if (mPagedProfileItemsCmd == null) {
            return Single.just(new ArrayList<>());
        }
        return mPagedProfileItemsCmd.getSelectedProfiles();
    }

    /**
     * Select every profile in the database, must only be used in multi select mode
     */
    public void selectAll() {
        if (mPagedProfileItemsCmd == null) return;
        mPagedProfileItemsCmd.selectAllProfiles();
    }

    /**
     * Clear the current selection, must only be used in multi select mode
     */
    public void unSelectAll() {
        if (mPagedProfileItemsCmd == null) return;
        mPagedProfileItemsCmd.unSelectAllProfiles();
    }

    /**
     * Currently displayed items, when a search is active this contains only the search results
     */
    public ArrayList<Profile> getItems() {
        if (mPagedProfileItemsCmd == null) return new ArrayList<>();
        ArrayList<Profile> items = mPagedProfileItemsCmd.getAllItems();
        return items == null ? new ArrayList<>() : items;
    }

    public Flowable<Set<Long>> getSelectedIdsFlow() {
        if (mPagedProfileItemsCmd == null) {
            return Flowable.just(new LinkedHashSet<>());
        }
        return mPagedProfileItemsCmd.getSelectedIdsFlow();
    }

    public static class ListMode implements Serializable {
        public static ListMode selectMode() {
            ListMode listMode = new ListMode();
            listMode.mSelectMode = SELECT_MODE;
            return listMode;
        }

        public static ListMode multiSelectMode() {
            ListMode listMode = new ListMode();
            listMode.mSelectMode = MULTI_SELECT_MODE;
            return listMode;
        }

        private static final byte SELECT_MODE = 0;

        private static final byte MULTI_SELECT_MODE = 1;

        private byte mSelectMode;

        private ListMode() {
        }

        public int getSelectMode() {
            return mSelectMode;
        }
    }
}
