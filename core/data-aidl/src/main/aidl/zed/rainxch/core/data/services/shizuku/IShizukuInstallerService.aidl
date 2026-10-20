package zed.rainxch.core.data.services.shizuku;

interface IShizukuInstallerService {
    int installPackage(in ParcelFileDescriptor pfd, long fileSize, String installerPackageName);
    int uninstallPackage(String packageName);
    void destroy();
}
