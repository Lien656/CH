package ch.home.chat.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

/** Контекст устройства для Claude: батарея, сеть. Передаётся в system prompt. */
object DeviceContext {
    fun get(context: Context): String {
        val parts = mutableListOf<String>()
        parts.add("Батарея: ${getBattery(context)}")
        parts.add("Сеть: ${getNetwork(context)}")
        return parts.joinToString(". ")
    }

    private fun getBattery(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return "неизвестно"
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return "$level%" + if (charging) ", заряжается" else ""
    }

    private fun getNetwork(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "неизвестно"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "есть"
        val net = cm.activeNetwork ?: return "нет"
        val caps = cm.getNetworkCapabilities(net) ?: return "есть"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобильная"
            else -> "есть"
        }
    }
}
