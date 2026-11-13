package m.co.rh.id.a_medic_log.app.ui.component.medicine;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.model.MedicineRow;
import m.co.rh.id.a_medic_log.app.provider.StatefulViewProvider;
import m.co.rh.id.a_medic_log.app.rx.RxDisposer;
import m.co.rh.id.a_medic_log.app.ui.page.NoteDetailPage;
import m.co.rh.id.a_medic_log.base.entity.Medicine;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Row item of the medicines page, it renders the aggregated
 * {@link MedicineRow} as-is (name, last intake, reminder on/off summary and
 * the 7-day adherence line) and never queries the database itself.
 */
public class MedicinesItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener, PopupMenu.OnMenuItemClickListener {

    private static final String TAG = MedicinesItemSV.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private SerialBehaviorSubject<MedicineRow> mMedicineRowSubject;
    private DateFormat mDateTimeFormat;
    private transient MedicinesOnRowClick mMedicinesOnRowClick;
    private transient MedicinesOnDeleteClick mMedicinesOnDeleteClick;
    private transient MedicinesOnIntakeHistoryClick mMedicinesOnIntakeHistoryClick;

    public MedicinesItemSV() {
        MedicineRow defaultRow = new MedicineRow();
        defaultRow.medicine = new Medicine();
        defaultRow.medicineIds = new ArrayList<>();
        mMedicineRowSubject = new SerialBehaviorSubject<>(defaultRow);
        mDateTimeFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_medicines, container, false);
        rootLayout.setOnClickListener(this);
        TextView nameText = rootLayout.findViewById(R.id.text_name);
        TextView descriptionText = rootLayout.findViewById(R.id.text_description);
        TextView lastIntakeText = rootLayout.findViewById(R.id.text_last_intake);
        TextView remindersSummaryText = rootLayout.findViewById(R.id.text_reminders_summary);
        TextView adherenceText = rootLayout.findViewById(R.id.text_adherence);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        Button moreAction = rootLayout.findViewById(R.id.button_more_action);
        moreAction.setOnClickListener(this);
        mRxDisposer
                .add("createView_onMedicineRowChanged",
                        mMedicineRowSubject.getSubject()
                                .debounce(16, TimeUnit.MILLISECONDS)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(medicineRow -> {
                                    nameText.setText(medicineRow.medicine.name);
                                    if (medicineRow.medicine.description == null
                                            || medicineRow.medicine.description.isEmpty()) {
                                        descriptionText.setText(null);
                                        descriptionText.setVisibility(View.GONE);
                                    } else {
                                        descriptionText.setText(medicineRow.medicine.description);
                                        descriptionText.setVisibility(View.VISIBLE);
                                    }
                                    Date lastIntakeDateTime = medicineRow.lastIntakeDateTime;
                                    if (lastIntakeDateTime != null) {
                                        lastIntakeText.setText(mDateTimeFormat.format(lastIntakeDateTime));
                                        lastIntakeText.setVisibility(View.VISIBLE);
                                    } else {
                                        lastIntakeText.setText(null);
                                        lastIntakeText.setVisibility(View.GONE);
                                    }
                                    int reminderCount = medicineRow.remindersOn + medicineRow.remindersOff;
                                    if (reminderCount > 0) {
                                        remindersSummaryText.setText(activity.getString(
                                                R.string.medicines_reminders_format,
                                                medicineRow.remindersOn, medicineRow.remindersOff));
                                        remindersSummaryText.setVisibility(View.VISIBLE);
                                    } else {
                                        remindersSummaryText.setText(null);
                                        remindersSummaryText.setVisibility(View.GONE);
                                    }
                                    if (medicineRow.takenCount + medicineRow.missedCount > 0) {
                                        adherenceText.setText(activity.getString(
                                                R.string.medicines_adherence_format,
                                                medicineRow.adherencePercent,
                                                activity.getString(R.string.adherence_taken),
                                                medicineRow.takenCount,
                                                activity.getString(R.string.adherence_missed),
                                                medicineRow.missedCount));
                                    } else {
                                        adherenceText.setText(
                                                R.string.medicines_adherence_no_data);
                                    }
                                    adherenceText.setVisibility(View.VISIBLE);
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
        mMedicinesOnRowClick = null;
        mMedicinesOnDeleteClick = null;
        mMedicinesOnIntakeHistoryClick = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.root_layout) {
            if (mMedicinesOnRowClick != null) {
                mMedicinesOnRowClick.medicines_onRowClick(mMedicineRowSubject.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mMedicinesOnDeleteClick != null) {
                mMedicinesOnDeleteClick.medicines_onDeleteClick(mMedicineRowSubject.getValue());
            }
        } else if (id == R.id.button_more_action) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.menu_medicines_item, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_view_intake_history) {
            if (mMedicinesOnIntakeHistoryClick != null) {
                mMedicinesOnIntakeHistoryClick.medicines_onIntakeHistoryClick(mMedicineRowSubject.getValue());
            }
            return true;
        } else if (id == R.id.menu_view_note) {
            Long noteId = mMedicineRowSubject.getValue().medicine.noteId;
            if (noteId != null) {
                mNavigator.push(Routes.NOTE_DETAIL_PAGE,
                        NoteDetailPage.Args.forUpdate(noteId));
            } else {
                mLogger.e(TAG, "Medicine " + mMedicineRowSubject.getValue().medicine.id
                        + " has no note id");
            }
            return true;
        }
        return false;
    }

    public void setMedicineRow(MedicineRow medicineRow) {
        mMedicineRowSubject.onNext(medicineRow);
    }

    public MedicineRow getMedicineRow() {
        return mMedicineRowSubject.getValue();
    }

    public void setOnRowClick(MedicinesOnRowClick medicinesOnRowClick) {
        mMedicinesOnRowClick = medicinesOnRowClick;
    }

    public void setOnDeleteClick(MedicinesOnDeleteClick medicinesOnDeleteClick) {
        mMedicinesOnDeleteClick = medicinesOnDeleteClick;
    }

    public void setOnIntakeHistoryClick(MedicinesOnIntakeHistoryClick medicinesOnIntakeHistoryClick) {
        mMedicinesOnIntakeHistoryClick = medicinesOnIntakeHistoryClick;
    }

    public interface MedicinesOnRowClick {
        void medicines_onRowClick(MedicineRow medicineRow);
    }

    public interface MedicinesOnDeleteClick {
        void medicines_onDeleteClick(MedicineRow medicineRow);
    }

    public interface MedicinesOnIntakeHistoryClick {
        void medicines_onIntakeHistoryClick(MedicineRow medicineRow);
    }
}
