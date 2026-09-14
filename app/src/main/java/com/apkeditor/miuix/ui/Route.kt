package com.apkeditor.miuix.ui

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * Type-safe navigation keys for the app, backed by miuix-nav.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object Home : Route

    @Serializable
    data object SavedApks : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route

    @Serializable
    data class ApkInfo(val apkPath: String) : Route

    @Serializable
    data class SmaliTree(val apkPath: String, val dexNames: List<String> = emptyList()) : Route

    @Serializable
    data class SmaliEdit(val apkPath: String, val dexName: String, val filePath: String) : Route

    @Serializable
    data class ArscTypes(val apkPath: String) : Route

    @Serializable
    data class ArscEntries(val apkPath: String, val type: String) : Route

    @Serializable
    data object XmlFiles : Route

    @Serializable
    data class XmlEdit(val path: String) : Route

    @Serializable
    data object UiSettings : Route

    @Serializable
    data object ThirdPartyLicenses : Route
}
