package zed.rainxch.core.domain.model.installation

// What a park discard left behind, from the point of view of a caller that is about to drop the
// row. The row is the only handle to a parked file, so Retained tells that caller to keep it:
// either the file did not actually go, or the row now names a different park it must not lose.
enum class ParkedInstallDisposal {
    // No parked file remains on disk and no row still names one.
    Discarded,

    // A parked file survived the delete, or the row names a park that is not the one this call
    // was about: something is still parked and the row has to stay.
    Retained,
}
