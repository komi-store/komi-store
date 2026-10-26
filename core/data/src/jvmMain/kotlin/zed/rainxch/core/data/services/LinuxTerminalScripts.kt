package zed.rainxch.core.data.services

internal object LinuxTerminalScripts {
    private const val RULE = "echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; "

    fun alienConversion(filePath: String): String =
        buildString {
            appendAlienSetup()
            appendAlienConversion(filePath)
            appendConvertedRpmInstall()
        }

    fun pacmanInstall(quotedPath: String): String =
        buildString {
            append(RULE)
            append("echo 'Installing Arch Package'; ")
            append(RULE)
            append("echo ''; ")
            append("echo 'You will be prompted for your sudo password.'; ")
            append("echo ''; ")
            append("sudo pacman -U $quotedPath; ")
            append("EXIT=\$?; ")
            append("echo ''; ")
            append("if [ \$EXIT -eq 0 ]; then ")
            append(RULE)
            append("echo 'Installation Complete!'; ")
            append(RULE)
            append("else ")
            append(RULE)
            append("echo 'Installation Failed (exit \$EXIT)'; ")
            append(RULE)
            append("fi; ")
            append("echo ''; ")
            append("echo 'Press Enter to close...'; read")
        }

    fun pacmanIncompatible(quotedPath: String): String =
        buildString {
            append(RULE)
            append("echo 'Wrong Package Format'; ")
            append(RULE)
            append("echo ''; ")
            append("echo 'This file is an Arch Linux package (.pkg.tar.zst):'; ")

            append("printf '  %s\\n' $quotedPath; ")
            append("echo ''; ")
            append("echo 'Your system uses a different package format.'; ")
            append("echo 'Please download the .deb, .rpm, or .AppImage variant'; ")
            append("echo 'instead from the release page.'; ")
            append("echo ''; ")
            append("echo 'Press Enter to close...'; read")
        }

    fun debInstall(filePath: String): String =
        buildString {
            append(RULE)
            append("echo 'Installing DEB Package'; ")
            append(RULE)
            append("echo ''; ")
            append("sudo dpkg -i '$filePath' && sudo apt-get install -f -y; ")
            append("echo ''; ")
            append(RULE)
            append("echo 'Installation Complete!'; ")
            append(RULE)
            append("echo ''; ")
            append("echo 'Press Enter to close...'; read")
        }

    fun rpmInstall(filePath: String): String =
        buildString {
            append(RULE)
            append("echo 'Installing RPM Package'; ")
            append(RULE)
            append("echo ''; ")
            append("sudo dnf install -y --nogpgcheck '$filePath' 2>/dev/null || ")
            append("sudo yum install -y --nogpgcheck '$filePath' 2>/dev/null || ")
            append("sudo zypper install -y --no-gpg-checks '$filePath' 2>/dev/null || ")
            append("sudo rpm -ivh --nosignature '$filePath'; ")
            append("echo ''; ")
            append(RULE)
            append("echo 'Installation Complete!'; ")
            append(RULE)
            append("echo ''; ")
            append("echo 'Press Enter to close...'; read")
        }

    fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun StringBuilder.appendAlienSetup() {
        append(RULE)
        append("echo 'DEB Package on RPM System Detected'; ")
        append(RULE)
        append("echo ''; ")
        append("echo 'This package will be converted to RPM format.'; ")
        append("echo 'This requires the \"alien\" tool.'; ")
        append("echo ''; ")

        append("if ! command -v alien &> /dev/null; then ")
        append("echo 'Installing alien and rpm-build...'; ")
        append("sudo dnf install -y alien rpm-build 2>/dev/null || ")
        append("sudo yum install -y alien rpm-build 2>/dev/null || ")
        append("sudo zypper install -y alien rpm-build 2>/dev/null; ")
        append("fi; ")

        append("if ! command -v alien &> /dev/null; then ")
        append("echo ''; ")
        append("echo 'ERROR: Failed to install alien.'; ")
        append("echo 'Please install it manually: sudo dnf install alien rpm-build'; ")
        append("echo ''; ")
        append("echo 'Press Enter to close...'; read; exit 1; ")
        append("fi; ")
    }

    private fun StringBuilder.appendAlienConversion(filePath: String) {
        append("echo ''; ")
        append("echo 'Converting to RPM (this may take a minute)...'; ")
        append("TMPDIR=/tmp/alien_install_$; ")
        append($$"mkdir -p \"$TMPDIR\" && cd \"$TMPDIR\" || exit 1; ")
        append("cp '$filePath' ./package.deb; ")
        append("sudo alien -r -c package.deb; ")

        append("if [ ! -f *.rpm ]; then ")
        append("echo ''; ")
        append("echo 'ERROR: Conversion failed.'; ")
        append($$"cd .. && rm -rf \"$TMPDIR\"; ")
        append("echo 'Press Enter to close...'; read; exit 1; ")
        append("fi; ")
    }

    private fun StringBuilder.appendConvertedRpmInstall() {
        append("echo ''; ")
        append("echo 'Installing converted RPM...'; ")
        append("INSTALL_SUCCESS=0; ")

        append("if sudo dnf install -y ./*.rpm 2>&1; then INSTALL_SUCCESS=1; ")
        append("elif sudo yum install -y ./*.rpm 2>&1; then INSTALL_SUCCESS=1; ")
        append("elif sudo zypper install -y --allow-unsigned-rpm ./*.rpm 2>&1; then INSTALL_SUCCESS=1; ")
        append("elif sudo rpm -ivh --nodeps --force ./*.rpm 2>&1; then INSTALL_SUCCESS=1; ")
        append("fi; ")

        append("echo ''; ")
        append($$"if [ $INSTALL_SUCCESS -eq 1 ]; then ")
        append(RULE)
        append("echo 'Installation Complete!'; ")
        append(RULE)
        append("else ")
        append(RULE)
        append("echo 'Installation Failed!'; ")
        append(RULE)
        append("echo ''; ")
        append("echo 'The RPM was created but installation failed.'; ")
        append("echo 'This usually happens due to file conflicts.'; ")
        append("echo ''; ")
        append("echo 'The converted RPM is located at:'; ")
        append($$"echo \"$TMPDIR/\"*.rpm; ")
        append("echo ''; ")
        append("echo 'You can try installing it manually with:'; ")
        append($$"echo \"sudo rpm -ivh --force $TMPDIR/\"*.rpm; ")
        append("echo ''; ")
        append("echo 'Or open the file with your software manager.'; ")
        append("fi; ")

        append($$"cd .. && rm -rf \"$TMPDIR\"; ")
        append("echo ''; ")
        append("echo 'Press Enter to close...'; read")
    }
}
