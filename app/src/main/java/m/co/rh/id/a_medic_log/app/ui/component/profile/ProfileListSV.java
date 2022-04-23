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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.PagedProfileItemsCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.ProfileChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
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
        mSvProvider.get(PagedProfileItemsCmd.class).refresh();
        if (mSearchStringSubject == null) {
            mSearchStringSubject = PublishSubject.create();
        }
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
        mOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mSvProvider.get(PagedProfileItemsCmd.class).loadNextPage();
                }
            }
        };
        ProfileItemSV.ListMode listMode = null;
        if (mListMode != null) {
            if (mListMode.mSelectMode == ListMode.SELECT_MODE) {
                listMode = ProfileItemSV.ListMode.selectMode();
            }
        }
        mProfileRecyclerViewAdapter = new ProfileRecyclerViewAdapter(
                mSvProvider.get(PagedProfileItemsCmd.class),
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
                                .subscribe(searchString -> mSvProvider.get(PagedProfileItemsCmd.class)
                                        .search(searchString))
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemRefreshed",
                        mSvProvider.get(PagedProfileItemsCmd.class).getItemsFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(decks -> mProfileRecyclerViewAdapter.notifyItemRefreshed())
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
                        mSvProvider.get(PagedProfileItemsCmd.class).getLoadingFlow()
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
        mSvProvider.get(PagedProfileItemsCmd.class).refresh();
    }

    public ArrayList<Profile> getSelectedProfile() {
        if (mSvProvider == null) return new ArrayList<>();
        return mSvProvider.get(PagedProfileItemsCmd.class).getSelectedItems();
    }

    public static class ListMode implements Serializable {
        public static ListMode selectMode() {
            ListMode listMode = new ListMode();
            listMode.mSelectMode = SELECT_MODE;
            return listMode;
        }

        private static final byte SELECT_MODE = 0;

        private byte mSelectMode;

        private ListMode() {
        }

        public int getSelectMode() {
            return mSelectMode;
        }
    }
}
