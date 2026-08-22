package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.function.Function;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.command.NewMedicineIntakeCmd;
import m.co.rh.id.a_medic_log.app.provider.command.QueryMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineIntakeCmd;
import m.co.rh.id.a_medic_log.app.rx.RxUtils;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.adapter.SuggestionAdapter;
import m.co.rh.id.a_medic_log.app.util.SimpleTextWatcher;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class MedicineIntakeDetailPage extends BaseDetailPage implements Toolbar.OnMenuItemClickListener, View.OnClickListener {
    private static final String TAG = MedicineIntakeDetailPage.class.getName();
    private transient NewMedicineIntakeCmd mNewMedicineIntakeCmd;
    private transient QueryMedicineCmd mQueryMedicineCmd;

    @NavInject
    private AppBarSV mAppBarSv;

    private SerialBehaviorSubject<MedicineIntake> mMedicineIntakeSubject;
    private DateFormat mDateFormat;

    private transient TextWatcher mTakenDateTimeTextWatcher;
    private transient TextWatcher mDescriptionTextWatcher;
    private transient Function<String, Single<LinkedHashSet<String>>> mSuggestionQuery;

    public MedicineIntakeDetailPage() {
        mAppBarSv = new AppBarSV(R.menu.page_medicine_intake_detail);
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
    }

    @Override
    protected void onProvideComponent(Provider provider) {
        boolean isUpdate = isUpdate();
        if (isUpdate) {
            mNewMedicineIntakeCmd = mSvProvider.get(UpdateMedicineIntakeCmd.class);
        } else {
            mNewMedicineIntakeCmd = mSvProvider.get(NewMedicineIntakeCmd.class);
        }
        mQueryMedicineCmd = mSvProvider.get(QueryMedicineCmd.class);
        if (isUpdate) {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_update_medicine_intake));
        } else {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_add_medicine_intake));
        }
        mAppBarSv.setMenuItemListener(this);
        initTextWatcher();
        mSuggestionQuery = s ->
                mQueryMedicineCmd.searchMedicineIntakeDescription(s);
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        if (isUpdate()) {
            mMedicineIntakeSubject = new SerialBehaviorSubject<>(getMedicineIntake());
        } else {
            MedicineIntake medicineIntake = new MedicineIntake();
            medicineIntake.medicineId = getMedicineId();
            mMedicineIntakeSubject = new SerialBehaviorSubject<>(medicineIntake);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_medicine_intake_detail, container, false);
        rootLayout.post(rootLayout::clearFocus);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSv.buildView(activity, container));
        EditText inputTakenDateTime = rootLayout.findViewById(R.id.input_text_taken_date_time);
        inputTakenDateTime.setOnClickListener(this);
        inputTakenDateTime.addTextChangedListener(mTakenDateTimeTextWatcher);
        AutoCompleteTextView inputDescription = rootLayout.findViewById(R.id.input_text_description);
        inputDescription.addTextChangedListener(mDescriptionTextWatcher);
        inputDescription.setThreshold(1);
        inputDescription.setAdapter(new SuggestionAdapter
                (activity, android.R.layout.select_dialog_item, mSuggestionQuery));
        mRxDisposer.add("MedicineIntakeDetailPage.createView_onMedicineIntakeUpdated",
                mMedicineIntakeSubject.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(medicineReminder -> {
                            if (medicineReminder.takenDateTime != null) {
                                inputTakenDateTime.setText(mDateFormat.format(medicineReminder.takenDateTime));
                            } else {
                                inputTakenDateTime.setText(null);
                            }
                            inputDescription.setText(medicineReminder.description);
                        }));
        mRxDisposer.add("MedicineIntakeDetailPage.createView_onTakenDateTimeValidated",
                mNewMedicineIntakeCmd.getTakenDateTimeValid()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s != null && !s.isEmpty()) {
                                inputDescription.setError(s);
                            } else {
                                inputDescription.setError(null);
                            }
                        }));
        mRxDisposer.add("MedicineIntakeDetailPage.createView_onInputDescriptionValidated",
                mNewMedicineIntakeCmd.getDescriptionValid()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s != null && !s.isEmpty()) {
                                inputDescription.setError(s);
                            } else {
                                inputDescription.setError(null);
                            }
                        }));
        return rootLayout;
    }

    @Override
    protected void onPageDispose(Activity activity) {
        if (mAppBarSv != null) {
            mAppBarSv.dispose(activity);
            mAppBarSv = null;
        }
        mTakenDateTimeTextWatcher = null;
        mDescriptionTextWatcher = null;
        mSuggestionQuery = null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            boolean isUpdate = isUpdate();
            Context context = mSvProvider.getContext();
            MedicineIntake medicineIntake = mMedicineIntakeSubject.getValue();
            if (mNewMedicineIntakeCmd.valid(medicineIntake)) {
                String successMessage;
                if (isUpdate) {
                    successMessage = context.getString(R.string.success_updating_medicine_intake);
                } else {
                    successMessage = context.getString(R.string.success_adding_medicine_intake);
                }
                RxUtils.executeAndLog(mRxDisposer, "MedicineIntakeDetailPage.onMenuItemClick_saveMedicineIntake",
                        mNewMedicineIntakeCmd.execute(medicineIntake), mLogger, TAG, successMessage,
                        medicineIntake1 -> mNavigator.pop(Result.with(medicineIntake)));
            } else {
                String error = mNewMedicineIntakeCmd.getValidationError();
                mLogger.i(TAG, error);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.input_text_taken_date_time) {
            NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
            mNavigator.push(navExtDialogConfig.route_dateTimePickerDialog(),
                    navExtDialogConfig.args_dateTimePickerDialog(true, mMedicineIntakeSubject.getValue().takenDateTime),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        NavExtDialogConfig navExtDialogConfig1 = provider.get(NavExtDialogConfig.class);
                        Date result = navExtDialogConfig1.result_dateTimePickerDialog(navRoute);
                        if (result != null) {
                            updateTakenDateTime(result);
                        }
                    });
        }
    }

    private void updateTakenDateTime(Date date) {
        MedicineIntake medicineIntake = mMedicineIntakeSubject.getValue();
        medicineIntake.takenDateTime = date;
        mMedicineIntakeSubject.onNext(medicineIntake);
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.isUpdate();
        }
        return false;
    }

    private Long getMedicineId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mMedicineId;
        }
        return null;
    }

    private MedicineIntake getMedicineIntake() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mMedicineIntake;
        }
        return null;
    }

    private void initTextWatcher() {
        if (mTakenDateTimeTextWatcher == null) {
            mTakenDateTimeTextWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    MedicineIntake medicineIntake = mMedicineIntakeSubject.getValue();
                    mNewMedicineIntakeCmd.valid(medicineIntake);
                }
            };
        }
        if (mDescriptionTextWatcher == null) {
            mDescriptionTextWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    String description = editable.toString();
                    MedicineIntake medicineIntake = mMedicineIntakeSubject.getValue();
                    medicineIntake.description = description;
                    mNewMedicineIntakeCmd.valid(medicineIntake);
                }
            };
        }
    }

    static class Result implements Serializable {
        static Result with(MedicineIntake medicineIntake) {
            Result result = new Result();
            result.mMedicineIntake = medicineIntake;
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

        private MedicineIntake mMedicineIntake;

        public MedicineIntake getMedicineIntake() {
            return mMedicineIntake;
        }
    }

    public static class Args implements Serializable {
        public static Args with(long medicineId) {
            Args args = new Args();
            args.mMedicineId = medicineId;
            return args;
        }

        public static Args forUpdate(MedicineIntake medicineIntake) {
            Args args = new Args();
            args.mMedicineIntake = medicineIntake;
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

        private Long mMedicineId;
        private MedicineIntake mMedicineIntake;

        private boolean isUpdate() {
            return mMedicineIntake != null;
        }
    }
}
