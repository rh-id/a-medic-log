package m.co.rh.id.a_medic_log.base.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;

/**
 * Helper class to compress and copy image
 */
public class ImageHelper {

    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 720;

    private final Context mAppContext;

    public ImageHelper(Context appContext) {
        mAppContext = appContext;
    }

    public void copyImage(Uri content, File outFile) throws IOException {
        copyImage(content, outFile, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public void copyImage(Uri content, File outFile, int width, int height) throws IOException {
        ContentResolver contentResolver = mAppContext.getContentResolver();
        BitmapFactory.Options bmOptions;
        try (ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(content, "r")) {
            try (InputStream fis = new FileInputStream(pfd.getFileDescriptor())) {
                bmOptions = getBitmapOptionForCompression(fis, width, height);
            }
        }
        Bitmap bitmap = processExifAttr(mAppContext, content, bmOptions);
        try (OutputStream fileOutputStream = new BufferedOutputStream(
                new FileOutputStream(outFile), 10240)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream);
            fileOutputStream.flush();
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private BitmapFactory.Options getBitmapOptionForCompression(InputStream fis, int width, int height) throws IOException {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(fis, null, bmOptions);
        int inWidth = bmOptions.outWidth;
        int inHeight = bmOptions.outHeight;
        int outWidth = width;
        int outHeight = height;
        if (inHeight > inWidth) {
            outHeight = width;
            outWidth = height;
        }
        int scaleFactor = Math.max(1, Math.min(inWidth / outWidth, inHeight / outHeight));
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        return bmOptions;
    }

    private Bitmap processExifAttr(Context context, Uri imageUri, BitmapFactory.Options bmOptions) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        int rotation;
        try (ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(imageUri, "r")) {
            ExifInterface exifInterface = new ExifInterface(pfd.getFileDescriptor());
            rotation = getRotation(exifInterface);
        }
        Bitmap bitmap;
        try (ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(imageUri, "r")) {
            FileDescriptor fd = pfd.getFileDescriptor();
            bitmap = BitmapFactory.decodeFileDescriptor(fd, null, bmOptions);
        }
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.setRotate(rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    matrix, true);
        }
        return bitmap;
    }

    private int getRotation(ExifInterface exifInterface) {
        int rotation = 0;
        int exifRotation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

        if (exifRotation != ExifInterface.ORIENTATION_UNDEFINED) {
            switch (exifRotation) {
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
            }
        }
        return rotation;
    }
}
