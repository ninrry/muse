// IMuseFileService.aidl
package luzzr.muse.shizuku;

interface IMuseFileService {
    boolean copyFile(in String sourcePath, in String targetPath);
    boolean deleteFile(in String path);
    long fileSize(in String path);
}
