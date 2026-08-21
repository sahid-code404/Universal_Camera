package com.sahidcode404.camera.updater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object SigningCertificate {
    fun currentSha256(context: Context): String {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        val signingInfo = requireNotNull(info.signingInfo)
        val signature = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners.first()
        else signingInfo.signingCertificateHistory.first()
        return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
