package m.co.rh.id.a_medic_log.app.ui.page.common;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_medic_log.R;
import m.co.rh.id.a_medic_log.app.constants.Constants;
import m.co.rh.id.a_medic_log.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class CreateFileSVDialog extends StatefulViewDialog<Activity> implements RequireComponent<Provider>, NavOnActivityResult<Activity>, View.OnClickListener {
    private static final String TAG = CreateFileSVDialog.class.getName();

    private static final int IMAGE_RADIO_SELECTED = 1;
    private static final int PHOTO_RADIO_SELECTED = 2;
    private static final int REQUEST_CODE_IMAGE_BROWSE = 1;
    private static final int REQUEST_CODE_TAKE_PHOTO = 2;

    private transient ILogger mLogger;
    private transient FileHelper mFileHelper;
    private transient ExecutorService mExecutorService;
    private transient CompositeDisposable mCompositeDisposable;

    private int mSelectedRadio = IMAGE_RADIO_SELECTED;
    private transient RadioGroup.OnCheckedChangeListener mOnCheckedChangeListener;
    private File mTempCameraFile;

    @Override
    public void provideComponent(Provider provider) {
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mExecutorService = provider.get(ExecutorService.class);
        mCompositeDisposable = new CompositeDisposable();
        mOnCheckedChangeListener = (radioGroup, id) -> {
            if (id == R.id.radio_button_image) {
                mSelectedRadio = IMAGE_RADIO_SELECTED;
            } else if (id == R.id.radio_button_photo) {
                mSelectedRadio = PHOTO_RADIO_SELECTED;
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.dialog_create_file, container, false);
        RadioGroup radioGroup = rootLayout.findViewById(R.id.radio_group);
        radioGroup.check(R.id.radio_button_image);
        radioGroup.setOnCheckedChangeListener(mOnCheckedChangeListener);
        Button cancelButton = rootLayout.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);
        Button okButton = rootLayout.findViewById(R.id.button_ok);
        okButton.setOnClickListener(this);
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mOnCheckedChangeListener != null) {
            mOnCheckedChangeListener = null;
        }
        if (mCompositeDisposable != null) {
            mCompositeDisposable.dispose();
            mCompositeDisposable = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_cancel) {
            getNavigator().pop();
        } else if (id == R.id.button_ok) {
            if (mSelectedRadio == IMAGE_RADIO_SELECTED) {
                Activity activity = getNavigator().getActivity();
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                activity.startActivityForResult(intent, REQUEST_CODE_IMAGE_BROWSE);
            } else if (mSelectedRadio == PHOTO_RADIO_SELECTED) {
                try {
                    mTempCameraFile = mFileHelper.createImageTempFile();
                    Activity activity = getNavigator().getActivity();
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    Uri photoURI = FileProvider.getUriForFile(activity,
                            Constants.FILE_PROVIDER_AUTHORITY,
                            mTempCameraFile);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    activity.startActivityForResult(cameraIntent, REQUEST_CODE_TAKE_PHOTO);
                } catch (Exception e) {
                    mLogger.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_IMAGE_BROWSE && resultCode == Activity.RESULT_OK) {
            Uri fullPhotoUri = data.getData();
            returnResult(fullPhotoUri);
        } else if (requestCode == REQUEST_CODE_TAKE_PHOTO && resultCode == Activity.RESULT_OK) {
            Uri fullPhotoUri = Uri.fromFile(mTempCameraFile);
            returnResult(fullPhotoUri);
            mTempCameraFile = null;
        }
    }

    private void returnResult(Uri fullPhotoUri) {
        mCompositeDisposable.add(Single.fromFuture(mExecutorService.submit(() -> mFileHelper
                        .createImageTempFile(fullPhotoUri))
                ).observeOn(AndroidSchedulers.mainThread())
                        .subscribe((file, throwable) -> {
                            if (throwable != null) {
                                if (throwable.getCause() != null) {
                                    mLogger.e(TAG, throwable.getCause().getMessage(), throwable);
                                } else {
                                    mLogger.e(TAG, throwable.getMessage(), throwable);
                                }
                            } else {
                                getNavigator().pop(Result.with(file));
                            }
                        })
        );
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

        static Result with(File file) {
            Result result = new Result();
            result.file = file;
            return result;
        }

        private File file;

        public File getFile() {
            return file;
        }
    }
}
