package m.co.rh.id.a_medic_log.app.ui.component;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.annotation.NavRouteIndex;
import m.co.rh.id.anavigator.component.INavigator;

public class AppBarSV extends StatefulView<Activity> implements View.OnClickListener, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;
    @NavRouteIndex
    private transient byte mRouteIndex;
    private String mTitle;
    private transient View.OnClickListener mNavigationOnClickListener;
    private Integer mMenuResId;
    private transient Toolbar.OnMenuItemClickListener mOnMenuItemClickListener;
    private transient Toolbar mToolbar;

    public AppBarSV() {
        this(null);
    }

    public AppBarSV(Integer menuResId) {
        mMenuResId = menuResId;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.app_bar, container, false);
        mToolbar = view.findViewById(R.id.toolbar);
        mToolbar.setTitle(mTitle);
        if (isInitialRoute()) {
            mToolbar.setNavigationIcon(R.drawable.ic_menu_white);
        } else {
            mToolbar.setNavigationIcon(R.drawable.ic_arrow_back_white);
        }
        mToolbar.setNavigationOnClickListener(this);
        if (mMenuResId != null) {
            mToolbar.inflateMenu(mMenuResId);
        }
        mToolbar.setOnMenuItemClickListener(this);
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mToolbar = null;
        mNavigationOnClickListener = null;
        mOnMenuItemClickListener = null;
        mNavigator = null;
    }

    public boolean isInitialRoute() {
        return mRouteIndex == 0;
    }

    public void setTitle(String title) {
        mTitle = title;
        if (mToolbar != null) {
            mToolbar.setTitle(title);
        }
    }

    public void setNavigationOnClick(View.OnClickListener navigationOnClickListener) {
        mNavigationOnClickListener = navigationOnClickListener;
    }

    public void setMenuItemListener(Toolbar.OnMenuItemClickListener listener) {
        mOnMenuItemClickListener = listener;
    }

    @Override
    public void onClick(View view) {
        if (isInitialRoute()) {
            if (mNavigationOnClickListener != null) {
                mNavigationOnClickListener.onClick(view);
            }
        } else {
            mNavigator.pop();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (mOnMenuItemClickListener != null) {
            return mOnMenuItemClickListener.onMenuItemClick(item);
        }
        return false;
    }
}
