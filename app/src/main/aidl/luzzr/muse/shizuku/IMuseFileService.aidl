// IMuseFileService.aidl
package luzzr.muse.shizuku;

import android.os.ParcelFileDescriptor;

interface IMuseFileService {
    boolean copyFile(in String sourcePath, in String targetPath);
    boolean copyFileDescriptor(in ParcelFileDescriptor source, in String targetPath);
    boolean renameFile(in String sourcePath, in String targetPath);
    boolean deleteFile(in String path);
    long fileSize(in String path);
}
