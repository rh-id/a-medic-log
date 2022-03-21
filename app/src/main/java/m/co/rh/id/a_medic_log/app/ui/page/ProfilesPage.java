package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.profile.ProfileListSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class ProfilesPage extends StatefulView<Activity> implements Toolbar.OnMenuItemClickListener {
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ProfileListSV mProfileListSV;

    public ProfilesPage() {
        mAppBarSV = new AppBarSV(R.menu.page_profiles);
        mProfileListSV = new ProfileListSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_profiles, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_profiles));
        mAppBarSV.setMenuItemListener(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mProfileListSV.buildView(activity, rootLayout));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int menuId = menuItem.getItemId();
        if (menuId == R.id.menu_add) {
            mNavigator.push(Routes.PROFILE_DETAIL_PAGE);
            return true;
        }
        return false;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mProfileListSV != null) {
            mProfileListSV.dispose(activity);
            mProfileListSV = null;
        }
    }
}
