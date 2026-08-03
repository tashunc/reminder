package com.reminder.app.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

sealed interface DriveAuthorizationResult {
    data class Granted(val accessToken: String) : DriveAuthorizationResult
    data class ConsentRequired(val pendingIntent: PendingIntent) : DriveAuthorizationResult
}

/**
 * Wraps Google Identity Services' Authorization API to get an OAuth access
 * token scoped to the user's Drive App Data folder (see
 * docs/drive-storage.md). Requesting this scope is also how the user
 * "signs in" for this app — there's no separate identity/profile step in
 * this first pass, so we don't yet show the signed-in account's email;
 * that would come from a later Credential Manager sign-in step layered on
 * top of this.
 */
class AuthManager(context: Context) {

    private val authorizationClient = Identity.getAuthorizationClient(context)
    private var cachedAccessToken: String? = null

    suspend fun requestDriveAuthorization(): DriveAuthorizationResult {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()

        val result = authorizationClient.authorize(request).await()
        return if (result.hasResolution()) {
            DriveAuthorizationResult.ConsentRequired(
                result.pendingIntent ?: error("hasResolution() was true but pendingIntent was null"),
            )
        } else {
            val token = result.accessToken ?: error("Authorization granted without an access token")
            cachedAccessToken = token
            DriveAuthorizationResult.Granted(token)
        }
    }

    /** Call from the Activity's IntentSender result launcher after the consent UI returns. */
    fun onAuthorizationConsentResult(data: Intent?): DriveAuthorizationResult {
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        val token = result.accessToken ?: error("Consent flow completed without an access token")
        cachedAccessToken = token
        return DriveAuthorizationResult.Granted(token)
    }

    /**
     * Returns the cached token from the most recent authorization. Callers
     * are expected to have completed [requestDriveAuthorization] (and its
     * consent flow, if required) at least once per app session before
     * calling repository methods.
     */
    fun getDriveAccessTokenOrThrow(): String {
        return cachedAccessToken
            ?: error("No Drive access token yet; call requestDriveAuthorization() from the sign-in screen first")
    }
}
