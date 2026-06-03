package m.co.rh.id.a_medic_log.base.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.exifinterface.media.ExifInterface;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Class to provide files through this app
 */
public class FileHelper {
    private static final String TAG = FileHelper.class.getName();

    private Context mAppContext;
    private ProviderValue<ILogger> mLogger;
    private File mLogFile;
    private File mTempFileRoot;
    private File mNoteAttachmentFileImageParent;
    private File mNoteAttachmentFileThumbnailParent;

    public FileHelper(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        File cacheDir = mAppContext.getCacheDir();
        File fileDir = mAppContext.getFilesDir();
        mLogFile = new File(cacheDir, "alogger/app.log");
        mTempFileRoot = new File(cacheDir, "/tmp");
        mTempFileRoot.mkdirs();
        mNoteAttachmentFileImageParent = new File(fileDir, "app/note/attachment/image");
        mNoteAttachmentFileImageParent.mkdirs();
        mNoteAttachmentFileThumbnailParent = new File(fileDir, "app/note/attachment/image/thumbnail");
        mNoteAttachmentFileThumbnailParent.mkdirs();
    }

    public File createTempFile(String fileName) throws IOException {
        return createTempFile(fileName, null);
    }

    /**
     * Create temporary file
     *
     * @param fileName file name for this file
     * @param content  content of the file to write to this temp file
     * @return temporary file
     * @throws IOException when failed to create file
     */
    public File createTempFile(String fileName, Uri content) throws IOException {
        File parent = new File(mTempFileRoot, UUID.randomUUID().toString());
        parent.mkdirs();
        String fName = fileName;
        if (fName == null || fName.isEmpty()) {
            fName = UUID.randomUUID().toString();
        }
        File tmpFile = new File(parent, fName);
        tmpFile.createNewFile();

        if (content != null) {
            ContentResolver cr = mAppContext.getContentResolver();
            try (InputStream inputStream = cr.openInputStream(content);
                 BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                 FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
                 BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream)) {
                byte[] buff = new byte[2048];
                int b = bufferedInputStream.read(buff);
                while (b != -1) {
                    bufferedOutputStream.write(buff);
                    b = bufferedInputStream.read(buff);
                }
            }
        }
        return tmpFile;
    }

    public void clearLogFile() {
        if (mLogFile.exists()) {
            mLogFile.delete();
            try {
                mLogFile.createNewFile();
            } catch (Throwable throwable) {
                mLogger.get().e(TAG, "Failed to create new file for log", throwable);
            }
        }
    }

    public File getLogFile() {
        return mLogFile;
    }

    public File createNoteAttachmentImage(Uri inUri, String fileName) throws IOException {
        File outFile = new File(mNoteAttachmentFileImageParent, fileName);
        try {
            outFile.createNewFile();
            copyImage(inUri, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getNoteAttachmentImage(String fileName) {
        return new File(mNoteAttachmentFileImageParent, fileName);
    }

    public File createNoteAttachmentThumbnail(Uri content, String fileName) throws IOException {
        File outFile = new File(mNoteAttachmentFileThumbnailParent, fileName);
        try {
            outFile.createNewFile();
            copyImage(content, outFile, 320, 180);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getNoteAttachmentThumbnail(String fileName) {
        return new File(mNoteAttachmentFileThumbnailParent, fileName);
    }

    public void deleteNoteAttachmentImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mNoteAttachmentFileImageParent, fileName);
            file.delete();
            File thumbnail = new File(mNoteAttachmentFileThumbnailParent, fileName);
            thumbnail.delete();
        }
    }

    public File createImageTempFile() throws IOException {
        File parent = new File(mTempFileRoot, UUID.randomUUID().toString());
        parent.mkdirs();
        File tmpFile = new File(parent, UUID.randomUUID().toString() + ".jpg");
        tmpFile.createNewFile();
        return tmpFile;
    }

    public File createImageTempFile(Uri content) throws IOException {
        File outFile = createImageTempFile();
        try {
            copyImage(content, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    private void copyImage(Uri content, File outFile) throws IOException {
        copyImage(content, outFile, 1280, 720);
    }

    private void copyImage(Uri content, File outFile, int width, int height) throws IOException {
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

    public File getNoteAttachmentImageParent() {
        return mNoteAttachmentFileImageParent;
    }
}
