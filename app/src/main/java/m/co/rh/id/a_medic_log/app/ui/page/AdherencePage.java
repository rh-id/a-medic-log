package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.report.AdherenceReportSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;

public class AdherencePage extends StatefulView<Activity> implements Toolbar.OnMenuItemClickListener {
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private AdherenceReportSV mAdherenceReportSV;

    public AdherencePage() {
        mAppBarSV = new AppBarSV(R.menu.menu_adherence);
        mAdherenceReportSV = new AdherenceReportSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_adherence, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_adherence));
        mAppBarSV.setMenuItemListener(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mAdherenceReportSV.buildView(activity, rootLayout));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        return false;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mAdherenceReportSV != null) {
            mAdherenceReportSV.dispose(activity);
            mAdherenceReportSV = null;
        }
    }
}
