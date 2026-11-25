package zed.rainxch.tweaks.presentation.components.desktop

import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot

enum class DesktopSection(
    val slot: TweaksDecorSlot,
) {
    LOOK(TweaksDecorSlot.LookAndFeel),
    CONNECTIVITY(TweaksDecorSlot.Connectivity),
    PRIVACY(TweaksDecorSlot.PrivacyData),
    APP(TweaksDecorSlot.App),
}
