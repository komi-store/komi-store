package zed.rainxch.core.data.services

import java.io.IOException

// The command's exit code, or the failure that kept it from starting or finishing.
internal fun processExitCode(command: List<String>): Result<Int> =
    try {
        Result.success(ProcessBuilder(command).start().waitFor())
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: InterruptedException) {
        Result.failure(e)
    }
