#!/bin/bash
# Komi Store Flatpak launcher script
# Launches the uber JAR with the bundled JetBrains Runtime

export JAVA_HOME=/app/jre

# Ensure config directory exists
mkdir -p "${XDG_CONFIG_HOME:-$HOME/.config}/komistore"
mkdir -p "${XDG_DATA_HOME:-$HOME/.local/share}/komistore"

# Force X11 backend — Compose Desktop (Skiko) does not yet reliably support
# native Wayland drawing surfaces. On Wayland desktops this runs via XWayland,
# which is available on all major distros (GNOME, KDE, etc.).
export GDK_BACKEND=x11

exec /app/jre/bin/java \
    -Djava.awt.headless=false \
    -Dawt.useSystemAAFontSettings=on \
    -Dswing.aatext=true \
    -Djava.util.prefs.userRoot="${XDG_CONFIG_HOME:-$HOME/.config}/komistore" \
    -Dapp.data.dir="${XDG_DATA_HOME:-$HOME/.local/share}/komistore" \
    -Dapp.downloads.dir="${XDG_DOWNLOAD_DIR:-$HOME/Downloads}" \
    -jar /app/lib/komistore.jar "$@"
