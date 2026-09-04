package m.co.rh.id.a_medic_log.base.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
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
    private ImageHelper mImageHelper;
    private File mLogFile;
    private File mTempFileRoot;
    private File mNoteAttachmentFileImageParent;
    private File mNoteAttachmentFileThumbnailParent;

    public FileHelper(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mImageHelper = new ImageHelper(mAppContext);
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
                    bufferedOutputStream.write(buff, 0, b);
                    b = bufferedInputStream.read(buff);
                }
            }
        }
        return tmpFile;
    }

    /**
     * Raw copy the source file into the destination Uri (e.g. a SAF document Uri)
     * using the ContentResolver.
     * No re-encoding is done, the bytes are copied as-is.
     *
     * @param source  source file to copy from
     * @param destUri destination Uri to copy into
     * @throws IOException when failed to open the output stream or to copy the file
     */
    public void copyFileToUri(File source, Uri destUri) throws IOException {
        ContentResolver contentResolver = mAppContext.getContentResolver();
        // "wt" requests truncate-on-write, some third-party DocumentsProviders
        // do not truncate an existing file on plain "w"
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(source));
             OutputStream outputStream = contentResolver.openOutputStream(destUri, "wt")) {
            if (outputStream == null) {
                throw new IOException("Failed to open output stream for " + destUri);
            }
            byte[] buff = new byte[2048];
            int b = inputStream.read(buff);
            while (b != -1) {
                outputStream.write(buff, 0, b);
                b = inputStream.read(buff);
            }
            outputStream.flush();
        }
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
            mImageHelper.copyImage(inUri, outFile);
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
            mImageHelper.copyImage(content, outFile, 320, 180);
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
            mImageHelper.copyImage(content, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getNoteAttachmentImageParent() {
        return mNoteAttachmentFileImageParent;
    }

    public File getNoteAttachmentThumbnailParent() {
        return mNoteAttachmentFileThumbnailParent;
    }

    /**
     * Raw copy the source image and thumbnail files into the attachment image and
     * thumbnail directories using newFileName as the file name.
     * No re-encoding is done, the bytes are copied as-is.
     *
     * @param sourceImage     source file of the full image
     * @param sourceThumbnail source file of the thumbnail
     * @param newFileName     new unique file name to use for both the image and the thumbnail
     * @throws IOException when failed to copy either file
     */
    public void copyToAttachmentDirs(File sourceImage, File sourceThumbnail, String newFileName) throws IOException {
        File imageFile = new File(mNoteAttachmentFileImageParent, newFileName);
        File thumbnailFile = new File(mNoteAttachmentFileThumbnailParent, newFileName);
        try {
            copyFile(sourceImage, imageFile);
            copyFile(sourceThumbnail, thumbnailFile);
        } catch (IOException e) {
            imageFile.delete();
            thumbnailFile.delete();
            throw e;
        }
    }

    private static void copyFile(File source, File outFile) throws IOException {
        outFile.createNewFile();
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(source));
             OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outFile))) {
            byte[] buff = new byte[2048];
            int b = inputStream.read(buff);
            while (b != -1) {
                outputStream.write(buff, 0, b);
                b = inputStream.read(buff);
            }
            outputStream.flush();
        }
    }
}
