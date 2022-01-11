package m.co.rh.id.a_medic_log.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class LicensesMenuSV extends StatefulView<Activity> implements View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_license, container, false);
        Button button = view.findViewById(R.id.menu_licenses);
        button.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.menu_licenses) {
            mNavigator.push((args, activity1) -> new LicensesPage());
        }
    }
}
