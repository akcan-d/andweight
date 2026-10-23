package com.measured.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Thin wrapper around the Health Connect SDK, scoped to the one record type this app cares
 * about - body weight - so the rest of the app only ever deals in plain kg/[LocalDate], never the
 * SDK's own [Mass]/[Instant] types or its availability quirks.
 */
class HealthConnectManager(context: Context) {

    private val appContext = context.applicationContext

    enum class Availability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNSUPPORTED }

    /** Re-checked on every access rather than cached - cheap, and means installing/updating the
     * Health Connect app from Settings is picked up without needing a process restart. */
    val availability: Availability
        get() = when (HealthConnectClient.getSdkStatus(appContext)) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_INSTALL_OR_UPDATE
            else -> Availability.UNSUPPORTED
        }

    private fun clientOrNull(): HealthConnectClient? =
        if (availability == Availability.AVAILABLE) HealthConnectClient.getOrCreate(appContext) else null

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    /** Hands the launcher this registers to [androidx.activity.compose.rememberLauncherForActivityResult]. */
    fun requestPermissionsContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasAllPermissions(): Boolean {
        val client = clientOrNull() ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    /** Takes the user to the Health Connect app's own Play Store listing, for when
     * [availability] is [Availability.NEEDS_INSTALL_OR_UPDATE]. */
    fun playStoreIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))

    /** Writes today's logged weight into Health Connect so any other app the user has granted
     * read access to (or a future smart scale) can see it. Quietly no-ops without permission -
     * this always runs right after a normal in-app save, not a flow where failing silently would
     * hide something the user did on purpose. */
    suspend fun writeWeight(date: LocalDate, weightKg: Double) {
        val client = clientOrNull() ?: return
        if (!hasAllPermissions()) return
        val zoneId = ZoneId.systemDefault()
        val time = date.atStartOfDay(zoneId).toInstant()
        client.insertRecords(
            listOf(
                WeightRecord(
                    time = time,
                    zoneOffset = zoneId.rules.getOffset(time),
                    weight = Mass.kilograms(weightKg),
                ),
            ),
        )
    }

    /** The most recent weight reading per calendar day over the last [days] days, as written by
     * any app with Health Connect write access (a smart scale's own app, for instance) - callers
     * decide what to do with days that don't already have a weight logged here. Empty without
     * permission, same as [writeWeight]. */
    suspend fun readRecentWeights(days: Int = 30): List<Pair<LocalDate, Double>> {
        val client = clientOrNull() ?: return emptyList()
        if (!hasAllPermissions()) return emptyList()
        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()
        val start = now.minusSeconds(days * 24L * 60 * 60)
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now),
            ),
        )
        return response.records
            .groupBy { it.time.atZone(zoneId).toLocalDate() }
            .map { (date, records) -> date to records.maxBy { it.time }.weight.inKilograms }
            .sortedBy { it.first }
    }
}
