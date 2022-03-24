package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.DeleteMedicineReminderCmd;
import m.co.rh.id.a_medic_log.app.provider.command.NewMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineReminderCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineReminderChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.adapter.SuggestionAdapter;
import m.co.rh.id.a_medic_log.app.ui.component.medicine.reminder.MedicineReminderItemSV;
import m.co.rh.id.a_medic_log.app.ui.component.medicine.reminder.MedicineReminderRecyclerViewAdapter;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class MedicineDetailPage extends StatefulView<Activity> implements RequireNavigator, RequireNavRoute, RequireComponent<Provider>, Toolbar.OnMenuItemClickListener, MedicineReminderItemSV.OnEditClick, MedicineReminderItemSV.OnEnableSwitchClick, MedicineReminderItemSV.OnDeleteClick, View.OnClickListener {
    private static final String TAG = MedicineDetailPage.class.getName();
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;
    private transient ExecutorService mExecutorService;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient MedicineReminderChangeNotifier mMedicineReminderChangeNotifier;
    private transient NewMedicineCmd mNewMedicineCmd;
    private transient QueryMedicineCmd mQueryMedicineCmd;
    private transient MedicineReminderRecyclerViewAdapter mMedicineReminderRecyclerViewAdapter;
    private transient ArrayAdapter<String> mSuggestionAdapter;
    private transient Function<String, Collection<String>> mSuggestionQuery;

    @NavInject
    private AppBarSV mAppBarSv;

    private MedicineState mMedicineState;

    private transient TextWatcher mNameTextWatcher;
    private transient TextWatcher mDescriptionTextWatcher;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        boolean isUpdate = isUpdate();
        mExecutorService = provider.get(ExecutorService.class);
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mMedicineReminderChangeNotifier = mSvProvider.get(MedicineReminderChangeNotifier.class);
        if (isUpdate) {
            mNewMedicineCmd = mSvProvider.get(UpdateMedicineCmd.class);
        } else {
            mNewMedicineCmd = mSvProvider.get(NewMedicineCmd.class);
        }
        mQueryMedicineCmd = mSvProvider.get(QueryMedicineCmd.class);
        if (mAppBarSv == null) {
            mAppBarSv = new AppBarSV(R.menu.page_medicine_detail);
        }
        if (isUpdate) {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_update_medicine));
        } else {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_add_medicine));
        }
        mAppBarSv.setMenuItemListener(this);
        if (mMedicineState == null) {
            mMedicineState = getMedicineState();
            if (!isUpdate && shouldSave()) {
                mMedicineState.setNoteId(getNoteId());
            }
        }
        initTextWatcher();
        mMedicineReminderRecyclerViewAdapter = new MedicineReminderRecyclerViewAdapter(mMedicineState,
                this, this, this, mNavigator, this);
        mSuggestionQuery = s ->
                mQueryMedicineCmd.searchMedicineName(s).blockingGet();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_medicine_detail, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSv.buildView(activity, container));
        mSuggestionAdapter = new SuggestionAdapter
                (activity, android.R.layout.select_dialog_item, mSuggestionQuery);
        AutoCompleteTextView inputName = rootLayout.findViewById(R.id.input_text_name);
        inputName.addTextChangedListener(mNameTextWatcher);
        inputName.setThreshold(1);
        inputName.setAdapter(mSuggestionAdapter);
        EditText inputDescription = rootLayout.findViewById(R.id.input_text_description);
        inputDescription.addTextChangedListener(mDescriptionTextWatcher);
        Button addMedicineReminderButton = rootLayout.findViewById(R.id.button_add_medicine_reminder);
        addMedicineReminderButton.setOnClickListener(this);
        RecyclerView medicineReminderRecyclerView = rootLayout.findViewById(R.id.recyclerView_medicine_reminder);
        medicineReminderRecyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        medicineReminderRecyclerView.setAdapter(mMedicineReminderRecyclerViewAdapter);
        mRxDisposer.add("createView_onMedicineChanged",
                mMedicineState.getMedicineFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(medicine -> {
                            inputName.setText(medicine.name);
                            inputDescription.setText(medicine.description);
                        }));
        mRxDisposer.add("createView_onInputNameValidated",
                mNewMedicineCmd.getNameValid()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s != null && !s.isEmpty()) {
                                inputName.setError(s);
                            } else {
                                inputName.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onMedicineReminderListChanged",
                mMedicineState.getMedicineReminderListFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(medicineReminders -> mMedicineReminderRecyclerViewAdapter.notifyItemRefreshed()));
        mRxDisposer.add("createView_onMedicineReminderAdded",
                mMedicineReminderChangeNotifier.getAddedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminder -> {
                            if (isUpdate() && shouldSave()) {
                                mMedicineState.addMedicineReminder(medicineReminder);
                            }
                        }));
        mRxDisposer.add("createView_onMedicineReminderUpdated",
                mMedicineReminderChangeNotifier.getUpdatedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(updateMedicineReminderEvent -> {
                            if (isUpdate() && shouldSave()) {
                                mMedicineState.updateMedicineReminder(updateMedicineReminderEvent.getAfter());
                            }
                        }));
        mRxDisposer.add("createView_onMedicineReminderDeleted",
                mMedicineReminderChangeNotifier.getDeletedMedicineReminder()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineReminder -> {
                            if (isUpdate() && shouldSave()) {
                                mMedicineState.deleteMedicineReminder(medicineReminder);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mAppBarSv != null) {
            mAppBarSv.dispose(activity);
            mAppBarSv = null;
        }
        if (mMedicineReminderRecyclerViewAdapter != null) {
            mMedicineReminderRecyclerViewAdapter.dispose(activity);
            mMedicineReminderRecyclerViewAdapter = null;
        }
        mNameTextWatcher = null;
        mDescriptionTextWatcher = null;
        if (mSuggestionAdapter != null) {
            mSuggestionAdapter.clear();
            mSuggestionAdapter = null;
        }
        mSuggestionQuery = null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            if (shouldSave()) {
                boolean isUpdate = isUpdate();
                Context context = mSvProvider.getContext();
                if (mNewMedicineCmd.valid(mMedicineState)) {
                    mRxDisposer.add("onMenuItemClick_saveNewMedicine",
                            mNewMedicineCmd.execute(mMedicineState)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((medicineState, throwable) -> {
                                        String errorMessage;
                                        String successMessage;
                                        if (isUpdate) {
                                            errorMessage = context.getString(R.string.error_failed_to_update_medicine);
                                            successMessage = context.getString(R.string.success_updating_medicine);
                                        } else {
                                            errorMessage = context.getString(R.string.error_failed_to_add_medicine);
                                            successMessage = context.getString(R.string.success_adding_medicine);
                                        }
                                        if (throwable != null) {
                                            mSvProvider.get(ILogger.class)
                                                    .e(TAG, errorMessage, throwable);
                                        } else {
                                            mSvProvider.get(ILogger.class)
                                                    .i(TAG, successMessage);
                                            mNavigator.pop(Result.with(mMedicineState));
                                        }
                                    }));
                } else {
                    String error = mNewMedicineCmd.getValidationError();
                    mSvProvider.get(ILogger.class).i(TAG, error);
                }
            } else {
                mNavigator.pop(Result.with(mMedicineState));
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_add_medicine_reminder) {
            MedicineReminderDetailPage.Args args;
            if (isUpdate()) {
                args = MedicineReminderDetailPage.Args.save(mMedicineState.getMedicineId());
            } else {
                args = MedicineReminderDetailPage.Args.dontSave();
            }
            mNavigator.push(Routes.MEDICINE_REMINDER_DETAIL_PAGE, args,
                    (navigator, navRoute, activity, currentView) -> {
                        MedicineReminderDetailPage.Result result = MedicineReminderDetailPage.Result.of(navRoute);
                        if (result != null) {
                            addNewMedicineReminder(result.getMedicineReminder());
                        }
                    });
        }
    }

    private void addNewMedicineReminder(MedicineReminder medicineReminder) {
        if (!isUpdate()) {
            mMedicineReminderRecyclerViewAdapter.notifyItemAdded(medicineReminder);
        }
    }

    @Override
    public void onEnableSwitchClick(MedicineReminder medicineReminder) {
        // save only if this is update medicine AND save flag enabled
        if (isUpdate() && shouldSave()) {
            Context context = mSvProvider.getContext();
            UpdateMedicineReminderCmd updateMedicineReminderCmd = mSvProvider.get(UpdateMedicineReminderCmd.class);
            if (updateMedicineReminderCmd.valid(medicineReminder)) {
                mRxDisposer.add("onEnableSwitchClick_saveMedicineReminder",
                        updateMedicineReminderCmd.execute(medicineReminder)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((medicineState, throwable) -> {
                                    String errorMessage = context.getString(R.string.error_failed_to_update_medicine_reminder);
                                    String successMessage = context.getString(R.string.success_updating_medicine_reminder);
                                    if (throwable != null) {
                                        mSvProvider.get(ILogger.class)
                                                .e(TAG, errorMessage, throwable);
                                    } else {
                                        mSvProvider.get(ILogger.class)
                                                .i(TAG, successMessage);
                                    }
                                }));
            } else {
                String error = updateMedicineReminderCmd.getValidationError();
                mSvProvider.get(ILogger.class).i(TAG, error);
            }
            mSvProvider.get(UpdateMedicineReminderCmd.class).execute(medicineReminder);
        } else {
            mMedicineReminderRecyclerViewAdapter.notifyItemUpdated(medicineReminder);
        }
    }

    @Override
    public void onEditClick(MedicineReminder medicineReminder) {
        MedicineReminderDetailPage.Args args;
        if (isUpdate() && shouldSave()) {
            args = MedicineReminderDetailPage.Args.forUpdate(medicineReminder.clone());
        } else {
            args = MedicineReminderDetailPage.Args.forEdit(medicineReminder.clone());
        }
        mNavigator.push(Routes.MEDICINE_REMINDER_DETAIL_PAGE,
                args,
                (navigator, navRoute, activity, currentView) -> {
                    MedicineReminderDetailPage.Result result = MedicineReminderDetailPage.Result.of(navRoute);
                    if (result != null) {
                        updateMedicineReminder(result.getMedicineReminder());
                    }
                });
    }

    private void updateMedicineReminder(MedicineReminder medicineReminder) {
        if (!isUpdate()) {
            mMedicineReminderRecyclerViewAdapter.notifyItemUpdated(medicineReminder);
        }
    }

    @Override
    public void onDeleteClick(MedicineReminder medicineReminder) {
        if (isUpdate() && shouldSave()) {
            Context context = mSvProvider.getContext();
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_delete_medicine_reminder, medicineReminder.message);
            NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
            mNavigator.push(navExtDialogConfig.route_confirmDialog(),
                    navExtDialogConfig.args_confirmDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        Boolean result = provider.get(NavExtDialogConfig.class).result_confirmDialog(navRoute);
                        if (result != null && result) {
                            confirmDeleteMedicineReminder(medicineReminder);
                        }
                    });
        } else {
            mMedicineReminderRecyclerViewAdapter.notifyItemDeleted(medicineReminder);
        }
    }

    private void confirmDeleteMedicineReminder(MedicineReminder medicineReminder) {
        Context context = mSvProvider.getContext();
        mRxDisposer.add("confirmDeleteMedicineReminder_deleteMedicineReminderCmd",
                mSvProvider.get(DeleteMedicineReminderCmd.class)
                        .execute(medicineReminder)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((note, throwable) -> {
                            String errorMessage = context.getString(R.string.error_failed_to_delete_medicine_reminder);
                            String successMessage = context.getString(R.string.success_deleting_medicine_reminder);
                            if (throwable != null) {
                                mSvProvider.get(ILogger.class)
                                        .e(TAG, errorMessage, throwable);
                            } else {
                                mSvProvider.get(ILogger.class)
                                        .i(TAG, successMessage);
                            }
                        })
        );
    }

    private MedicineState getMedicineState() {
        Args args = Args.of(mNavRoute);
        if (args != null && args.mMedicineState != null) {
            return args.mMedicineState;
        }
        return new MedicineState();
    }

    private Long getNoteId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mNoteId;
        }
        return null;
    }


    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.isUpdate();
        }
        return false;
    }

    private boolean shouldSave() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.shouldSave();
        }
        return true;
    }

    private void initTextWatcher() {
        if (mNameTextWatcher == null) {
            mNameTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    String name = editable.toString();
                    mMedicineState.setMedicineName(name);
                    mNewMedicineCmd.valid(mMedicineState);
                }
            };
        }
        if (mDescriptionTextWatcher == null) {
            mDescriptionTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    String desc = editable.toString();
                    mMedicineState.setMedicineDescription(desc);
                }
            };
        }
    }

    static class Result implements Serializable {
        static Result with(MedicineState medicineState) {
            Result result = new Result();
            result.mMedicineState = medicineState;
            return result;
        }

        static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteResult());
            }
            return null;
        }

        static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private MedicineState mMedicineState;

        public MedicineState getMedicineState() {
            return mMedicineState;
        }
    }

    static class Args implements Serializable {
        static Args dontSave() {
            Args args = new Args();
            args.mShouldSave = false;
            return args;
        }

        static Args save(long noteId) {
            Args args = new Args();
            args.mShouldSave = true;
            args.mNoteId = noteId;
            return args;
        }

        static Args forUpdate(MedicineState medicineState) {
            Args args = new Args();
            args.mMedicineState = medicineState;
            return args;
        }

        static Args forEdit(MedicineState medicineState) {
            Args args = new Args();
            args.mMedicineState = medicineState;
            args.mShouldSave = false;
            return args;
        }

        static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private MedicineState mMedicineState;
        private boolean mShouldSave = true;
        private Long mNoteId;

        private boolean isUpdate() {
            return mMedicineState != null;
        }

        private boolean shouldSave() {
            return mShouldSave;
        }
    }
}
