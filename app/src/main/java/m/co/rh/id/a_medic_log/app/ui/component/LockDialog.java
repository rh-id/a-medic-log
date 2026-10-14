package m.co.rh.id.a_medic_log.app.ui.component;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;

import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.provider.component.AppLockAuthHandler;

/**
 * Full-screen overlay shown while the app is locked, and the kick-off owner of
 * the biometric presentation: {@link #show()} asks {@link AppLockAuthHandler}
 * to present the prompt and the Unlock button retries through
 * {@link AppLockAuthHandler#forcePresent(FragmentActivity, Callbacks)}
 * (explicit user intent recovers even from a wedged presentation), while the
 * prompt lifecycle and the presenting flag remain owned by
 * {@link AppLockAuthHandler}. The dialog implements
 * {@link AppLockAuthHandler.Callbacks} itself: hints are surfaced through
 * {@link #setHint(CharSequence)} and a successful authentication notifies the
 * {@link OnUnlockedListener} BEFORE the window is dismissed, so the host's
 * unlock side effects still run while the lock is up (the dismiss cancels the
 * prompt, harmless once authentication already succeeded).
 * <p>
 * This is deliberately a plain {@link Dialog} and NOT a navigator route:
 * Navigator.setViewAnimator() replaces the activity content view on init and on
 * every reBuildAllRoute() (fired on each uiMode change), so an in-content overlay
 * would be destroyed; and StatefulViewDialog windows are re-shown ABOVE the
 * activity window by Navigator.onActivityResumed, which an in-content overlay
 * could never cover. A full-screen dialog shown after resume stacks above all
 * of it (dialog windows stack in show order).
 */
public class LockDialog implements AppLockAuthHandler.Callbacks {

    /**
     * Notified once on a successful authentication, BEFORE the lock window is
     * dismissed so the unlock side effects run while the lock is still up.
     */
    public interface OnUnlockedListener {
        void onUnlocked();
    }

    private final FragmentActivity mActivity;
    private final AppLockAuthHandler mAuthHandler;
    private final OnUnlockedListener mOnUnlockedListener;
    private Dialog mDialog;
    private TextView mTextHint;
    /**
     * Last hint received, re-applied when the dialog window is re-created so
     * the message survives the window recreation of the stacking dance
     */
    @Nullable
    private CharSequence mLastHint;

    public LockDialog(@NonNull FragmentActivity activity, @NonNull AppLockAuthHandler authHandler,
                      @NonNull OnUnlockedListener listener) {
        mActivity = activity;
        mAuthHandler = authHandler;
        mOnUnlockedListener = listener;
    }

    @Override
    public void onHint(@NonNull CharSequence hint) {
        setHint(hint);
    }

    @Override
    public void onAuthenticated() {
        // notify-then-dismiss order matters: the host's unlock side effects must
        // run while the lock window is still up; dismiss() also cancels the
        // prompt, which is harmless once authentication already succeeded
        mOnUnlockedListener.onUnlocked();
        dismiss();
    }

    /**
     * Surfaces a message from the authentication owner (lockout, missing
     * authenticator, error, ...).
     */
    public void setHint(@Nullable CharSequence hint) {
        mLastHint = hint;
        TextView textHint = mTextHint;
        if (textHint != null) {
            textHint.setVisibility(View.VISIBLE);
            textHint.setText(hint);
        }
    }

    public boolean isShowing() {
        Dialog dialog = mDialog;
        return dialog != null && dialog.isShowing();
    }

    /**
     * Shows the lock dialog window and kicks off the biometric presentation.
     * Safe to call repeatedly (e.g. on every resume while locked): if the window
     * is already showing it is discarded and re-created so it always stacks
     * above any dialog route the navigator re-showed on resume, and the last
     * hint is carried over to the new window. The presentation is requested
     * AFTER the dismiss-and-recreate dance so the dance's cancel always
     * precedes the new presentation request in the auth handler's queue (a
     * cancel issued after the request would discard it as stale).
     */
    public void show() {
        if (isShowing()) {
            // the navigator re-shows its dialog routes on resume and their windows
            // land above this one, so the lock window must always be re-added last:
            // discard the current window and build a fresh one
            dismiss();
        }
        if (mDialog == null) {
            mDialog = createDialog();
        }
        if (!mDialog.isShowing()) {
            mDialog.show();
        }
        // window is stacked last, now ask for the prompt (no-op while a
        // presentation is already in flight, the next resume re-presents otherwise)
        mAuthHandler.ensurePresented(mActivity, this);
    }

    public void dismiss() {
        Dialog dialog = mDialog;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        mDialog = null;
        mTextHint = null;
        // the system prompt must never outlive the lock window, ask the owner
        // to tear the presentation down (no-op when nothing is presenting)
        mAuthHandler.cancel();
    }

    @NonNull
    private Dialog createDialog() {
        Dialog dialog = new Dialog(mActivity);
        dialog.setContentView(R.layout.dialog_lock);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            // resolve the window background from the theme like MainActivity does
            TypedValue windowBackground = new TypedValue();
            mActivity.getTheme().resolveAttribute(android.R.attr.windowBackground,
                    windowBackground, true);
            if (windowBackground.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && windowBackground.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                window.setBackgroundDrawable(new ColorDrawable(windowBackground.data));
            } else if (windowBackground.resourceId != 0) {
                window.setBackgroundDrawableResource(windowBackground.resourceId);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // draw behind the system bars so the lock screen covers everything,
                // the inset listener below keeps the content out of them
                window.setDecorFitsSystemWindows(false);
            }
            // keep the content inside the system bars (the app is edge-to-edge on Android 15+)
            View contentView = dialog.findViewById(android.R.id.content);
            ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(systemBars.left, systemBars.top,
                        systemBars.right, systemBars.bottom);
                return insets;
            });
        }
        TextView textHint = dialog.findViewById(R.id.text_lock_hint);
        mTextHint = textHint;
        if (mLastHint != null) {
            // re-apply the last hint so it survives the window recreation
            textHint.setVisibility(View.VISIBLE);
            textHint.setText(mLastHint);
        } else {
            textHint.setVisibility(View.GONE);
        }
        Button buttonUnlock = dialog.findViewById(R.id.button_unlock);
        buttonUnlock.setOnClickListener(view ->
                // explicit user intent to (re)try the authentication, recovers
                // even from a wedged presentation by resetting the presenting flag
                mAuthHandler.forcePresent(mActivity, this));
        return dialog;
    }
}
