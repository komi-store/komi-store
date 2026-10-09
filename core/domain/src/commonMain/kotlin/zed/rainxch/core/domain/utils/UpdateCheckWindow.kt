package zed.rainxch.core.domain.utils

// The raw window the fetch-driven update check judges: the release fetch asks the server for
// this many entries and the window-fed check expects no fewer before it trusts a fed list.
// Exposed so the caller that feeds a window can tell "the read covered the check's ground"
// from "the read saw less than the check would" — a shorter list must not settle a verdict,
// or a genuine update sitting past the read's coverage would be reported as gone.
object UpdateCheckWindow {
    const val Size: Int = 50
}
