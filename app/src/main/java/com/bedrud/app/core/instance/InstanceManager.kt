package com.bedrud.app.core.instance

import android.app.Application
import com.bedrud.app.core.api.AdminApi
import com.bedrud.app.core.api.ApiClientFactory
import com.bedrud.app.core.api.AuthApi
import com.bedrud.app.core.api.AuthInterceptor
import com.bedrud.app.core.api.RoomApi
import com.bedrud.app.core.api.TokenAuthenticator
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.auth.PasskeyManager
import com.bedrud.app.core.livekit.RoomManager
import com.bedrud.app.models.HealthResponse
import com.bedrud.app.models.Instance
import com.bedrud.app.models.PublicSettings
import com.bedrud.app.ui.screens.settings.SettingsStore
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** How long to wait for the public-settings fetch before giving up and falling back to defaults. */
private const val SETTINGS_TIMEOUT_MS = 8_000L

/** Connect/read timeout for the lightweight server health probe (shorter than the main API client). */
private const val HEALTH_CHECK_TIMEOUT_SECONDS = 10L

/** Load state of the active server's public settings (see [InstanceManager.publicSettings]). */
sealed interface PublicSettingsState {
    data object Loading : PublicSettingsState
    data class Loaded(val settings: PublicSettings) : PublicSettingsState
    data object Failed : PublicSettingsState
}

class InstanceManager(
    private val application: Application,
    val store: InstanceStore,
    private val settingsStore: SettingsStore,
) {
    private val _authManager = MutableStateFlow<AuthManager?>(null)
    val authManager: StateFlow<AuthManager?> = _authManager.asStateFlow()

    private val _authApi = MutableStateFlow<AuthApi?>(null)
    val authApi: StateFlow<AuthApi?> = _authApi.asStateFlow()

    private val _roomApi = MutableStateFlow<RoomApi?>(null)
    val roomApi: StateFlow<RoomApi?> = _roomApi.asStateFlow()

    private val _passkeyManager = MutableStateFlow<PasskeyManager?>(null)
    val passkeyManager: StateFlow<PasskeyManager?> = _passkeyManager.asStateFlow()

    private val _roomManager = MutableStateFlow<RoomManager?>(null)
    val roomManager: StateFlow<RoomManager?> = _roomManager.asStateFlow()

    private val _adminApi = MutableStateFlow<AdminApi?>(null)
    val adminApi: StateFlow<AdminApi?> = _adminApi.asStateFlow()

    // Public settings for the active server, fetched on activation (init/add/switch) so screens like
    // the sign-in hub render ready — whichever route reaches them — instead of loading per-screen.
    private val _publicSettings = MutableStateFlow<PublicSettingsState>(PublicSettingsState.Loading)
    val publicSettings: StateFlow<PublicSettingsState> = _publicSettings.asStateFlow()

    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settingsJob: Job? = null

    init {
        rebuild()
    }

    fun rebuild() {
        val instance = store.activeInstance ?: run {
            _authManager.value = null
            _authApi.value = null
            _roomApi.value = null
            _passkeyManager.value = null
            _roomManager.value = null
            _adminApi.value = null
            settingsJob?.cancel()
            _publicSettings.value = PublicSettingsState.Loading
            return
        }

        val baseURL = instance.apiBaseURL
        val am = AuthManager(application, instance.id)
        val factory = ApiClientFactory(baseURL)

        val interceptor = AuthInterceptor(am)
        val authenticator = TokenAuthenticator(am, baseURL) {
            _authApi.value ?: error("AuthApi not yet initialized — token refresh attempted before setup completed")
        }
        val okHttp = factory.createOkHttpClient(interceptor, authenticator)
        val retrofit = factory.createRetrofit(okHttp)

        val auth: AuthApi = factory.createApi(retrofit)
        val room: RoomApi = factory.createApi(retrofit)
        val admin: AdminApi = factory.createApi(retrofit)
        val pk = PasskeyManager(application, auth, am)
        val rm = RoomManager(application, settingsStore)

        _authManager.value = am
        _authApi.value = auth
        _roomApi.value = room
        _adminApi.value = admin
        _passkeyManager.value = pk
        _roomManager.value = rm

        refreshPublicSettings(auth, instance.id)
    }

    /** Kicks off a fetch of the active server's public settings, published via [publicSettings]. */
    private fun refreshPublicSettings(api: AuthApi, instanceId: String) {
        settingsJob?.cancel()
        _publicSettings.value = PublicSettingsState.Loading
        settingsJob = settingsScope.launch {
            _publicSettings.value = try {
                val response = withTimeoutOrNull(SETTINGS_TIMEOUT_MS) { api.getPublicSettings() }
                if (response != null && response.isSuccessful && response.body() != null) {
                    val settings = response.body()!!
                    // Adopt the server's own name as the instance's display name, so the brand mark
                    // and every instance list show the operator's branding, not the URL-derived name.
                    // Captured instanceId, not the live active one, so a mid-flight switch can't
                    // rename the wrong server.
                    settings.serverName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                        store.updateDisplayName(instanceId, name)
                    }
                    PublicSettingsState.Loaded(settings)
                } else {
                    PublicSettingsState.Failed
                }
            } catch (_: Exception) {
                PublicSettingsState.Failed
            }
        }
    }

    /**
     * Suspends until the active server's public settings finish loading (or the fetch gives up).
     * Bounded so it never blocks the caller indefinitely — used before navigating into the sign-in
     * hub so it renders ready.
     */
    suspend fun awaitPublicSettings() {
        withTimeoutOrNull(SETTINGS_TIMEOUT_MS) {
            publicSettings.first { it !is PublicSettingsState.Loading }
        }
    }

    fun switchTo(instanceId: String) {
        store.setActive(instanceId)
        rebuild()
    }

    fun removeInstance(id: String) {
        if (store.activeInstanceId.value == id) {
            _authManager.value?.logout()
        }
        store.removeInstance(id)
        rebuild()
    }

    suspend fun checkHealth(serverURL: String): HealthResponse {
        val baseURL = if (serverURL.endsWith("/")) {
            "$serverURL${Instance.API_PATH_SEGMENT}"
        } else {
            "$serverURL/${Instance.API_PATH_SEGMENT}"
        }
        val plainClient = OkHttpClient.Builder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val gson = GsonBuilder().setLenient().create()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseURL.trimEnd('/') + "/")
            .client(plainClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        val api = retrofit.create(HealthApi::class.java)
        val response = api.health()
        if (response.isSuccessful) {
            return response.body() ?: HealthResponse()
        } else {
            throw Exception("Server returned ${response.code()}")
        }
    }

    suspend fun addInstance(serverURL: String, displayName: String) {
        checkHealth(serverURL)
        val instance = com.bedrud.app.models.Instance(
            serverURL = serverURL,
            displayName = displayName
        )
        store.addInstance(instance)
        store.setActive(instance.id)
        rebuild()
    }
}

interface HealthApi {
    @retrofit2.http.GET("health")
    suspend fun health(): retrofit2.Response<HealthResponse>
}
