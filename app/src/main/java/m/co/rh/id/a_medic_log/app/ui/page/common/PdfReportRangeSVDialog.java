package m.co.rh.id.a_medic_log.app.ui.page.common;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textview.MaterialTextView;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;

/**
 * Dialog to pick the intake-history range (7, 30 or 90 days) for the
 * PDF medication report export. Pops with a {@link Result} carrying the
 * selected range in days when OK is pressed, pops without a result on cancel.
 */
public class PdfReportRangeSVDialog extends StatefulViewDialog<Activity>
        implements View.OnClickListener {
    public static final int RANGE_7_DAYS = 7;
    public static final int RANGE_30_DAYS = 30;
    public static final int RANGE_90_DAYS = 90;

    private int mSelectedRangeDays;

    public PdfReportRangeSVDialog() {
        mSelectedRangeDays = RANGE_30_DAYS;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater()
                .inflate(R.layout.dialog_pdf_report_range, container, false);
        MaterialTextView textTitle = rootLayout.findViewById(R.id.text_title);
        textTitle.setText(R.string.dialog_pdf_report_range_title);
        MaterialButtonToggleGroup toggleGroupRange =
                rootLayout.findViewById(R.id.toggle_group_range);
        // sync the toggle with the selected range before registering the
        // listener so the restore itself does not change the selection
        toggleGroupRange.check(mSelectedRangeDays == RANGE_7_DAYS ? R.id.toggle_7_days
                : mSelectedRangeDays == RANGE_90_DAYS ? R.id.toggle_90_days
                : R.id.toggle_30_days);
        toggleGroupRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.toggle_7_days) {
                mSelectedRangeDays = RANGE_7_DAYS;
            } else if (checkedId == R.id.toggle_90_days) {
                mSelectedRangeDays = RANGE_90_DAYS;
            } else {
                mSelectedRangeDays = RANGE_30_DAYS;
            }
        });
        MaterialButton okButton = rootLayout.findViewById(R.id.button_ok);
        okButton.setOnClickListener(this);
        MaterialButton cancelButton = rootLayout.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_ok) {
            getNavigator().pop(Result.with(mSelectedRangeDays));
        } else if (id == R.id.button_cancel) {
            getNavigator().pop();
        }
    }

    public static class Result implements Serializable {
        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable serializable = navRoute.getRouteResult();
                if (serializable instanceof Result) {
                    return (Result) serializable;
                }
            }
            return null;
        }

        static Result with(int rangeDays) {
            Result result = new Result();
            result.rangeDays = rangeDays;
            return result;
        }

        private int rangeDays;

        public Integer getRangeDays() {
            return rangeDays;
        }
    }
}
