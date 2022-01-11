package m.co.rh.id.a_medic_log.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Routes;
import m.co.rh.id.a_medic_log.app.ui.component.AppBarSV;
import m.co.rh.id.a_medic_log.app.ui.component.medicine.intake.MedicineIntakeListSV;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.anavigator.component.RequireNavigator;

public class MedicineIntakeListPage extends StatefulView<Activity> implements RequireNavigator, RequireNavRoute, Toolbar.OnMenuItemClickListener {
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private MedicineIntakeListSV mMedicineIntakeListSV;

    public MedicineIntakeListPage() {
        mAppBarSV = new AppBarSV(R.menu.page_medicine_intake_list);
        mMedicineIntakeListSV = new MedicineIntakeListSV();
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
        mMedicineIntakeListSV.setMedicineId(getMedicineId());
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_medicine_intake_list, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_medicine_intake_list));
        mAppBarSV.setMenuItemListener(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mMedicineIntakeListSV.buildView(activity, rootLayout));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int menuId = menuItem.getItemId();
        if (menuId == R.id.menu_add) {
            mNavigator.push(Routes.MEDICINE_INTAKE_DETAIL_PAGE,
                    MedicineIntakeDetailPage.Args.with(getMedicineId()));
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
        if (mMedicineIntakeListSV != null) {
            mMedicineIntakeListSV.dispose(activity);
            mMedicineIntakeListSV = null;
        }
    }

    private Long getMedicineId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mMedicineId;
        }
        return null;
    }

    static class Args implements Serializable {
        static Args with(long medicineId) {
            Args args = new Args();
            args.mMedicineId = medicineId;
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

        private Long mMedicineId;
    }
}
