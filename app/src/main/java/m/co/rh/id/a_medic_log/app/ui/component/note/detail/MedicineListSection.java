package m.co.rh.id.a_medic_log.app.ui.component.note.detail;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryNoteCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.medicine.MedicineItemSV;
import m.co.rh.id.a_medic_log.app.ui.component.medicine.MedicineRecyclerViewAdapter;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineIntakeDetailPage;
import m.co.rh.id.a_medic_log.app.ui.page.MedicineIntakeListPage;
import m.co.rh.id.a_medic_log.app.util.UiUtils;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.a_medic_log.base.state.NoteState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class MedicineListSection implements View.OnClickListener,
        MedicineItemSV.MedicineItemOnMedicineIntakeListClick, MedicineItemSV.MedicineItemOnEditClick,
        MedicineItemSV.MedicineItemOnDeleteClick, MedicineItemSV.MedicineItemOnAddMedicineIntakeClick {

    private static final String TAG = MedicineListSection.class.getName();

    private final INavigator mNavigator;
    private final Provider mSvProvider;
    private final NoteState mNoteState;
    private final SerialBehaviorSubject<Boolean> mShowSubject;
    private final Long mRouteNoteId;
    private final ILogger mLogger;
    private final RxDisposer mRxDisposer;
    private final ExecutorService mExecutorService;
    private final QueryNoteCmd mQueryNoteCmd;
    private final DeleteMedicineCmd mDeleteMedicineCmd;
    private final MedicineReminderChangeNotifier mMedicineReminderChangeNotifier;
    private final StatefulView mParentStatefulView;

    private MedicineRecyclerViewAdapter mMedicineRecyclerViewAdapter;
    private TextView mMedicineTitle;
    private Button mShareMedicineButton;
    private RecyclerView mMedicineRecyclerView;

    public MedicineListSection(INavigator navigator, Provider svProvider,
                               NoteState noteState, SerialBehaviorSubject<Boolean> showSubject,
                               Long routeNoteId, StatefulView parentStatefulView) {
        mNavigator = navigator;
        mSvProvider = svProvider;
        mNoteState = noteState;
        mShowSubject = showSubject;
        mRouteNoteId = routeNoteId;
        mParentStatefulView = parentStatefulView;
        mLogger = svProvider.get(ILogger.class);
        mRxDisposer = svProvider.get(RxDisposer.class);
        mExecutorService = svProvider.get(ExecutorService.class);
        mQueryNoteCmd = svProvider.get(QueryNoteCmd.class);
        mDeleteMedicineCmd = svProvider.get(DeleteMedicineCmd.class);
        mMedicineReminderChangeNotifier = svProvider.get(MedicineReminderChangeNotifier.class);
        mMedicineRecyclerViewAdapter = new MedicineRecyclerViewAdapter(mNoteState,
                this, this, this, this, mNavigator, mParentStatefulView);
    }

    public void bindViews(Activity activity, ViewGroup rootLayout) {
        mShareMedicineButton = rootLayout.findViewById(R.id.button_share_medicine);
        Button addMedicineButton = rootLayout.findViewById(R.id.button_add_medicine);
        Button expandMedicine = rootLayout.findViewById(R.id.button_expand_medicine);
        View medicineTextContainer = rootLayout.findViewById(R.id.container_medicine_text);
        mMedicineTitle = rootLayout.findViewById(R.id.text_medicine_title);
        mMedicineRecyclerView = rootLayout.findViewById(R.id.recyclerView_medicine);
        mShareMedicineButton.setOnClickListener(this);
        addMedicineButton.setOnClickListener(this);
        expandMedicine.setOnClickListener(this);
        medicineTextContainer.setOnClickListener(this);
        mMedicineRecyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mMedicineRecyclerView.setAdapter(mMedicineRecyclerViewAdapter);
        mRxDisposer.add("MedicineListSection.bindViews_onMedicineListShow",
                mShowSubject.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                mMedicineRecyclerView.setVisibility(View.VISIBLE);
                            } else {
                                mMedicineRecyclerView.setVisibility(View.GONE);
                            }
                            expandMedicine.setActivated(aBoolean);
                        }));
        mRxDisposer.add("MedicineListSection.bindViews_onMedicineChanged",
                mNoteState.getMedicineListFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(medicineStates ->
                        {
                            int size = medicineStates.size();
                            mMedicineTitle.setText(activity.getString(R.string.title_medicine, size));
                            if (size > 0) {
                                mShareMedicineButton.setVisibility(View.VISIBLE);
                            } else {
                                mShareMedicineButton.setVisibility(View.GONE);
                            }
                            mMedicineRecyclerViewAdapter.notifyItemRefreshed();
                        })
        );
        mRxDisposer.add("MedicineListSection.bindViews_onMedicineReminderAdded",
                mMedicineReminderChangeNotifier.getAddedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .flatMapSingle(medicineReminder -> {
                            if (isUpdate()) {
                                return mQueryNoteCmd.queryMedicineInfo(mNoteState);
                            }
                            return Single.just(mNoteState.getMedicineList());
                        })
                        .subscribe(
                                medicineStates -> {},
                                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)
                        ));
        mRxDisposer.add("MedicineListSection.bindViews_onMedicineReminderUpdated",
                mMedicineReminderChangeNotifier.getUpdatedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .flatMapSingle(medicineReminder -> {
                            if (isUpdate()) {
                                return mQueryNoteCmd.queryMedicineInfo(mNoteState);
                            }
                            return Single.just(mNoteState.getMedicineList());
                        })
                        .subscribe(
                                medicineStates -> {},
                                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)
                        ));
        mRxDisposer.add("MedicineListSection.bindViews_onMedicineReminderDeleted",
                mMedicineReminderChangeNotifier.getDeletedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .flatMapSingle(medicineReminder -> {
                            if (isUpdate()) {
                                return mQueryNoteCmd.queryMedicineInfo(mNoteState);
                            }
                            return Single.just(mNoteState.getMedicineList());
                        })
                        .subscribe(
                                medicineStates -> {},
                                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)
                        ));
    }

    @Override
    public void medicineItem_onEditClick(MedicineState medicineState) {
        MedicineDetailPage.Args args;
        MedicineState medicineStateArgs = medicineState.clone();
        if (isUpdate()) {
            args = MedicineDetailPage.Args.forUpdate(medicineStateArgs);
        } else {
            args = MedicineDetailPage.Args.forEdit(medicineStateArgs);
        }
        mNavigator.push(Routes.MEDICINE_DETAIL_PAGE,
                args,
                (navigator, navRoute, activity, currentView) -> {
                    MedicineDetailPage.Result result = MedicineDetailPage.Result.of(navRoute);
                    if (result != null) {
                        updateMedicineState(result.getMedicineState());
                    }
                });
    }

    @Override
    public void medicineItem_onDeleteClick(MedicineState medicineState) {
        if (isUpdate()) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_medicine, medicineState.getMedicineName());
            UiUtils.showConfirmDialog(mNavigator, mSvProvider, title, content,
                    () -> confirmDeleteMedicine(medicineState));
        } else {
            mMedicineRecyclerViewAdapter.notifyItemDeleted(medicineState);
        }
    }

    private void confirmDeleteMedicine(MedicineState medicineState) {
        Context context = mSvProvider.getContext();
        mRxDisposer.add("MedicineListSection.confirmDeleteMedicine_deleteMedicineCmd",
                mDeleteMedicineCmd
                        .execute(medicineState)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((note, throwable) -> {
                            String successMessage = context.getString(R.string.success_deleting_medicine);
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) {
                                    cause = throwable;
                                }
                                mLogger
                                        .e(TAG, cause.getMessage(), cause);
                            } else {
                                mLogger
                                        .i(TAG, successMessage);
                                mMedicineRecyclerViewAdapter.notifyItemDeleted(medicineState);
                            }
                        })
        );
    }

    @Override
    public void medicineItem_onAddMedicineIntakeClick(MedicineState medicineState) {
        Long medicineId = medicineState.getMedicineId();
        if (medicineId != null) {
            mNavigator.push(Routes.MEDICINE_INTAKE_DETAIL_PAGE,
                    MedicineIntakeDetailPage.Args.with(medicineId));
        }
    }

    @Override
    public void medicineItem_onMedicineIntakeListClick(MedicineState medicineState) {
        Long medicineId = medicineState.getMedicineId();
        if (medicineId != null) {
            mNavigator.push(Routes.MEDICINE_INTAKES_PAGE,
                    MedicineIntakeListPage.Args.with(medicineId));
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_share_medicine) {
            mRxDisposer.add("MedicineListSection.onClick_shareMedicine",
                    mQueryNoteCmd.createShareMedicineText(mNoteState)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((s, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    Context context = mSvProvider.getContext();
                                    UiUtils.shareText(context, s, context.getString(R.string.share_text));
                                }
                            })
            );
        } else if (id == R.id.button_add_medicine) {
            MedicineDetailPage.Args args;
            if (isUpdate()) {
                args = MedicineDetailPage.Args.save(getRouteNoteId());
            } else {
                args = MedicineDetailPage.Args.dontSave();
            }
            mNavigator.push(Routes.MEDICINE_DETAIL_PAGE,
                    args,
                    (navigator, navRoute, activity, currentView) -> {
                        MedicineDetailPage.Result result = MedicineDetailPage.Result.of(navRoute);
                        if (result != null) {
                            addMedicineState(result.getMedicineState());
                        }
                    });
        } else if (id == R.id.container_medicine_text ||
                id == R.id.button_expand_medicine) {
            mShowSubject.onNext(!mShowSubject.getValue());
        }
    }

    private void addMedicineState(MedicineState medicineState) {
        mMedicineRecyclerViewAdapter.notifyItemAdded(medicineState);
    }

    private void updateMedicineState(MedicineState medicineState) {
        mMedicineRecyclerViewAdapter.notifyItemUpdated(medicineState);
    }

    private boolean isUpdate() {
        return mRouteNoteId != null;
    }

    private long getRouteNoteId() {
        return mRouteNoteId;
    }

    public void dispose(Activity activity) {
        if (mMedicineRecyclerViewAdapter != null) {
            mMedicineRecyclerViewAdapter.dispose(activity);
            mMedicineRecyclerViewAdapter = null;
        }
        mMedicineTitle = null;
        mShareMedicineButton = null;
        mMedicineRecyclerView = null;
    }
}
