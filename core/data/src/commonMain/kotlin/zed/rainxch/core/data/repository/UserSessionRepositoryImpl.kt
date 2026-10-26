package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.USER_PROFILE
import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.core.data.mappers.toUserProfile
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.SessionSnapshot
import zed.rainxch.core.domain.model.account.UserProfile
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.repository.UserSessionRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UserSessionRepositoryImpl(
    private val tokenStore: TokenStore,
    private val cacheManager: CacheManager,
    private val httpClientProvider: () -> HttpClient,
    private val logger: KomiStoreLogger,
) : UserSessionRepository {
    private val httpClient: HttpClient get() = httpClientProvider()

    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    private val sessionExpiredMutex = Mutex()

    private var failingTokenSnapshot: String? = null
    private var firstFailureAtMillis: Long = 0L
    private var consecutiveFailures: Int = 0

    override fun isUserLoggedIn(): Flow<Boolean> =
        tokenStore
            .tokenFlow()
            .map { it != null }

    override suspend fun isCurrentlyUserLoggedIn(): Boolean = tokenStore.currentToken() != null

    @Volatile
    private var _lastKnownSession: SessionSnapshot? = null

    override val lastKnownSession: SessionSnapshot? get() = _lastKnownSession

    private fun recordSession(
        isLoggedIn: Boolean,
        profile: UserProfile?,
    ) {
        _lastKnownSession = SessionSnapshot(isLoggedIn, profile)
    }

    override fun clearLastKnownSession() {
        recordSession(isLoggedIn = false, profile = null)
    }

    private suspend fun ownedCachedProfile(
        token: GithubDeviceTokenSuccessDto,
        allowStale: Boolean,
    ): UserProfile? {
        val stored =
            cacheManager.get<String>(CACHE_OWNER_KEY)
                ?: if (allowStale) cacheManager.getStale<String>(CACHE_OWNER_KEY) else null
        val current = ownershipStamp(token)
        if (stored != null && current != null && stored != current) return null
        return cacheManager.get<UserProfile>(CACHE_KEY)
            ?: if (allowStale) cacheManager.getStale<UserProfile>(CACHE_KEY) else null
    }

    private fun ownershipStamp(token: GithubDeviceTokenSuccessDto): String? = token.savedAtEpochMillis?.toString()

    private suspend fun isCurrentToken(token: GithubDeviceTokenSuccessDto): Boolean =
        tokenStore.currentToken()?.accessToken == token.accessToken

    override suspend fun primeSession() {
        val token = tokenStore.currentToken()
        val profile = token?.let { ownedCachedProfile(it, allowStale = true) }
        recordSession(token != null, profile)
    }

    override fun getUser(): Flow<UserProfile?> =
        flow {
            val token = tokenStore.currentToken()
            if (token == null) {
                cacheManager.invalidate(CACHE_KEY)
                recordSession(isLoggedIn = false, profile = null)
                emit(null)
                return@flow
            }

            val cached = ownedCachedProfile(token, allowStale = false)
            if (cached != null) {
                logger.debug("Profile cache hit")
                emit(publishIfCurrent(token, cached))
                return@flow
            }

            val fetched = fetchAndCacheProfile(token)
            if (fetched != null) {
                emit(publishIfCurrent(token, fetched))
                return@flow
            }

            val fallback = ownedCachedProfile(token, allowStale = true)
            if (fallback != null) {
                logger.debug("Using cached profile as fallback")
                emit(publishIfCurrent(token, fallback))
            } else {
                keepLastKnownProfile(token)
                emit(null)
            }
        }.flowOn(Dispatchers.IO)

    // Null when the fetch or the cache write failed; the caller then falls back to a cached copy.
    private suspend fun fetchAndCacheProfile(token: GithubDeviceTokenSuccessDto): UserProfile? {
        val fetched =
            try {
                httpClient.executeRequest<UserProfileNetwork> {
                    get("/user") {
                        header(HttpHeaders.Accept, "application/vnd.github+json")
                    }
                }
            } catch (e: RateLimitException) {
                Result.failure(e)
            }
        val userProfile =
            fetched.map { it.toUserProfile() }.getOrElse { error ->
                logger.error("Failed to fetch user profile: ${error.message}")
                return null
            }
        return try {
            cacheManager.put(CACHE_KEY, userProfile, USER_PROFILE)
            ownershipStamp(token)?.let { cacheManager.put(CACHE_OWNER_KEY, it, USER_PROFILE) }
            logger.debug("Fetched and cached user profile: ${userProfile.username}")
            userProfile
        } catch (e: SQLiteException) {
            logger.error("Failed to fetch user profile: ${e.message}")
            null
        }
    }

    private suspend fun publishIfCurrent(
        token: GithubDeviceTokenSuccessDto,
        profile: UserProfile,
    ): UserProfile? {
        if (!isCurrentToken(token)) return null
        recordSession(isLoggedIn = true, profile = profile)
        return profile
    }

    private suspend fun keepLastKnownProfile(token: GithubDeviceTokenSuccessDto) {
        if (!isCurrentToken(token)) return
        val previous = _lastKnownSession?.takeIf { it.isLoggedIn }?.profile
        if (previous != null) {
            recordSession(isLoggedIn = true, profile = previous)
        }
    }

    override suspend fun notifySessionExpired(tokenKey: String?) {
        if (tokenKey.isNullOrEmpty()) return
        sessionExpiredMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            if (tokenKey != failingTokenSnapshot ||
                now - firstFailureAtMillis > FAILURE_WINDOW_MS
            ) {
                failingTokenSnapshot = tokenKey
                firstFailureAtMillis = now
                consecutiveFailures = 1
            } else {
                consecutiveFailures += 1
            }

            if (consecutiveFailures < REQUIRED_CONSECUTIVE_FAILURES) {
                Logger.w(TAG) {
                    "notifySessionExpired: 401 count=$consecutiveFailures (need " +
                        "$REQUIRED_CONSECUTIVE_FAILURES); deferring sign-out"
                }
                return@withLock
            }

            val current = tokenStore.currentToken()?.accessToken
            if (current != tokenKey) {
                Logger.w(TAG) {
                    "notifySessionExpired: stored token rotated since the failing " +
                        "request; skipping clear"
                }
                resetCounter()
                return@withLock
            }

            Logger.w(TAG) {
                "notifySessionExpired: $consecutiveFailures consecutive 401s within " +
                    "window; clearing token"
            }
            tokenStore.clear()
            cacheManager.invalidate(CACHE_KEY)
            recordSession(isLoggedIn = false, profile = null)
            resetCounter()
            _sessionExpiredEvent.emit(Unit)
        }
    }

    override suspend fun notifyRequestSucceeded(tokenKey: String?) {
        if (tokenKey.isNullOrEmpty()) return
        sessionExpiredMutex.withLock {
            if (tokenKey == failingTokenSnapshot) {
                resetCounter()
            }
        }
    }

    private fun resetCounter() {
        failingTokenSnapshot = null
        firstFailureAtMillis = 0L
        consecutiveFailures = 0
    }

    override suspend fun logout() {
        tokenStore.clear()
        cacheManager.clearAll()
        recordSession(isLoggedIn = false, profile = null)
    }

    private companion object {
        const val TAG = "AuthState"
        const val REQUIRED_CONSECUTIVE_FAILURES = 2
        const val FAILURE_WINDOW_MS = 60_000L
        private const val CACHE_KEY = "profile:me"
        private const val CACHE_OWNER_KEY = "profile:me:owner"
    }
}
