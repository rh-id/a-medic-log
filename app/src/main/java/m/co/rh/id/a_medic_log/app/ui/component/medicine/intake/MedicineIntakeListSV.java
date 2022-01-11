package m.co.rh.id.a_medic_log.app.ui.component.medicine.intake;

import android.app.Activity;
import android.content.Context;
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
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteMedicineIntakeCmd;
import m.co.rh.id.a_medic_log.app.provider.command.PagedMedicineIntakeItemsCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineIntakeDetailPage;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.rx.SerialBehaviorSubject;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class MedicineIntakeListSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener, MedicineIntakeItemSV.OnEditClick, MedicineIntakeItemSV.OnDeleteClick {
    private static final String TAG = MedicineIntakeListSV.class.getName();

    private transient INavigator mNavigator;
    private transient ExecutorService mExecutorService;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient MedicineIntakeChangeNotifier mMedicineIntakeChangeNotifier;
    private transient PagedMedicineIntakeItemsCmd mPagedMedicineIntakeItemsCmd;
    private transient DeleteMedicineIntakeCmd mDeleteMedicineIntakeCmd;
    private SerialBehaviorSubject<String> mSearchStringSubject;
    private Long mMedicineId;
    private transient TextWatcher mSearchTextWatcher;
    private transient MedicineIntakeRecyclerViewAdapter mMedicineIntakeRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mOnScrollListener;

    public MedicineIntakeListSV() {
        mSearchStringSubject = new SerialBehaviorSubject<>("");
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mMedicineIntakeChangeNotifier = mSvProvider.get(MedicineIntakeChangeNotifier.class);
        mPagedMedicineIntakeItemsCmd = mSvProvider.get(PagedMedicineIntakeItemsCmd.class);
        mPagedMedicineIntakeItemsCmd.setMedicineId(mMedicineId);
        mPagedMedicineIntakeItemsCmd.refresh();
        mDeleteMedicineIntakeCmd = mSvProvider.get(DeleteMedicineIntakeCmd.class);
        if (mSearchTextWatcher == null) {
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
        }
        if (mOnScrollListener == null) {
            mOnScrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mPagedMedicineIntakeItemsCmd.loadNextPage();
                    }
                }
            };
        }
        mMedicineIntakeRecyclerViewAdapter = new MedicineIntakeRecyclerViewAdapter(mPagedMedicineIntakeItemsCmd,
                this, this, mNavigator, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.list_medicine_intake, container, false);
        EditText editTextSearch = rootLayout.findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(mSearchTextWatcher);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mMedicineIntakeRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        recyclerView.addOnScrollListener(mOnScrollListener);
        mRxDisposer
                .add("createView_onItemSearched",
                        mSearchStringSubject.getSubject()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(searchString -> mPagedMedicineIntakeItemsCmd
                                        .search(searchString))
                );
        mRxDisposer
                .add("createView_onItemRefreshed",
                        mPagedMedicineIntakeItemsCmd.getItemsFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(decks -> mMedicineIntakeRecyclerViewAdapter.notifyItemRefreshed())
                );
        mRxDisposer
                .add("createView_onItemAdded",
                        mMedicineIntakeChangeNotifier.getAddedMedicineIntake()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mMedicineIntakeRecyclerViewAdapter::notifyItemAdded));
        mRxDisposer
                .add("createView_onItemUpdated",
                        mMedicineIntakeChangeNotifier.getUpdatedMedicineIntake()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(medicineIntakeUpdatedEvent ->
                                        mMedicineIntakeRecyclerViewAdapter.notifyItemUpdated(medicineIntakeUpdatedEvent.getAfter())));
        mRxDisposer
                .add("createView_onLoadingChanged",
                        mPagedMedicineIntakeItemsCmd.getLoadingFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(swipeRefreshLayout::setRefreshing)
                );
        mRxDisposer
                .add("createView_onItemDeleted",
                        mMedicineIntakeChangeNotifier
                                .getDeletedMedicineIntake()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mMedicineIntakeRecyclerViewAdapter::notifyItemDeleted));

        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mSearchTextWatcher = null;
        if (mMedicineIntakeRecyclerViewAdapter != null) {
            mMedicineIntakeRecyclerViewAdapter.dispose(activity);
            mMedicineIntakeRecyclerViewAdapter = null;
        }
        mOnScrollListener = null;
    }

    @Override
    public void onRefresh() {
        mPagedMedicineIntakeItemsCmd.refresh();
    }

    @Override
    public void onEditClick(MedicineIntake medicineIntake) {
        mNavigator.push(Routes.MEDICINE_INTAKE_DETAIL_PAGE,
                MedicineIntakeDetailPage.Args.forUpdate(medicineIntake.clone()));
    }

    @Override
    public void onDeleteClick(MedicineIntake medicineIntake) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm);
        String content = context.getString(R.string.confirm_delete_medicine_intake, medicineIntake.description);
        NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mNavigator.push(navExtDialogConfig.getRoutePath(NavExtDialogConfig.ROUTE_CONFIRM),
                navExtDialogConfig.args_confirmDialog(title, content),
                (navigator, navRoute, activity, currentView) -> {
                    Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                    Boolean result = provider.get(NavExtDialogConfig.class).result_confirmDialog(navRoute);
                    if (result != null && result) {
                        confirmDeleteMedicineIntake(medicineIntake);
                    }
                });
    }

    private void confirmDeleteMedicineIntake(MedicineIntake medicineIntake) {
        Context context = mSvProvider.getContext();
        mRxDisposer.add("confirmDeleteMedicineIntake_deleteMedicineIntake",
                mDeleteMedicineIntakeCmd
                        .execute(medicineIntake)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((note, throwable) -> {
                            String errorMessage = context.getString(R.string.error_failed_to_delete_medicine_intake);
                            String successMessage = context.getString(R.string.success_deleting_medicine_intake);
                            if (throwable != null) {
                                mSvProvider.get(ILogger.class)
                                        .e(TAG, errorMessage, throwable);
                            } else {
                                mSvProvider.get(ILogger.class)
                                        .i(TAG, successMessage);
                                mMedicineIntakeRecyclerViewAdapter.notifyItemDeleted(medicineIntake);
                            }
                        })
        );
    }

    public void setMedicineId(long medicineId) {
        mMedicineId = medicineId;
        if (mPagedMedicineIntakeItemsCmd != null) {
            mPagedMedicineIntakeItemsCmd.setMedicineId(medicineId);
            mPagedMedicineIntakeItemsCmd.refresh();
        }
    }
}
