package m.co.rh.id.a_medic_log.app.ui.component.medicine;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.model.MedicineRow;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.PagedMedicinesCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.rx.RxUtils;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineIntakeListPage;
import m.co.rh.id.a_medic_log.app.util.SimpleTextWatcher;
import m.co.rh.id.a_medic_log.app.util.UiUtils;
import m.co.rh.id.a_medic_log.base.repository.MedicineRepository;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * List component of the medicines page, it owns its own
 * {@link PagedMedicinesCmd} instance and renders the aggregated medicine rows.
 */
public class MedicineListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener,
        MedicinesItemSV.MedicinesOnRowClick, MedicinesItemSV.MedicinesOnDeleteClick,
        MedicinesItemSV.MedicinesOnIntakeHistoryClick {
    private static final String TAG = MedicineListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient ExecutorService mExecutorService;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient ILogger mLogger;
    private transient MedicineChangeNotifier mMedicineChangeNotifier;
    private transient MedicineIntakeChangeNotifier mMedicineIntakeChangeNotifier;
    private transient MedicineReminderChangeNotifier mMedicineReminderChangeNotifier;
    private transient PagedMedicinesCmd mMedicinesCmd;
    private transient DeleteMedicineCmd mDeleteMedicineCmd;
    private transient MedicineRepository mMedicineRepository;
    private SerialBehaviorSubject<String> mSearchStringSubject;
    private Long mProfileId;
    private transient TextWatcher mSearchTextWatcher;
    private transient MedicinesRecyclerViewAdapter mMedicinesRecyclerViewAdapter;

    public MedicineListSV() {
        mSearchStringSubject = new SerialBehaviorSubject<>("");
    }

    @Override
    public void provideComponent(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mLogger = mSvProvider.get(ILogger.class);
        mMedicineChangeNotifier = mSvProvider.get(MedicineChangeNotifier.class);
        mMedicineIntakeChangeNotifier = mSvProvider.get(MedicineIntakeChangeNotifier.class);
        mMedicineReminderChangeNotifier = mSvProvider.get(MedicineReminderChangeNotifier.class);
        mMedicinesCmd = mSvProvider.get(PagedMedicinesCmd.class);
        mMedicinesCmd.loadWithProfileId(mProfileId);
        mMedicineRepository = mSvProvider.get(MedicineRepository.class);
        mDeleteMedicineCmd = mSvProvider.get(DeleteMedicineCmd.class);
        if (mSearchTextWatcher == null) {
            mSearchTextWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    mSearchStringSubject.onNext(editable.toString());
                }
            };
        }
        mMedicinesRecyclerViewAdapter = new MedicinesRecyclerViewAdapter(mMedicinesCmd,
                this, this, this, mNavigator, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.list_medicines, container, false);
        EditText editTextSearch = rootLayout.findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(mSearchTextWatcher);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mMedicinesRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mRxDisposer
                .add("createView_onItemSearched",
                        mSearchStringSubject.getSubject()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(searchString -> mMedicinesCmd
                                        .search(searchString))
                );
        mRxDisposer
                .add("createView_onItemRefreshed",
                        mMedicinesCmd.getItemsFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(medicineRows -> mMedicinesRecyclerViewAdapter.notifyItemRefreshed())
                );
        mRxDisposer
                .add("createView_onLoadingChanged",
                        mMedicinesCmd.getLoadingFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(swipeRefreshLayout::setRefreshing)
                );
        mRxDisposer
                .add("createView_onMedicineChanged",
                        mMedicineChangeNotifier.getAnyChanged()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(medicineState -> mMedicinesCmd.refresh())
                );
        mRxDisposer
                .add("createView_onMedicineIntakeChanged",
                        mMedicineIntakeChangeNotifier.getAnyChanged()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(medicineIntake -> mMedicinesCmd.refresh())
                );
        mRxDisposer
                .add("createView_onMedicineReminderChanged",
                        mMedicineReminderChangeNotifier.getAnyChanged()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(medicineReminder -> mMedicinesCmd.refresh())
                );
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
        if (mMedicinesRecyclerViewAdapter != null) {
            mMedicinesRecyclerViewAdapter.dispose(activity);
            mMedicinesRecyclerViewAdapter = null;
        }
    }

    @Override
    public void onRefresh() {
        mMedicinesCmd.refresh();
    }

    @Override
    public void medicines_onRowClick(MedicineRow medicineRow) {
        Long medicineId = medicineRow.medicine.id;
        if (medicineId == null) {
            return;
        }
        // hydrate the full state off the main thread before pushing the detail page
        mRxDisposer.add("MedicineListSV.medicines_onRowClick_queryMedicineInfo",
                Single.fromCallable(() -> mMedicineRepository.queryMedicineInfo(medicineId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((medicineState, throwable) -> {
                            if (throwable != null) {
                                RxUtils.logError(mLogger, TAG, throwable);
                                return;
                            }
                            mNavigator.push(Routes.MEDICINE_DETAIL_PAGE,
                                    MedicineDetailPage.Args.forUpdate(medicineState));
                        }));
    }

    @Override
    public void medicines_onDeleteClick(MedicineRow medicineRow) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm);
        String content = context.getString(R.string.confirm_delete_medicines,
                medicineRow.medicine.name);
        UiUtils.showConfirmDialog(mNavigator, title, content,
                () -> confirmDeleteMedicineRow(medicineRow));
    }

    @Override
    public void medicines_onIntakeHistoryClick(MedicineRow medicineRow) {
        Long medicineId = medicineRow.medicine.id;
        if (medicineId == null) {
            return;
        }
        mNavigator.push(Routes.MEDICINE_INTAKES_PAGE,
                MedicineIntakeListPage.Args.with(medicineId));
    }

    /**
     * The delete hydrates the FULL medicine state including its reminders
     * off the main thread first: the reminder event handler cancels the
     * AlarmManager alarms only from the payload's reminder list, deleting a
     * bare medicine entity would orphan the enabled alarms.
     */
    private void confirmDeleteMedicineRow(MedicineRow medicineRow) {
        Long medicineId = medicineRow.medicine.id;
        if (medicineId == null) {
            return;
        }
        Context context = mSvProvider.getContext();
        String successMessage = context.getString(R.string.success_deleting_medicine);
        RxUtils.executeAndLog(mRxDisposer, "MedicineListSV.confirmDeleteMedicineRow_deleteMedicineCmd",
                Single.fromCallable(() -> mMedicineRepository.queryMedicineInfo(medicineId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .flatMap(medicineState -> mDeleteMedicineCmd.execute(medicineState)),
                mSvProvider.get(ILogger.class), TAG, successMessage,
                medicineState -> mMedicinesCmd.refresh());
    }

    public void setProfileId(Long profileId) {
        mProfileId = profileId;
        if (mMedicinesCmd != null) {
            mMedicinesCmd.loadWithProfileId(profileId);
        }
    }
}
