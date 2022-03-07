package m.co.rh.id.a_medic_log.app.ui.component.medicine.reminder;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.base.entity.MedicineReminder;
import m.co.rh.id.a_medic_log.base.rx.SerialBehaviorSubject;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class MedicineReminderItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private transient Provider mSvProvider;
    private transient OnEnableSwitchClick mOnEnableSwitchClick;
    private transient OnEditClick mOnEditClick;
    private transient OnDeleteClick mOnDeleteClick;
    private SerialBehaviorSubject<MedicineReminder> mMedicineReminderSubject;
    private DateFormat mDateFormat;

    public MedicineReminderItemSV() {
        mMedicineReminderSubject = new SerialBehaviorSubject<>(new MedicineReminder());
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_medicine_reminder, container, false);
        rootLayout.findViewById(R.id.root_layout).setOnClickListener(this);
        TextView startDateText = rootLayout.findViewById(R.id.text_start_date);
        TextView messageText = rootLayout.findViewById(R.id.text_message);
        SwitchCompat enabledButton = rootLayout.findViewById(R.id.button_enabled);
        enabledButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        mSvProvider.get(RxDisposer.class)
                .add("createView_onMedicineReminderChanged",
                        mMedicineReminderSubject.getSubject()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(medicineReminder -> {
                                    startDateText.setText(mDateFormat.format(medicineReminder.startDateTime));
                                    messageText.setText(medicineReminder.message);
                                    enabledButton.setChecked(medicineReminder.reminderEnabled);
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
        mOnEnableSwitchClick = null;
        mOnEditClick = null;
        mOnDeleteClick = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        MedicineReminder medicineReminder = mMedicineReminderSubject.getValue();
        if (id == R.id.root_layout) {
            if (mOnEditClick != null) {
                mOnEditClick.onEditClick(medicineReminder);
            }
        } else if (id == R.id.button_enabled) {
            medicineReminder.reminderEnabled = !medicineReminder.reminderEnabled;
            if (mOnEnableSwitchClick != null) {
                mOnEnableSwitchClick.onEnableSwitchClick(medicineReminder);
            }
        } else if (id == R.id.button_delete) {
            if (mOnDeleteClick != null) {
                mOnDeleteClick.onDeleteClick(medicineReminder);
            }
        }
    }

    public void setOnEnableSwitchClick(OnEnableSwitchClick onEnableSwitchClick) {
        mOnEnableSwitchClick = onEnableSwitchClick;
    }

    public void setOnEditClick(OnEditClick onEditClick) {
        mOnEditClick = onEditClick;
    }

    public void setOnDeleteClick(OnDeleteClick onDeleteClick) {
        mOnDeleteClick = onDeleteClick;
    }

    public void setMedicineReminder(MedicineReminder medicineReminder) {
        mMedicineReminderSubject.onNext(medicineReminder);
    }

    public MedicineReminder getMedicineReminder() {
        return mMedicineReminderSubject.getValue();
    }

    public interface OnEnableSwitchClick {
        void onEnableSwitchClick(MedicineReminder medicineReminder);
    }

    public interface OnEditClick {
        void onEditClick(MedicineReminder medicineReminder);
    }

    public interface OnDeleteClick {
        void onDeleteClick(MedicineReminder medicineReminder);
    }
}
