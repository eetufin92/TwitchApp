package com.eetu.twitchapp.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class FilterListSubscription(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isRemovable: Boolean = true,
    val lastUpdate: Long = 0L,
    val ruleCount: Int = 0
)

data class AdBlockFilters(
    val version: Int = 1,
    val blockedDomains: List<String> = emptyList(),
    val blockedUrlKeywords: List<String> = emptyList(),
    val cosmeticCssSelectors: List<String> = emptyList()
)

class AdBlockManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("twitch_adblock_prefs", Context.MODE_PRIVATE)
    private val listsDir = File(context.filesDir, "adblock_lists").apply { mkdirs() }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val subscriptionListAdapter = moshi.adapter<List<FilterListSubscription>>(
        Types.newParameterizedType(List::class.java, FilterListSubscription::class.java)
    )

    companion object {
        @Volatile
        private var instance: AdBlockManager? = null

        fun getInstance(context: Context): AdBlockManager {
            return instance ?: synchronized(this) {
                instance ?: AdBlockManager(context.applicationContext).also { instance = it }
            }
        }

        @Volatile
        private var domainSet: Set<String> = emptySet()
        @Volatile
        private var keywordList: List<String> = emptyList()
        @Volatile
        private var cachedCss: String = ""

        // Domains that must NEVER be blocked to preserve Twitch and third-party emote functionality
        val PROTECTED_DOMAINS = setOf(
            "twitch.tv",
            "m.twitch.tv",
            "www.twitch.tv",
            "player.twitch.tv",
            "jtvnw.net",
            "ttvnw.net",
            "twitchcdn.net",
            "7tv.app",
            "7tv.io",
            "betterttv.net",
            "frankerfacez.com",
            "ivr.fi"
        )

        fun isProtectedDomain(host: String): Boolean {
            val h = host.lowercase().trim()
            return PROTECTED_DOMAINS.any { h == it || h.endsWith(".$it") }
        }

        // Only official uBlock Origin and EasyList filter lists
        val DEFAULT_SUBSCRIPTIONS = listOf(
            FilterListSubscription(
                id = "ublock_filters",
                name = "uBlock filters",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
                isEnabled = true,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "ublock_badware",
                name = "uBlock filters – Badware risks",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
                isEnabled = true,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "ublock_privacy",
                name = "uBlock filters – Privacy",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
                isEnabled = true,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "ublock_quick_fixes",
                name = "uBlock filters – Quick fixes",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/quick-fixes.txt",
                isEnabled = true,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "ublock_unbreak",
                name = "uBlock filters – Unbreak",
                url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt",
                isEnabled = true,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "easylist",
                name = "EasyList",
                url = "https://easylist.to/easylist/easylist.txt",
                isEnabled = true,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "easyprivacy",
                name = "EasyPrivacy",
                url = "https://easylist.to/easylist/easyprivacy.txt",
                isEnabled = true,
                isRemovable = false
            ),
            FilterListSubscription(
                id = "peter_lowe",
                name = "Peter Lowe’s Ad server list",
                url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
                isEnabled = true,
                isRemovable = false
            )
        )

        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        fun parseFilterContent(content: String): AdBlockFilters {
            val domains = mutableListOf<String>()
            val keywords = mutableListOf<String>()
            val cosmeticSelectors = mutableListOf<String>()

            content.lines().forEach { rawLine ->
                val l = rawLine.trim()
                if (l.isEmpty() || l.startsWith("!") || l.startsWith("[")) return@forEach

                // Network domain blocking rules: ||doubleclick.net^
                if (l.startsWith("||")) {
                    val ruleWithoutPrefix = l.removePrefix("||")
                    if (!ruleWithoutPrefix.contains("/")) {
                        val domain = ruleWithoutPrefix.substringBefore("^").substringBefore("$").trim().lowercase()
                        if (domain.isNotEmpty() && domain.contains(".") && !isProtectedDomain(domain) && !domain.contains("*")) {
                            domains.add(domain)
                        }
                    }
                    return@forEach
                }

                // Cosmetic element hiding rules: ##.ad-banner or twitch.tv##.ad-class
                if (l.contains("##")) {
                    val domainPart = l.substringBefore("##").trim().lowercase()
                    val selector = l.substringAfter("##").trim()

                    val isTwitchTarget = domainPart.isEmpty() || domainPart.split(",").any {
                        it.trim() == "twitch.tv" || it.trim() == "m.twitch.tv" || it.trim() == "www.twitch.tv"
                    }

                    // Never hide video player elements
                    val isPlayerElement = selector == "video" ||
                            selector.contains("video-player") ||
                            selector.contains("player-container")

                    if (isTwitchTarget && !isPlayerElement && selector.isNotEmpty() && !selector.startsWith("+js")) {
                        val isProcedural = selector.contains(":has(") ||
                                selector.contains(":has-text(") ||
                                selector.contains(":upward(") ||
                                selector.contains(":xpath(") ||
                                selector.contains("{") ||
                                selector.contains("}")

                        if (!isProcedural && selector.length < 200) {
                            cosmeticSelectors.add(selector)
                        }
                    }
                    return@forEach
                }
            }

            return AdBlockFilters(
                version = 1,
                blockedDomains = domains.distinct(),
                blockedUrlKeywords = keywords.distinct(),
                cosmeticCssSelectors = cosmeticSelectors.distinct()
            )
        }
    }

    init {
        compileActiveFilters()
    }

    fun isAdBlockEnabled(): Boolean = prefs.getBoolean("adblock_enabled", true)
    fun setAdBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("adblock_enabled", enabled).apply()
    }

    fun isAutoUpdateOnLaunch(): Boolean = prefs.getBoolean("auto_update_on_launch", true)
    fun setAutoUpdateOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean("auto_update_on_launch", enabled).apply()
    }

    fun getLastUpdateTime(): String {
        val timestamp = prefs.getLong("last_filter_update", 0L)
        return if (timestamp == 0L) "Never" else {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun getSubscriptions(): List<FilterListSubscription> {
        val json = prefs.getString("subscriptions_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val list = subscriptionListAdapter.fromJson(json)
                if (!list.isNullOrEmpty()) return list
            } catch (e: Exception) {
                android.util.Log.e("AdBlockManager", "Failed to parse subscriptions", e)
            }
        }
        return DEFAULT_SUBSCRIPTIONS
    }

    fun saveSubscriptions(subs: List<FilterListSubscription>) {
        val json = subscriptionListAdapter.toJson(subs)
        prefs.edit().putString("subscriptions_json", json).apply()
        compileActiveFilters()
    }

    fun toggleSubscription(id: String, enabled: Boolean) {
        val updated = getSubscriptions().map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveSubscriptions(updated)
    }

    @Synchronized
    fun compileActiveFilters() {
        val domains = mutableSetOf<String>()
        val cosmeticSelectors = mutableSetOf<String>()

        val subs = getSubscriptions().filter { it.isEnabled }
        for (sub in subs) {
            val file = File(listsDir, "${sub.id}.txt")
            if (file.exists()) {
                try {
                    val parsed = parseFilterContent(file.readText())
                    domains.addAll(parsed.blockedDomains)
                    cosmeticSelectors.addAll(parsed.cosmeticCssSelectors)
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Failed to compile list ${sub.id}", e)
                }
            }
        }

        domainSet = domains.map { it.lowercase() }.toSet()
        cachedCss = if (cosmeticSelectors.isEmpty()) "" else {
            cosmeticSelectors.joinToString("\n") {
                "$it { display: none !important; visibility: hidden !important; height: 0 !important; }"
            }
        }
    }

    fun isUrlBlocked(host: String?, url: String?): Boolean {
        if (!isAdBlockEnabled() || url == null) return false
        val lowerHost = host?.lowercase() ?: ""
        val lowerUrl = url.lowercase()

        // Never block core media, stream manifests, or emote CDNs
        if (isProtectedDomain(lowerHost)) return false
        if (lowerUrl.contains("/hls/") ||
            lowerUrl.contains(".m3u8") ||
            lowerUrl.contains(".ts") ||
            lowerUrl.contains("usher.ttvnw.net") ||
            lowerUrl.contains("gql.twitch.tv")
        ) {
            return false
        }

        for (domain in domainSet) {
            if (lowerHost == domain || lowerHost.endsWith(".$domain")) {
                return true
            }
        }

        return false
    }

    fun getCosmeticCss(): String = cachedCss

    suspend fun autoUpdateIfDue() = withContext(Dispatchers.IO) {
        if (!isAutoUpdateOnLaunch()) return@withContext
        val lastUpdate = prefs.getLong("last_filter_update", 0L)
        val twentyFourHours = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastUpdate > twentyFourHours) {
            updateAllFilters()
        }
    }

    suspend fun updateAllFilters(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val subs = getSubscriptions()
            val updatedSubs = mutableListOf<FilterListSubscription>()
            var atLeastOneSuccess = false

            for (sub in subs) {
                if (!sub.isEnabled) {
                    updatedSubs.add(sub)
                    continue
                }

                try {
                    val request = Request.Builder()
                        .url(sub.url)
                        .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body.string()
                        if (body.isNotEmpty()) {
                            val parsed = parseFilterContent(body)
                            val ruleCount = parsed.blockedDomains.size + parsed.cosmeticCssSelectors.size
                            File(listsDir, "${sub.id}.txt").writeText(body)
                            updatedSubs.add(sub.copy(lastUpdate = System.currentTimeMillis(), ruleCount = ruleCount))
                            atLeastOneSuccess = true
                            continue
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Failed to update list ${sub.name}", e)
                }

                updatedSubs.add(sub)
            }

            saveSubscriptions(updatedSubs)
            prefs.edit().putLong("last_filter_update", System.currentTimeMillis()).apply()

            if (atLeastOneSuccess) Result.success(Unit) else Result.failure(Exception("Failed to update lists"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
