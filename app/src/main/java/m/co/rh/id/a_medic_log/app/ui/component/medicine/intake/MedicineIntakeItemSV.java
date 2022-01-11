package m.co.rh.id.a_medic_log.app.ui.component.medicine.intake;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.base.entity.MedicineIntake;
import m.co.rh.id.a_medic_log.base.rx.SerialBehaviorSubject;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class MedicineIntakeItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private transient Provider mSvProvider;
    private transient OnEditClick mOnEditClick;
    private transient OnDeleteClick mOnDeleteClick;
    private SerialBehaviorSubject<MedicineIntake> mMedicineIntakeSubject;
    private DateFormat mDateFormat;

    public MedicineIntakeItemSV() {
        mMedicineIntakeSubject = new SerialBehaviorSubject<>(new MedicineIntake());
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_medicine_intake, container, false);
        rootLayout.findViewById(R.id.root_layout).setOnClickListener(this);
        TextView takenDateText = rootLayout.findViewById(R.id.text_taken_date_time);
        TextView descriptionText = rootLayout.findViewById(R.id.text_description);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        mSvProvider.get(RxDisposer.class)
                .add("createView_onMedicineIntakeChanged",
                        mMedicineIntakeSubject.getSubject()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(medicineIntake -> {
                                    takenDateText.setText(mDateFormat.format(medicineIntake.takenDateTime));
                                    descriptionText.setText(medicineIntake.description);
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
        mOnEditClick = null;
        mOnDeleteClick = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        MedicineIntake medicineIntake = mMedicineIntakeSubject.getValue();
        if (id == R.id.button_edit) {
            if (mOnEditClick != null) {
                mOnEditClick.onEditClick(medicineIntake);
            }
        } else if (id == R.id.button_delete) {
            if (mOnDeleteClick != null) {
                mOnDeleteClick.onDeleteClick(medicineIntake);
            }
        }
    }

    public void setOnEditClick(OnEditClick onEditClick) {
        mOnEditClick = onEditClick;
    }

    public void setOnDeleteClick(OnDeleteClick onDeleteClick) {
        mOnDeleteClick = onDeleteClick;
    }

    public void setMedicineIntake(MedicineIntake medicineIntake) {
        mMedicineIntakeSubject.onNext(medicineIntake);
    }

    public MedicineIntake getMedicineIntake() {
        return mMedicineIntakeSubject.getValue();
    }

    public interface OnEditClick {
        void onEditClick(MedicineIntake medicineIntake);
    }

    public interface OnDeleteClick {
        void onDeleteClick(MedicineIntake medicineIntake);
    }
}
