package zed.rainxch.core.data.network

import zed.rainxch.core.domain.model.error.NetworkException

class RepoNotFoundException : NetworkException("Repository not found", null)
