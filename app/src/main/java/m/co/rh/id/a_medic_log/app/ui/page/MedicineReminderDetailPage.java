package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.NewMedicineReminderCmd;
import m.co.rh.id.a_medic_log.app.provider.command.UpdateMedicineReminderCmd;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.rx.SerialBehaviorSubject;
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

public class MedicineReminderDetailPage extends StatefulView<Activity> implements RequireNavigator, RequireNavRoute, RequireComponent<Provider>, Toolbar.OnMenuItemClickListener, View.OnClickListener {
    private static final String TAG = MedicineReminderDetailPage.class.getName();
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient NewMedicineReminderCmd mNewMedicineReminderCmd;

    @NavInject
    private AppBarSV mAppBarSv;

    private SerialBehaviorSubject<MedicineReminder> mMedicineReminderSubject;
    private SerialBehaviorSubject<LinkedHashSet<Integer>> mReminderDaysSubject;
    private DateFormat mDateFormat;

    private transient TextWatcher mStartDateTimeTextWatcher;
    private transient TextWatcher mMessageTextWatcher;

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
        mSvProvider = provider.get(StatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        if (isUpdate) {
            mNewMedicineReminderCmd = mSvProvider.get(UpdateMedicineReminderCmd.class);
        } else {
            mNewMedicineReminderCmd = mSvProvider.get(NewMedicineReminderCmd.class);
        }
        if (mAppBarSv == null) {
            mAppBarSv = new AppBarSV(R.menu.page_medicine_reminder_detail);
        }
        if (isUpdate) {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_update_medicine_reminder));
        } else {
            mAppBarSv.setTitle(mNavigator.getActivity()
                    .getString(R.string.title_add_medicine_reminder));
        }
        mAppBarSv.setMenuItemListener(this);
        initTextWatcher();
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
        MedicineReminder medicineReminder = getMedicineReminder();
        mMedicineReminderSubject = new SerialBehaviorSubject<>(medicineReminder);
        mReminderDaysSubject = new SerialBehaviorSubject<>(medicineReminder.reminderDays);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_medicine_reminder_detail, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSv.buildView(activity, container));
        EditText inputStartDateTime = rootLayout.findViewById(R.id.input_text_start_date_time);
        inputStartDateTime.setOnClickListener(this);
        inputStartDateTime.addTextChangedListener(mStartDateTimeTextWatcher);
        EditText inputMessage = rootLayout.findViewById(R.id.input_text_message);
        inputMessage.addTextChangedListener(mMessageTextWatcher);
        TextView reminderDaysText = rootLayout.findViewById(R.id.text_reminder_days);
        Button reminderDaysMon = rootLayout.findViewById(R.id.reminder_days_mon);
        reminderDaysMon.setOnClickListener(this);
        Button reminderDaysTue = rootLayout.findViewById(R.id.reminder_days_tue);
        reminderDaysTue.setOnClickListener(this);
        Button reminderDaysWed = rootLayout.findViewById(R.id.reminder_days_wed);
        reminderDaysWed.setOnClickListener(this);
        Button reminderDaysThu = rootLayout.findViewById(R.id.reminder_days_thu);
        reminderDaysThu.setOnClickListener(this);
        Button reminderDaysFri = rootLayout.findViewById(R.id.reminder_days_fri);
        reminderDaysFri.setOnClickListener(this);
        Button reminderDaysSat = rootLayout.findViewById(R.id.reminder_days_sat);
        reminderDaysSat.setOnClickListener(this);
        Button reminderDaysSun = rootLayout.findViewById(R.id.reminder_days_sun);
        reminderDaysSun.setOnClickListener(this);
        mRxDisposer.add("createView_onMedicineReminderUpdated",
                mMedicineReminderSubject.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(medicineReminder -> {
                            if (medicineReminder.startDateTime != null) {
                                inputStartDateTime.setText(mDateFormat.format(medicineReminder.startDateTime));
                            } else {
                                inputStartDateTime.setText(null);
                            }
                            inputMessage.setText(medicineReminder.message);
                        }));
        mRxDisposer.add("createView_onStartDateTimeValidated",
                mNewMedicineReminderCmd.getStartDateTimeValid()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s != null && !s.isEmpty()) {
                                inputMessage.setError(s);
                            } else {
                                inputMessage.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onInputMessageValidated",
                mNewMedicineReminderCmd.getMessageValid()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s != null && !s.isEmpty()) {
                                inputMessage.setError(s);
                            } else {
                                inputMessage.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onReminderDaysValidated",
                mNewMedicineReminderCmd.getReminderDaysValid()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s != null && !s.isEmpty()) {
                                reminderDaysText.setError(s);
                            } else {
                                reminderDaysText.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onReminderDaysChanged",
                mReminderDaysSubject.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(integers -> {
                            if (!integers.isEmpty()) {
                                reminderDaysMon.setActivated(integers.contains(Calendar.MONDAY));
                                reminderDaysTue.setActivated(integers.contains(Calendar.TUESDAY));
                                reminderDaysWed.setActivated(integers.contains(Calendar.WEDNESDAY));
                                reminderDaysThu.setActivated(integers.contains(Calendar.THURSDAY));
                                reminderDaysFri.setActivated(integers.contains(Calendar.FRIDAY));
                                reminderDaysSat.setActivated(integers.contains(Calendar.SATURDAY));
                                reminderDaysSun.setActivated(integers.contains(Calendar.SUNDAY));
                            } else {
                                reminderDaysMon.setActivated(false);
                                reminderDaysTue.setActivated(false);
                                reminderDaysWed.setActivated(false);
                                reminderDaysThu.setActivated(false);
                                reminderDaysFri.setActivated(false);
                                reminderDaysSat.setActivated(false);
                                reminderDaysSun.setActivated(false);
                            }
                            mNewMedicineReminderCmd.valid(mMedicineReminderSubject.getValue());
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
        mStartDateTimeTextWatcher = null;
        mMessageTextWatcher = null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            MedicineReminder medicineReminder = mMedicineReminderSubject.getValue();
            if (shouldSave()) {
                boolean isUpdate = isUpdate();
                Context context = mSvProvider.getContext();
                if (mNewMedicineReminderCmd.valid(medicineReminder)) {
                    mRxDisposer.add("onMenuItemClick_saveNewMedicineReminder",
                            mNewMedicineReminderCmd.execute(medicineReminder)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((medicineState, throwable) -> {
                                        String successMessage;
                                        if (isUpdate) {
                                            successMessage = context.getString(R.string.success_updating_medicine_reminder);
                                        } else {
                                            successMessage = context.getString(R.string.success_adding_medicine_reminder);
                                        }
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
                                            mNavigator.pop(Result.with(medicineReminder));
                                        }
                                    }));
                } else {
                    String error = mNewMedicineReminderCmd.getValidationError();
                    mLogger.i(TAG, error);
                }
            } else {
                if (mNewMedicineReminderCmd.valid(medicineReminder)) {
                    mNavigator.pop(Result.with(medicineReminder));
                } else {
                    String error = mNewMedicineReminderCmd.getValidationError();
                    mLogger.i(TAG, error);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.input_text_start_date_time) {
            NavExtDialogConfig navExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
            mNavigator.push(navExtDialogConfig.route_dateTimePickerDialog(),
                    navExtDialogConfig.args_dateTimePickerDialog(true, mMedicineReminderSubject.getValue().startDateTime),
                    (navigator, navRoute, activity, currentView) -> {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        NavExtDialogConfig navExtDialogConfig1 = provider.get(NavExtDialogConfig.class);
                        Date result = navExtDialogConfig1.result_dateTimePickerDialog(navRoute);
                        if (result != null) {
                            updateStartDateTime(result);
                        }
                    });
        } else if (id == R.id.reminder_days_mon) {
            LinkedHashSet<Integer> reminderDays = mReminderDaysSubject.getValue();
            if (!reminderDays.add(Calendar.MONDAY)) {
                reminderDays.remove(Calendar.MONDAY);
            }
            mReminderDaysSubject.onNext(reminderDays);
        } else if (id == R.id.reminder_days_tue) {
            LinkedHashSet<Integer> reminderDays = mReminderDaysSubject.getValue();
            if (!reminderDays.add(Calendar.TUESDAY)) {
                reminderDays.remove(Calendar.TUESDAY);
            }
            mReminderDaysSubject.onNext(reminderDays);
        } else if (id == R.id.reminder_days_wed) {
            LinkedHashSet<Integer> reminderDays = mReminderDaysSubject.getValue();
            if (!reminderDays.add(Calendar.WEDNESDAY)) {
                reminderDays.remove(Calendar.WEDNESDAY);
            }
            mReminderDaysSubject.onNext(reminderDays);
        } else if (id == R.id.reminder_days_thu) {
            LinkedHashSet<Integer> reminderDays = mReminderDaysSubject.getValue();
            if (!reminderDays.add(Calendar.THURSDAY)) {
                reminderDays.remove(Calendar.THURSDAY);
            }
            mReminderDaysSubject.onNext(reminderDays);
        } else if (id == R.id.reminder_days_fri) {
            LinkedHashSet<Integer> reminderDays = mReminderDaysSubject.getValue();
            if (!reminderDays.add(Calendar.FRIDAY)) {
                reminderDays.remove(Calendar.FRIDAY);
            }
            mReminderDaysSubject.onNext(reminderDays);
        } else if (id == R.id.reminder_days_sat) {
            LinkedHashSet<Integer> reminderDays = mReminderDaysSubject.getValue();
            if (!reminderDays.add(Calendar.SATURDAY)) {
                reminderDays.remove(Calendar.SATURDAY);
            }
            mReminderDaysSubject.onNext(reminderDays);
        } else if (id == R.id.reminder_days_sun) {
            LinkedHashSet<Integer> reminderDays = mReminderDaysSubject.getValue();
            if (!reminderDays.add(Calendar.SUNDAY)) {
                reminderDays.remove(Calendar.SUNDAY);
            }
            mReminderDaysSubject.onNext(reminderDays);
        }
    }

    private void updateStartDateTime(Date date) {
        MedicineReminder medicineReminder = mMedicineReminderSubject.getValue();
        medicineReminder.startDateTime = date;
        mMedicineReminderSubject.onNext(medicineReminder);
    }

    private MedicineReminder getMedicineReminder() {
        MedicineReminder medicineReminder;
        Args args = Args.of(mNavRoute);
        if (args != null && args.mMedicineReminder != null) {
            medicineReminder = args.mMedicineReminder;
        } else {
            medicineReminder = new MedicineReminder();
            medicineReminder.medicineId = getMedicineId();
        }
        return medicineReminder;
    }

    private Long getMedicineId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mMedicineId;
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
        if (mStartDateTimeTextWatcher == null) {
            mStartDateTimeTextWatcher = new TextWatcher() {
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
                    MedicineReminder medicineReminder = mMedicineReminderSubject.getValue();
                    mNewMedicineReminderCmd.valid(medicineReminder);
                }
            };
        }
        if (mMessageTextWatcher == null) {
            mMessageTextWatcher = new TextWatcher() {
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
                    String message = editable.toString();
                    MedicineReminder medicineReminder = mMedicineReminderSubject.getValue();
                    medicineReminder.message = message;
                    mNewMedicineReminderCmd.valid(medicineReminder);
                }
            };
        }
    }

    static class Result implements Serializable {
        static Result with(MedicineReminder medicineReminder) {
            Result result = new Result();
            result.mMedicineReminder = medicineReminder;
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

        private MedicineReminder mMedicineReminder;

        public MedicineReminder getMedicineReminder() {
            return mMedicineReminder;
        }
    }

    static class Args implements Serializable {
        static Args dontSave() {
            Args args = new Args();
            args.mShouldSave = false;
            return args;
        }

        static Args save(long medicineId) {
            Args args = new Args();
            args.mShouldSave = true;
            args.mMedicineId = medicineId;
            return args;
        }

        static Args forUpdate(MedicineReminder medicineReminder) {
            Args args = new Args();
            args.mMedicineReminder = medicineReminder;
            return args;
        }

        static Args forEdit(MedicineReminder medicineReminder) {
            Args args = new Args();
            args.mMedicineReminder = medicineReminder;
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

        private MedicineReminder mMedicineReminder;
        private boolean mShouldSave = true;
        private Long mMedicineId;

        private boolean isUpdate() {
            return mMedicineReminder != null;
        }

        private boolean shouldSave() {
            return mShouldSave;
        }
    }
}
