package m.co.rh.id.a_medic_log.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.component.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class ThemeMenuSV extends StatefulView<Activity> implements RadioGroup.OnCheckedChangeListener,
        CompoundButton.OnCheckedChangeListener {

    @NavInject
    private transient Provider mProvider;
    private transient Activity mActivity;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        mActivity = activity;
        View view = activity.getLayoutInflater().inflate(R.layout.menu_theme, container, false);
        int selectedRadioId = getSelectedRadioId();
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup);
        radioGroup.check(selectedRadioId);
        radioGroup.setOnCheckedChangeListener(this);
        CompoundButton switchDynamicColors = view.findViewById(R.id.switch_dynamic_colors);
        // apply current value before attaching the listener so initialization
        // does not write back to the shared preferences nor recreate the activity
        switchDynamicColors.setChecked(mProvider.get(AppSharedPreferences.class).isDynamicColorsEnabled());
        switchDynamicColors.setOnCheckedChangeListener(this);
        // dynamic colors need Android 12+ support, hide the option otherwise
        if (!DynamicColors.isDynamicColorAvailable()) {
            switchDynamicColors.setVisibility(View.GONE);
        }
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mActivity = null;
    }

    private int getSelectedRadioId() {
        int theme = mProvider.get(AppSharedPreferences.class).getSelectedTheme();
        int result = R.id.radio_system;
        if (theme == AppCompatDelegate.MODE_NIGHT_NO) {
            result = R.id.radio_light;
        } else if (theme == AppCompatDelegate.MODE_NIGHT_YES) {
            result = R.id.radio_dark;
        }
        return result;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        int id = compoundButton.getId();
        if (id == R.id.switch_dynamic_colors) {
            mProvider.get(AppSharedPreferences.class).setDynamicColorsEnabled(isChecked);
            Activity activity = mActivity;
            if (activity != null) {
                activity.recreate();
            }
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int i) {
        int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (i == R.id.radio_light) {
            selectedTheme = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (i == R.id.radio_dark) {
            selectedTheme = AppCompatDelegate.MODE_NIGHT_YES;
        }
        mProvider.get(AppSharedPreferences.class)
                .setSelectedTheme(selectedTheme);
    }
}
