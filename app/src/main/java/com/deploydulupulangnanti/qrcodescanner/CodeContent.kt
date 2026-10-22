package com.deploydulupulangnanti.qrcodescanner

import java.net.URI

enum class ContentKind(val label: String) { LINK("Website"), WIFI("Wi-Fi"), EMAIL("Email"), PHONE("Phone"), CONTACT("Contact"), TEXT("Text"), PRODUCT("Barcode") }

object CodeContent {
    fun kind(value: String, format: String = "QR_CODE"): ContentKind = when {
        value.startsWith("WIFI:", true) -> ContentKind.WIFI
        value.startsWith("BEGIN:VCARD", true) || value.startsWith("MECARD:", true) -> ContentKind.CONTACT
        value.startsWith("mailto:", true) -> ContentKind.EMAIL
        value.startsWith("tel:", true) -> ContentKind.PHONE
        webUrl(value) != null -> ContentKind.LINK
        format in setOf("EAN_13", "EAN_8", "UPC_A", "UPC_E", "ISBN") -> ContentKind.PRODUCT
        else -> ContentKind.TEXT
    }
    fun webUrl(value: String): String? = runCatching {
        val uri = URI(value.trim())
        value.trim().takeIf { uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null }
    }.getOrNull()
    fun title(value: String, format: String): String = when (kind(value, format)) {
        ContentKind.LINK -> runCatching { URI(value.trim()).host }.getOrDefault("Website")
        ContentKind.WIFI -> "Wi-Fi network"
        ContentKind.CONTACT -> "Contact details"
        else -> value.removePrefix("mailto:").removePrefix("tel:").lineSequence().firstOrNull().orEmpty().take(90)
    }
    private fun wifiEscape(value: String) = buildString { value.forEach { if (it in "\\;,:\"") append('\\'); append(it) } }
    fun wifi(ssid: String, password: String, secured: Boolean = true, hidden: Boolean = false): String {
        require(ssid.isNotBlank()) { "Enter a network name." }
        require(ssid.toByteArray(Charsets.UTF_8).size <= 32) { "Network names must be at most 32 UTF-8 bytes." }
        require(!secured || password.length in 8..63 || Regex("^[0-9a-fA-F]{64}$").matches(password)) { "WPA passwords need 8–63 characters or a 64-digit hexadecimal key." }
        return "WIFI:T:${if (secured) "WPA" else "nopass"};S:${wifiEscape(ssid)};P:${if (secured) wifiEscape(password) else ""};H:$hidden;;"
    }
    fun create(type: String, text: String, extra: String = "", secured: Boolean = true): String {
        require(text.isNotBlank()) { "Add some content first." }
        return when (type) {
            "Website" -> {
                val url = if (text.contains("://")) text.trim() else "https://${text.trim()}"
                require(webUrl(url) != null) { "Enter a valid http or https website." }; url
            }
            "Wi-Fi" -> wifi(text, extra, secured)
            "Email" -> { require(Regex("^[^\\s@?]+@[^\\s@?]+\\.[^\\s@?]+$").matches(text.trim())) { "Enter a valid email address." }; "mailto:${text.trim()}" }
            "Phone" -> { require(Regex("^[+0-9() .-]{3,40}$").matches(text.trim())) { "Enter a valid phone number." }; "tel:${text.trim()}" }
            else -> text
        }
    }
    fun csvCell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
