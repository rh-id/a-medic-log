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
import m.co.rh.id.a_medic_log.app.ui.component.note.NoteListSV;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class NoteListPage extends StatefulView<Activity> implements RequireNavigator, RequireNavRoute, RequireComponent<Provider>, Toolbar.OnMenuItemClickListener {
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private NoteListSV mNoteListSV;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        Activity activity = mNavigator.getActivity();
        if (mAppBarSV == null) {
            mAppBarSV = new AppBarSV(R.menu.page_profile_list);
        }
        mAppBarSV.setTitle(activity.getString(R.string.title_note_list));
        mAppBarSV.setMenuItemListener(this);
        if (mNoteListSV == null) {
            mNoteListSV = new NoteListSV(getProfileId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_note_list, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mNoteListSV.buildView(activity, rootLayout));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int menuId = menuItem.getItemId();
        if (menuId == R.id.menu_add) {
            mNavigator.push(Routes.NOTE_DETAIL_PAGE,
                    NoteDetailPage.Args.withProfileId(getProfileId()));
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
        if (mNoteListSV != null) {
            mNoteListSV.dispose(activity);
            mNoteListSV = null;
        }
    }

    private Long getProfileId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mProfileId;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args withProfileId(long profileId) {
            Args args = new Args();
            args.mProfileId = profileId;
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

        private Long mProfileId;
    }
}
