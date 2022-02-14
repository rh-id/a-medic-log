package m.co.rh.id.a_medic_log.app.ui.component.medicine;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.gridlayout.widget.GridLayout;

import com.google.android.material.textview.MaterialTextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.provider.command.QueryMedicineCmd;
import m.co.rh.id.a_medic_log.app.provider.notifier.MedicineIntakeChangeNotifier;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.rx.OptionalBehaviorSubject;
import m.co.rh.id.a_medic_log.base.rx.SerialBehaviorSubject;
import m.co.rh.id.a_medic_log.base.state.MedicineState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class MedicineItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener, PopupMenu.OnMenuItemClickListener {

    private transient ExecutorService mExecutorService;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient MedicineIntakeChangeNotifier mMedicineIntakeChangeNotifier;
    private transient QueryMedicineCmd mQueryMedicineCmd;
    private SerialBehaviorSubject<MedicineState> mMedicineStateSubject;
    private OptionalBehaviorSubject<MedicineIntake> mLastMedicineIntakeSubject;
    private DateFormat mDateTimeFormat;
    private DateFormat mTimeFormat;
    private transient OnMedicineIntakeListClick mOnMedicineIntakeListClick;
    private transient OnEditClick mOnEditClick;
    private transient OnDeleteClick mOnDeleteClick;
    private transient OnAddMedicineIntakeClick mOnAddMedicineIntakeClick;

    public MedicineItemSV() {
        mMedicineStateSubject = new SerialBehaviorSubject<>(new MedicineState());
        mLastMedicineIntakeSubject = new OptionalBehaviorSubject<>();
        mDateTimeFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
        mTimeFormat = new SimpleDateFormat("HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mMedicineIntakeChangeNotifier = mSvProvider.get(MedicineIntakeChangeNotifier.class);
        mQueryMedicineCmd = mSvProvider.get(QueryMedicineCmd.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_medicine, container, false);
        rootLayout.setOnClickListener(this);
        TextView nameText = rootLayout.findViewById(R.id.text_name);
        TextView descriptionText = rootLayout.findViewById(R.id.text_description);
        TextView lastIntakeText = rootLayout.findViewById(R.id.text_last_intake);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        Button moreAction = rootLayout.findViewById(R.id.button_more_action);
        moreAction.setOnClickListener(this);
        GridLayout containerReminder = rootLayout.findViewById(R.id.container_reminder);
        mRxDisposer
                .add("createView_onMedicineStateChanged",
                        mMedicineStateSubject.getSubject()
                                .debounce(16, TimeUnit.MILLISECONDS)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(medicineState -> {
                                    nameText.setText(medicineState.getMedicineName());
                                    descriptionText.setText(medicineState.getMedicineDescription());
                                    if (medicineState.getMedicineId() != null) {
                                        moreAction.setVisibility(View.VISIBLE);
                                    } else {
                                        moreAction.setVisibility(View.GONE);
                                    }
                                    queryLastMedicineIntake(medicineState.getMedicineId());
                                    containerReminder.removeAllViews();
                                    ArrayList<MedicineReminder> medicineReminders = medicineState.getMedicineReminderList();
                                    if (medicineReminders != null && !medicineReminders.isEmpty()) {
                                        containerReminder.setVisibility(View.VISIBLE);
                                        for (MedicineReminder medicineReminder : medicineReminders
                                        ) {
                                            MaterialTextView materialTextView = new MaterialTextView(activity);
                                            materialTextView.setText(mTimeFormat.format(medicineReminder.startDateTime));
                                            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                                            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
                                            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                                            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1.0f);
                                            materialTextView.setLayoutParams(params);
                                            materialTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                                            int tintColor = activity.getResources().getColor(R.color.daynight_black_white);
                                            Drawable icon;
                                            if (medicineReminder.reminderEnabled) {
                                                icon = AppCompatResources.getDrawable(activity, R.drawable.ic_timer_black);
                                            } else {
                                                icon = AppCompatResources.getDrawable(activity, R.drawable.ic_timer_off_black);
                                            }
                                            icon = DrawableCompat.wrap(icon);
                                            DrawableCompat.setTint(icon, tintColor);
                                            materialTextView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                                            containerReminder.addView(materialTextView);
                                        }
                                    } else {
                                        containerReminder.setVisibility(View.GONE);
                                    }
                                }));
        mRxDisposer.add("createView_onLastMedicineIntakeChanged",
                mLastMedicineIntakeSubject.getSubject()
                        .debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(medicineIntakeOpt -> {
                            if (medicineIntakeOpt.isPresent()) {
                                Date takenDateTime = medicineIntakeOpt.get().takenDateTime;
                                lastIntakeText.setText(mDateTimeFormat.format(takenDateTime));
                                lastIntakeText.setVisibility(View.VISIBLE);
                            } else {
                                lastIntakeText.setText(null);
                                lastIntakeText.setVisibility(View.GONE);
                            }
                        }));
        mRxDisposer.add("createView_onMedicineIntakeAdded",
                mMedicineIntakeChangeNotifier.getAddedMedicineIntake()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineIntake -> {
                            if (medicineIntake.medicineId != null &&
                                    medicineIntake.medicineId.equals(
                                            mMedicineStateSubject.getValue().getMedicineId())) {
                                queryLastMedicineIntake(medicineIntake.medicineId);
                            }
                        }));
        mRxDisposer.add("createView_onMedicineIntakeUpdated",
                mMedicineIntakeChangeNotifier.getUpdatedMedicineIntake()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(updateMedicineIntakeEvent -> {
                            MedicineIntake medicineIntake = updateMedicineIntakeEvent.getAfter();
                            if (medicineIntake.medicineId != null &&
                                    medicineIntake.medicineId.equals(
                                            mMedicineStateSubject.getValue().getMedicineId())) {
                                queryLastMedicineIntake(medicineIntake.medicineId);
                            }
                        }));
        mRxDisposer.add("createView_onMedicineIntakeDeleted",
                mMedicineIntakeChangeNotifier.getDeletedMedicineIntake()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(medicineIntake -> {
                            if (medicineIntake.medicineId != null &&
                                    medicineIntake.medicineId.equals(
                                            mMedicineStateSubject.getValue().getMedicineId())) {
                                queryLastMedicineIntake(medicineIntake.medicineId);
                            }
                        }));
        return rootLayout;
    }

    private void queryLastMedicineIntake(Long medicineId) {
        if (medicineId != null) {
            mRxDisposer.add("queryLastMedicineIntake_lastMedicineIntake",
                    mQueryMedicineCmd.lastMedicineIntake(medicineId)
                            .observeOn(Schedulers.from(mExecutorService))
                            .subscribe((medicineIntake, throwable) -> {
                                if (throwable == null) {
                                    mLastMedicineIntakeSubject.onNext(medicineIntake);
                                }
                            })
            );
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mOnEditClick = null;
        mOnDeleteClick = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.root_layout) {
            if (mOnMedicineIntakeListClick != null) {
                mOnMedicineIntakeListClick.onMedicineIntakeListClick(mMedicineStateSubject.getValue());
            }
        } else if (id == R.id.button_edit) {
            if (mOnEditClick != null) {
                mOnEditClick.onEditClick(mMedicineStateSubject.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mOnDeleteClick != null) {
                mOnDeleteClick.onDeleteClick(mMedicineStateSubject.getValue());
            }
        } else if (id == R.id.button_more_action) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.medicine_item_more_action, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add_medicine_intake) {
            if (mOnAddMedicineIntakeClick != null) {
                mOnAddMedicineIntakeClick.onAddMedicineIntakeClick(mMedicineStateSubject.getValue());
            }
        }
        return false;
    }

    public void setOnEditClick(OnEditClick onEditClick) {
        mOnEditClick = onEditClick;
    }

    public void setOnDeleteClick(OnDeleteClick onDeleteClick) {
        mOnDeleteClick = onDeleteClick;
    }

    public void setMedicineState(MedicineState medicineState) {
        mMedicineStateSubject.onNext(medicineState);
    }

    public MedicineState getMedicineState() {
        return mMedicineStateSubject.getValue();
    }

    public void setOnAddMedicineIntakeClick(OnAddMedicineIntakeClick onAddMedicineIntakeClick) {
        mOnAddMedicineIntakeClick = onAddMedicineIntakeClick;
    }

    public void setOnMedicineIntakeListClick(OnMedicineIntakeListClick onMedicineIntakeListClick) {
        mOnMedicineIntakeListClick = onMedicineIntakeListClick;
    }

    public interface OnMedicineIntakeListClick {
        void onMedicineIntakeListClick(MedicineState medicineState);
    }

    public interface OnEditClick {
        void onEditClick(MedicineState medicineState);
    }

    public interface OnDeleteClick {
        void onDeleteClick(MedicineState medicineState);
    }

    public interface OnAddMedicineIntakeClick {
        void onAddMedicineIntakeClick(MedicineState medicineState);
    }
}
