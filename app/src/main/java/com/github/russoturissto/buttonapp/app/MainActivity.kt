package com.github.russoturissto.buttonapp.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.IntentFilter
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.app.DownloadManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.appcompat.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.GridLayout
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import java.util.Date
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val preferences by lazy { getSharedPreferences("balancehub_data", MODE_PRIVATE) }
    private val payments = mutableListOf<PaymentReminder>()
    private val paymentBaseline = mutableMapOf<String, String>()
    private lateinit var titleInput: EditText
    private lateinit var amountInput: EditText
    private lateinit var currencyInput: EditText
    private lateinit var leadDaysInput: EditText
    private lateinit var dateButton: Button
    private lateinit var recurrenceInput: Spinner
    private lateinit var listContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var dashboardScroll: android.widget.ScrollView
    private var measuredDashboardViewportDp: Int = 0
    private fun dashboardSizing(weeks: Int = 5): HomeDashboardSize {
        val viewport = measuredDashboardViewportDp
        return if (viewport > 0) HomeDashboardSizing.forViewport(
            viewport, resources.configuration.fontScale, weeks)
        else HomeDashboardSizing.forHeight(
            resources.configuration.screenHeightDp, resources.configuration.fontScale)
    }
    private var tabViews: List<View> = emptyList()
    private var bottomNavigation: BalanceBottomNavigation? = null
    private var currentTab = 0
    private var mobileSettingsOpen = false
    private var bankSettingsOpen = false
    private var engineeringMenuOpen = false
    private var engineeringChannelsOpen = false
    private var engineeringChannelCategory: String? = null
    private var engineeringBanksOpen = false
    private var engineeringNotificationsOpen = false
    private var engineeringDeliveryOpen = false
    private var engineeringWinterOpen = false
    private var engineeringOtherBankId: String? = null
    private var engineeringTapCount = 0
    private var engineeringTapStartedAt = 0L
    private var operatorDetailsOpen = false
    private var archiveOpen = false
    private var expandedArchivePaymentId: String? = null
    private var eventArchiveExpanded = false
    private var expandedArchiveEventId: String? = null
    private var eventListExpanded = false
    private var soundSettingsExpanded = false
    private val settingsStack = mutableListOf<String>()
    private val settingsScrollPositions = mutableMapOf<String, Int>()
    private var expandedEventId: String? = null
    private var selectedPaymentIcon: String? = null
    private var welcomeShowing = false
    private var appLockUnlocked = false
    private var appLockScreenVisible = false
    private var appLockMandatoryScreen = false
    private var appLockBackgroundAt = 0L
    private var appLockScreenTurnedOff = false
    private var fingerprintSignal: CancellationSignal? = null
    private var fingerprintDialog: AlertDialog? = null
    private val lockScreenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Conservative on Android 11: a screen-off is treated as a lock
                // when the user selected "after locking the device".
                appLockScreenTurnedOff = true
            }
        }
    }
    private var checkingGithubUpdates = false
    private var updateDialogOpen = false
    private var pendingGithubInstall = false
    private var lastExactAlarmState: Boolean? = null
    private var pendingSignalRepair: Pair<String, String>? = null
    private var signalRepairCheckAfter: Long = 0L
    private var deferredUpdate: UpdateRelease? = null
    private val downloadedApkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val release = GithubUpdateEngine.cached(this@MainActivity) ?: return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val state = GithubApkInstaller.status(this@MainActivity, release) ?: return
            if (state.id != id || state.status != DownloadManager.STATUS_SUCCESSFUL) return
            val prefs = GithubUpdateEngine.preferences(this@MainActivity)
            if (prefs.getLong("completed_download_offer", 0L) >= release.code) return
            prefs.edit { putLong("completed_download_offer", release.code) }
            if (updateDialogOpen) return // The visible dialog is already polling progress.
            if (welcomeShowing || !appLockUnlocked || appLockScreenVisible)
                deferredUpdate = release
            else window.decorView.post { showGithubUpdateDialog(release) }
        }
    }
    private var upcomingListExpanded = false
    private var homeSelectedDay: Int? = null
    private val homeCalendarMonth: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    private var expandedPaymentId: String? = null
    private var selectedOperatorId: Int? = null
    private var sessionAutoRefreshStarted = false
    private var tutorialStep = -1
    private var renderedWinter = false
    private val winterDateHandler = Handler(Looper.getMainLooper())
    private val winterDateTick = object : Runnable {
        override fun run() {
            refreshWinterOnDateChange()
            scheduleWinterDateTick()
        }
    }
    private fun scheduleWinterDateTick() {
        winterDateHandler.removeCallbacks(winterDateTick)
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 1)
            set(Calendar.MILLISECOND, 0)
        }
        winterDateHandler.postDelayed(winterDateTick,
            (midnight.timeInMillis - System.currentTimeMillis()).coerceAtLeast(1_000L))
    }
    private fun refreshWinterOnDateChange() {
        if (renderedWinter == winterActive || isFinishing || welcomeShowing) return
        if (appLockScreenVisible) {
            if (!appLockUnlocked) showLockedScreen()
        } else if (appLockUnlocked) {
            val priorScroll = if (currentTab == 4 && ::dashboardScroll.isInitialized)
                dashboardScroll.scrollY else 0
            showDashboard()
            if (currentTab == 4) dashboardScroll.post { dashboardScroll.scrollTo(0, priorScroll) }
        }
    }
    private var releaseHistoryVersions: List<String> = emptyList()
    private var releaseHistoryRefreshStarted = false
    private var expandedReleaseHistoryVersion: String? = null
    private val tutorialHighlightedViews =
        linkedMapOf<View, Pair<android.graphics.drawable.Drawable?, Float>>()
    // Match the approved header position on every tab and in every theme.
    // Never derive this padding from transient window insets during recreation.
    private val headerTopPadding get() = dp(36)
    private val bankUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SberPushNotificationListener.ACTION_BALANCE_UPDATED &&
                (currentTab == 1 || (currentTab == 4 &&
                    (bankSettingsOpen || engineeringOtherBankId != null))) &&
                ::contentContainer.isInitialized) renderTab()
        }
    }
    private val calendarChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_DATE_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED) {
                refreshWinterOnDateChange()
                scheduleWinterDateTick()
                if (::contentContainer.isInitialized && currentTab == 0) renderTab()
            }
        }
    }
    private val paymentChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PaymentArchiveReceiver.ACTION_PAYMENTS_CHANGED) return
            payments.clear()
            loadPayments()
            if (::contentContainer.isInitialized && (currentTab == 0 || currentTab == 2))
                renderTab()
        }
    }
    private val eventChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == EventScheduler.ACTION_EVENTS_CHANGED &&
                ::contentContainer.isInitialized && (currentTab == 0 || currentTab == 3)) {
                if (currentTab == 3) {
                    val activeIds = EventStore.active(this@MainActivity).map { it.id }.toSet()
                    expandedEventId = expandedEventId?.takeIf { it in activeIds }
                }
                renderTab()
            }
        }
    }
    private var editingId: String? = null
    private var pendingPaymentPlan: PaymentPlan? = null
    private var dueDate = ReminderDateTimePolicy.atOpening()

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (getSharedPreferences("balancehub_data", MODE_PRIVATE)
            .getString("appearance", "system")) {
            "day" -> AppCompatDelegate.MODE_NIGHT_NO
            "night" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)
        if (AppLockSecurity.supported()) {
            registerReceiver(lockScreenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        currentTab = (savedInstanceState?.getInt("current_tab")
            ?: intent.getIntExtra("open_tab", 0)).coerceIn(0, 4)
        mobileSettingsOpen = savedInstanceState?.getBoolean("mobile_settings_open") ?: false
        savedInstanceState?.getStringArrayList("settings_stack")?.let {
            settingsStack.addAll(SettingsNavigationPolicy.sanitize(it))
        }
        settingsScrollPositions[settingsScreen()] = savedInstanceState?.getInt("settings_scroll", 0) ?: 0
        // Engineer access is never restored from saved state, disk, or deep links.
        bankSettingsOpen = false
        engineeringMenuOpen = false
        engineeringChannelsOpen = false
        engineeringChannelCategory = null
        engineeringBanksOpen = false
        engineeringNotificationsOpen = false
        engineeringDeliveryOpen = false
        engineeringOtherBankId = null
        operatorDetailsOpen = savedInstanceState?.getBoolean("operator_details_open") ?: false
        archiveOpen = savedInstanceState?.getBoolean("archive_open") ?: false
        expandedArchivePaymentId = savedInstanceState?.getString("expanded_archive_payment_id")
        eventArchiveExpanded = savedInstanceState?.getBoolean("event_archive_open") ?: false
        expandedArchiveEventId = savedInstanceState?.getString("expanded_archive_event_id")
        eventListExpanded = savedInstanceState?.getBoolean("event_list_expanded")
            ?: preferences.getBoolean("event_list_expanded", false)
        soundSettingsExpanded = savedInstanceState?.getBoolean("sound_settings_expanded")
            ?: preferences.getBoolean("sound_settings_expanded", false)
        expandedEventId = savedInstanceState?.getString("expanded_event_id")
        upcomingListExpanded = savedInstanceState?.getBoolean("payments_list_expanded")
            ?: preferences.getBoolean("payments_list_expanded", false)
        expandedPaymentId = savedInstanceState?.getString("expanded_payment_id")
        // New install or upgraded APK: collapse both sections once, while
        // preserving all payments, events and choices on ordinary relaunches.
        val installedCode = PackageInfoCompat.getLongVersionCode(
            packageManager.getPackageInfo(packageName, 0))
        val lastListVersion = preferences.getLong("upcoming_lists_last_seen_code", -1L)
        if (UpcomingListVersionPolicy.shouldCollapse(installedCode, lastListVersion)) {
            upcomingListExpanded = false
            eventListExpanded = false
            expandedPaymentId = null
            expandedEventId = null
            preferences.edit(commit = true) {
                putBoolean("payments_list_expanded", false)
                putBoolean("event_list_expanded", false)
                putLong("upcoming_lists_last_seen_code", installedCode)
            }
        }
        homeSelectedDay = savedInstanceState?.getInt("home_selected_day", -1)
            ?.takeIf { it in 1..31 }
        savedInstanceState?.getLong("home_calendar_month", 0L)
            ?.takeIf { it > 0L }?.let { homeCalendarMonth.timeInMillis = it }
        selectedOperatorId = savedInstanceState?.getInt("selected_operator_id", Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        tutorialStep = savedInstanceState?.getInt("tutorial_step") ?:
            if (preferences.getBoolean("onboarding_completed", false)) -1 else 0
        PaymentArchiveStore.archiveDue(this)
        EventScheduler.restoreAfterBoot(this)
        loadPayments()
        for (sim in T2Balance.sims(this)) syncMobilePayment(sim)
        BalanceNotificationSounds.ensureDefaultChannels(this)
        appLockUnlocked = !AppLockSecurity.enabled(this)
        applyAppLockWindowPrivacy()
        if (appLockUnlocked) {
            showDashboard()
            openNotificationTarget(intent)
            if (currentTab == 4) dashboardScroll.post {
                dashboardScroll.scrollTo(0, settingsScrollPositions[settingsScreen()] ?: 0)
            }
        } else showLockedScreen()
        GithubApkInstaller.cleanOld(this)
        GithubUpdateEngine.schedule(this)
        window.decorView.post {
            if (appLockUnlocked) afterAppLockUnlocked()
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    appLockScreenVisible -> moveTaskToBack(true)
                    archiveOpen && currentTab == 2 -> {
                        archiveOpen = false
                        expandedArchivePaymentId = null
                        renderTab()
                    }
                    eventArchiveExpanded && currentTab == 3 -> {
                        eventArchiveExpanded = false
                        expandedArchiveEventId = null
                        renderTab()
                    }
                    operatorDetailsOpen && currentTab == 1 -> {
                        operatorDetailsOpen = false
                        renderTab()
                    }
                    engineeringOtherBankId != null && engineeringMenuOpen &&
                        currentTab == 4 -> {
                        engineeringOtherBankId = null
                        renderTab()
                    }
                    bankSettingsOpen && engineeringMenuOpen && currentTab == 4 -> {
                        bankSettingsOpen = false
                        renderTab()
                    }
                    engineeringBanksOpen && engineeringMenuOpen && currentTab == 4 -> {
                        engineeringBanksOpen = false
                        renderTab()
                    }
                    engineeringNotificationsOpen && engineeringMenuOpen && currentTab == 4 -> {
                        engineeringNotificationsOpen = false
                        renderTab()
                    }
                    engineeringDeliveryOpen && engineeringMenuOpen && currentTab == 4 -> {
                        engineeringDeliveryOpen = false
                        renderTab()
                    }
                    engineeringWinterOpen && engineeringMenuOpen && currentTab == 4 -> {
                        engineeringWinterOpen = false
                        renderTab()
                    }
                    engineeringChannelCategory != null && engineeringMenuOpen &&
                        currentTab == 4 -> {
                        engineeringChannelCategory = null
                        renderTab()
                    }
                    engineeringChannelsOpen && engineeringMenuOpen && currentTab == 4 -> {
                        engineeringChannelsOpen = false
                        renderTab()
                    }
                    engineeringMenuOpen && currentTab == 4 -> {
                        closeEngineeringMenu()
                        renderTab()
                    }
                    mobileSettingsOpen && currentTab == 4 -> {
                        mobileSettingsOpen = false
                        if (settingsStack.lastOrNull() == "mobile") backSettingsPage()
                        else renderTab()
                    }
                    settingsStack.isNotEmpty() && currentTab == 4 -> backSettingsPage()
                    // Back from any top-level section always returns Home, not
                    // the previously visited tab or the Android launcher.
                    currentTab != 0 -> selectTab(0)
                    // At Home, leave the task alive with its local data and
                    // navigation state, just as pressing Android Home would.
                    else -> moveTaskToBack(true)
                }
            }
        })
    }


    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, bankUpdateReceiver,
            IntentFilter(SberPushNotificationListener.ACTION_BALANCE_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, eventChangedReceiver,
            IntentFilter(EventScheduler.ACTION_EVENTS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, paymentChangedReceiver,
            IntentFilter(PaymentArchiveReceiver.ACTION_PAYMENTS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, calendarChangedReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, downloadedApkReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED)
        scheduleWinterDateTick()
    }

    override fun onStop() {
        if (appLockUnlocked && AppLockSecurity.enabled(this)) {
            appLockBackgroundAt = SystemClock.elapsedRealtime()
        }
        fingerprintSignal?.cancel()
        fingerprintSignal = null
        fingerprintDialog?.dismiss()
        fingerprintDialog = null
        if (appLockMandatoryScreen) appLockScreenVisible = false
        closeEngineeringMenu()
        BalanceNotificationSounds.stopPreview()
        winterDateHandler.removeCallbacks(winterDateTick)
        unregisterReceiver(bankUpdateReceiver)
        unregisterReceiver(eventChangedReceiver)
        unregisterReceiver(paymentChangedReceiver)
        unregisterReceiver(calendarChangedReceiver)
        unregisterReceiver(downloadedApkReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (AppLockSecurity.enabled(this)) {
            val elapsed = if (appLockBackgroundAt > 0L)
                (SystemClock.elapsedRealtime() - appLockBackgroundAt).coerceAtLeast(0L)
            else 0L
            val lockedByDevice = appLockScreenTurnedOff ||
                (getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager)
                    .isDeviceLocked
            if (appLockBackgroundAt > 0L &&
                AppLockPolicy.shouldRelock(AppLockSecurity.relock(this),
                    elapsed, lockedByDevice)) appLockUnlocked = false
            if (!appLockUnlocked) {
                applyAppLockWindowPrivacy()
                // A PIN setup/settings screen is not a valid unlock screen.
                if (!appLockScreenVisible || !appLockMandatoryScreen) showLockedScreen()
                return
            }
        }
        PaymentArchiveStore.archiveDue(this)
        // The background receiver may already have archived a payment while the
        // activity was paused. Always reconcile the in-memory list with history.
        val archivedIds = PaymentArchiveStore.archived(this).map { it.id }.toSet()
        if (archivedIds.isNotEmpty()) payments.removeAll { it.id in archivedIds }
        val beforeEvents = EventStore.active(this).associate { it.id to it.dueAt }
        EventStore.reconcileElapsed(this)
        val exact = ExactReminderAlarms.allowed(this)
        val permissionChanged = lastExactAlarmState != exact
        lastExactAlarmState = exact
        if (permissionChanged) payments.forEach(::scheduleReminder)
        for (event in EventStore.active(this)) {
            if (permissionChanged || beforeEvents[event.id] != event.dueAt)
                EventScheduler.schedule(this, event)
        }
        if (pendingGithubInstall && GithubApkInstaller.allowedToInstall(this)) {
            pendingGithubInstall = false
            GithubUpdateEngine.cached(this)?.let { tryInstallUpdate(it) }
        }
        val themeChanged = renderedWinter != winterActive
        if (themeChanged) refreshWinterOnDateChange()
        if (!themeChanged && ::contentContainer.isInitialized &&
            (currentTab == 0 || currentTab == 1 || currentTab == 3 || currentTab == 4)) renderTab()
    }

    override fun onDestroy() {
        fingerprintSignal?.cancel()
        fingerprintSignal = null
        if (AppLockSecurity.supported()) unregisterReceiver(lockScreenOffReceiver)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingSignalRepair != null) {
            Handler(Looper.getMainLooper()).postDelayed({ verifyPendingSignalRepair() }, 300L)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 510 || requestCode == 511) &&
            (currentTab == 1 || (currentTab == 4 && mobileSettingsOpen))) renderTab()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("current_tab", currentTab)
        outState.putBoolean("mobile_settings_open", mobileSettingsOpen)
        // Never serialize engineering access or its internal screen.
        outState.putBoolean("bank_settings_open", false)
        outState.putBoolean("operator_details_open", operatorDetailsOpen)
        outState.putBoolean("archive_open", archiveOpen)
        outState.putString("expanded_archive_payment_id", expandedArchivePaymentId)
        outState.putBoolean("event_archive_open", eventArchiveExpanded)
        outState.putString("expanded_archive_event_id", expandedArchiveEventId)
        outState.putBoolean("event_list_expanded", eventListExpanded)
        outState.putBoolean("sound_settings_expanded", soundSettingsExpanded)
        outState.putStringArrayList("settings_stack", ArrayList(settingsStack))
        if (::dashboardScroll.isInitialized && currentTab == 4) {
            outState.putInt("settings_scroll", dashboardScroll.scrollY)
        }
        outState.putString("expanded_event_id", expandedEventId)
        outState.putBoolean("payments_list_expanded", upcomingListExpanded)
        outState.putString("expanded_payment_id", expandedPaymentId)
        outState.putInt("home_selected_day", homeSelectedDay ?: -1)
        outState.putLong("home_calendar_month", homeCalendarMonth.timeInMillis)
        selectedOperatorId?.let { outState.putInt("selected_operator_id", it) }
        outState.putInt("tutorial_step", tutorialStep)
        super.onSaveInstanceState(outState)
    }

    private val isNight: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    private val winterActive: Boolean get() = WinterThemeState.active(this)
    private fun winterDrawable(original: android.graphics.drawable.Drawable,
        surface: WinterSurface): android.graphics.drawable.Drawable =
        if (winterActive) WinterFrostDrawable(original, resources.displayMetrics.density,
            isNight, surface) else original
    private val ink: Int get() = if (winterActive) Color.rgb(241, 250, 253)
        else if (isNight) Color.rgb(239, 249, 247) else Color.rgb(30, 57, 59)
    private val muted: Int get() = if (winterActive) Color.rgb(168, 205, 217)
        else if (isNight) Color.rgb(165, 189, 191) else Color.rgb(104, 129, 130)
    private val accent: Int get() = if (winterActive) Color.rgb(54, 213, 218)
        else if (isNight) Color.rgb(87, 169, 167) else Color.rgb(35, 131, 131)
    private val surface: Int get() = if (winterActive) Color.rgb(14, 56, 72)
        else if (isNight) Color.rgb(32, 54, 60) else Color.WHITE
    private val backdrop: Int get() = if (winterActive) Color.rgb(4, 33, 48)
        else if (isNight) Color.rgb(17, 33, 40) else Color.rgb(237, 248, 246)
    private val softAccent: Int get() = if (winterActive) Color.rgb(19, 79, 95)
        else if (isNight) Color.rgb(38, 77, 78) else Color.rgb(223, 244, 239)

    private val balanceHubTypeface: Typeface by lazy {
        Typeface.createFromAsset(assets, "fonts/montserrat_bold.ttf")
    }

    private fun screenGradient(): android.graphics.drawable.Drawable =
        winterDrawable(GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (winterActive) {
                if (isNight) intArrayOf(Color.rgb(5, 38, 53), Color.rgb(3, 26, 39))
                else intArrayOf(Color.rgb(12, 65, 82), Color.rgb(6, 43, 59))
            } else if (isNight) intArrayOf(Color.rgb(19, 42, 49), Color.rgb(17, 31, 38))
            else intArrayOf(Color.rgb(238, 250, 247), Color.rgb(245, 249, 249))
        ), WinterSurface.SCREEN)

    private fun cardGradient(): android.graphics.drawable.Drawable = winterDrawable(
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (winterActive) {
                if (isNight) intArrayOf(Color.rgb(10, 61, 78), Color.rgb(7, 35, 51))
                else intArrayOf(Color.rgb(24, 81, 101), Color.rgb(12, 56, 77))
            } else if (isNight) intArrayOf(Color.rgb(37, 63, 67), Color.rgb(31, 48, 57))
            else intArrayOf(Color.WHITE, Color.rgb(235, 247, 245))
        ).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), if (winterActive) Color.rgb(126, 212, 232)
                else if (isNight) Color.rgb(66, 102, 104)
                else Color.rgb(205, 229, 225))
        }, WinterSurface.CARD)

    private fun filledButtonGradient(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        if (isNight) intArrayOf(Color.rgb(47, 131, 136), Color.rgb(40, 158, 142))
        else intArrayOf(Color.rgb(48, 145, 150), Color.rgb(36, 143, 125))
    ).apply { cornerRadius = dp(14).toFloat() }

    private fun tabBackground(selected: Boolean): GradientDrawable =
        if (selected) filledButtonGradient() else GradientDrawable().apply {
            setColor(if (isNight) Color.rgb(23, 41, 48) else Color.rgb(245, 252, 250))
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1), if (isNight) Color.rgb(112, 151, 151)
                else Color.rgb(143, 181, 181))
        }

    private fun rounded(color: Int, radius: Int = 18): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun textLine(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    /** Shared visual style for all mobile providers and supported banks. */
    private fun balanceAmountPill(label: String, knownBalance: Boolean = true): TextView =
        textLine(label, if (knownBalance) BalanceAmountStyle.FONT_SIZE_SP else 15f,
            if (winterActive) ink else Color.rgb(23, 55, 33), true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(BalanceAmountStyle.HORIZONTAL_PADDING_DP),
                dp(BalanceAmountStyle.VERTICAL_PADDING_DP),
                dp(BalanceAmountStyle.HORIZONTAL_PADDING_DP),
                dp(BalanceAmountStyle.VERTICAL_PADDING_DP))
            background = if (winterActive) null else rounded(
                if (isNight) Color.rgb(191, 239, 104)
                else Color.rgb(216, 241, 166), BalanceAmountStyle.CORNER_RADIUS_DP)
        }

    /** Keep full-size balances intact; stack the pill when horizontal space is short. */
    private fun balancePillNeedsOwnLine(pill: TextView, reservedDp: Int): Boolean =
        BalanceAmountStyle.needsOwnLine(
            resources.configuration.screenWidthDp, resources.displayMetrics.density,
            pill.paint.measureText(pill.text.toString()), reservedDp)

    private fun verticalGap(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = cardGradient()
        elevation = dp(2).toFloat()
    }

    /** Reusable themed toggle chip for monthly payment days and event weekdays. */
    private fun recurringChoiceChip(
        label: String, description: String, initiallySelected: Boolean,
        onChanged: (Boolean) -> Unit
    ): TextView = textLine(label, 14f, ink, true).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        isClickable = true
        isFocusable = true
        minHeight = dp(42)

        fun updateAppearance(active: Boolean) {
            isSelected = active
            contentDescription = "$description, " + if (active) "выбрано" else "не выбрано"
            setTextColor(if (active) Color.WHITE else ink)
            background = if (active) filledButtonGradient() else
                GradientDrawable().apply {
                    setColor(if (isNight) Color.rgb(34, 66, 69)
                        else Color.rgb(242, 250, 248))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (isNight) Color.rgb(79, 156, 155)
                        else Color.rgb(116, 180, 171))
                }
            elevation = if (active) dp(2).toFloat() else 0f
        }

        updateAppearance(initiallySelected)
        setOnClickListener {
            val next = !isSelected
            updateAppearance(next)
            onChanged(next)
        }
    }


    /**
     * LinearLayout weights are measured against each dialog row's actual width.
     * GridLayout's undefined specs with width=0 were overflowing in ScrollView
     * on some OEM displays and large font scales.
     */
    private fun recurringChoiceRows(
        choices: List<Pair<String, Int>>, columns: Int,
        descriptionPrefix: String, selected: Set<Int>,
        onChanged: (Int, Boolean) -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        require(columns > 0)
        orientation = LinearLayout.VERTICAL
        for (chunk in choices.chunked(columns)) {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for ((label, value) in chunk) {
                val chip = recurringChoiceChip(
                    label, "$descriptionPrefix $label", value in selected
                ) { enabled -> onChanged(value, enabled) }
                row.addView(chip, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
            repeat(columns - chunk.size) {
                row.addView(View(this@MainActivity),
                    LinearLayout.LayoutParams(0, dp(50), 1f))
            }
            addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun secondaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(ink)
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            if (isNight) intArrayOf(Color.rgb(49, 84, 85), Color.rgb(40, 73, 74))
            else intArrayOf(Color.rgb(229, 247, 242), Color.rgb(214, 240, 234))
        ).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), accent)
        }
        setOnClickListener { action() }
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = filledButtonGradient()
        elevation = dp(2).toFloat()
        setOnClickListener { action() }
    }


    private fun themedDialogBuilder(): AlertDialog.Builder =
        AlertDialog.Builder(this, if (winterActive) {
            if (isNight) R.style.BalanceHubDialogWinterDark
            else R.style.BalanceHubDialogWinterLight
        } else if (isNight) R.style.BalanceHubDialogDark
        else R.style.BalanceHubDialogLight)

    private fun dialogGradient(): android.graphics.drawable.Drawable = winterDrawable(
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (winterActive) {
                if (isNight) intArrayOf(Color.rgb(8, 54, 72), Color.rgb(5, 29, 46))
                else intArrayOf(Color.rgb(24, 85, 106), Color.rgb(10, 51, 72))
            } else if (isNight) intArrayOf(Color.rgb(36, 66, 70), Color.rgb(26, 46, 54))
            else intArrayOf(Color.WHITE, Color.rgb(233, 247, 243))
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), if (winterActive) Color.rgb(135, 218, 240)
                else if (isNight) Color.rgb(72, 132, 127)
                else Color.rgb(172, 217, 208))
        }, WinterSurface.DIALOG)

    private fun showThemedChoices(
        title: String, labels: List<String>, selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, labels
        ) {
            override fun getView(position: Int, convertView: View?,
                                 parent: android.view.ViewGroup): View {
                val active = position == selectedIndex
                return TextView(this@MainActivity).apply {
                    text = (if (active) "✓    " else "      ") + labels[position]
                    textSize = 16f
                    setTextColor(if (active) ink else ink)
                    if (active) setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(15), dp(16), dp(15))
                    if (active) {
                        background = GradientDrawable().apply {
                            setColor(softAccent)
                            cornerRadius = dp(12).toFloat()
                            setStroke(dp(1), accent)
                        }
                    } else background = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                    }
                }
            }
        }
        val dialog = themedDialogBuilder().setTitle(title)
            .setAdapter(adapter) { _, index -> onSelected(index) }
            .setNegativeButton("Отмена", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(dialogGradient())
        }
        dialog.show()
    }

    private fun swipeToTab(nextTab: Int) {
        // Gestures and button clicks use one transition path.
        selectTab(nextTab)
    }

    private fun showDashboard() {
        renderedWinter = winterActive
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = screenGradient()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), headerTopPadding, dp(20), dp(23))
            background = BalanceHubHeaderDrawable(isNight, winterActive)
            if (winterActive) {
                // A foreground layer is drawn after the header children.
                // It remains visible above the gradient without moving the title.
                foreground = WinterReferenceHeaderDrawable(
                    Color.TRANSPARENT.toDrawable(),
                    resources.displayMetrics.density
                )
            }
        }
        header.addView(textLine("BalanceHub", 21f, Color.WHITE, true).apply {
            typeface = balanceHubTypeface
            letterSpacing = -0.015f
            // Shift the wordmark down without resizing the gradient header.
            translationY = dp(8).toFloat()
        })
        screen.addView(header)

        // Horizontal gestures switch tabs while normal vertical scrolling
        // and child taps retain their original behavior.
        val scroll = object : android.widget.ScrollView(this) {
            private var downX = 0f
            private var downY = 0f
            private var downTime = 0L
            private var multiTouch = false

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        downTime = event.eventTime
                        multiTouch = false
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
                    MotionEvent.ACTION_UP -> {
                        val editingField = contentContainer.findFocus() is EditText
                        if (!editingField && !multiTouch) {
                            val direction = SwipeGesture.direction(
                                event.x - downX, event.y - downY,
                                event.eventTime - downTime, resources.displayMetrics.density
                            )
                            val destination = currentTab + direction
                            if (direction != 0 && destination in 0..4) {
                                // Cancel the child's gesture, not its click, once a real
                                // horizontal swipe has been confirmed on release.
                                val cancellation = MotionEvent.obtain(event)
                                cancellation.action = MotionEvent.ACTION_CANCEL
                                super.dispatchTouchEvent(cancellation)
                                cancellation.recycle()
                                post { swipeToTab(destination) }
                                return true
                            }
                        }
                    }
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(contentContainer)
        dashboardScroll = scroll
        // Insets, header size and device font scale all change the real viewport.
        // Use its measured height instead of guessing from screenHeightDp.
        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val height = ((bottom - top) / resources.displayMetrics.density).toInt()
            val previous = measuredDashboardViewportDp
            if (height > 0 && kotlin.math.abs(previous - height) >= 2) {
                measuredDashboardViewportDp = height
                if (currentTab == 0) {
                    scroll.post {
                        if (currentTab == 0 && measuredDashboardViewportDp == height &&
                            !isFinishing) renderTab()
                    }
                }
            }
        }
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Variant 3: genuinely floating pill, 90–92% of screen width.
        // Keep at least 48dp hit targets; allow extra width on small phones.
        val navSidePadding = when {
            resources.configuration.screenWidthDp < 340 -> dp(8)
            resources.configuration.screenWidthDp < 370 -> dp(14)
            else -> dp(19)
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // The real safe-area padding is supplied after receiving root insets.
            setPadding(navSidePadding, dp(8), navSidePadding,
                dp(BottomNavigationSafeArea.GAP_DP))
            background = null
        }
        val navigation = BalanceBottomNavigation(this, isNight, currentTab) {
            selectTab(it)
        }
        bottomNavigation = navigation
        tabViews = (0 until NavigationMotionPolicy.TAB_COUNT).mapNotNull {
            navigation.section(it)
        }
        tabs.addView(navigation, LinearLayout.LayoutParams(-1, dp(60)))
        // In edge-to-edge mode navigationBars() reports the navigation
        // boundary for both three-button and transparent gesture navigation.
        // systemGestures() also includes a larger touch-recognition region and
        // must NOT raise the visual pill above the actual navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(screen) { _, insets ->
            val bottom = BottomNavigationSafeArea.bottomPaddingPx(
                navigationBar = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()).bottom,
                gapPx = dp(BottomNavigationSafeArea.GAP_DP)
            )
            if (tabs.paddingBottom != bottom) {
                tabs.setPadding(navSidePadding, dp(8), navSidePadding, bottom)
            }
            insets
        }
        screen.addView(tabs)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = backdrop
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !isNight
        }
        setContentView(screen)
        // Re-query the actual navigation mode after attachment (also updated
        // automatically for later inset changes and window resizing).
        ViewCompat.requestApplyInsets(screen)
        renderTab()
    }


    // One transition for taps, swipes, card shortcuts and notification deep links.
    // Keep the persistent navigation view; only its indicator and content animate.
    private fun selectTab(index: Int) {
        if (!NavigationMotionPolicy.isChange(currentTab, index)) return
        val direction = NavigationMotionPolicy.direction(currentTab, index)
        // Cancel from the current frame before applying the new state. Rapid
        // repeated selections cannot leave the content translated or translucent.
        contentContainer.animate().cancel()
        contentContainer.alpha = 1f
        contentContainer.translationX = 0f
        if (tutorialStep == 0 && index == 1) {
            tutorialStep = 1
        }
        currentTab = index
        // Settings is index 4, not index 3 (Events).
        if (index != NavigationMotionPolicy.SETTINGS_INDEX) {
            settingsStack.clear()
            settingsScrollPositions.clear()
            closeEngineeringMenu()
            mobileSettingsOpen = false
            bankSettingsOpen = false
        }
        if (index != 1) operatorDetailsOpen = false
        if (index != 2) {
            archiveOpen = false
            expandedPaymentId = null
        }
        if (index != 3) expandedEventId = null
        bottomNavigation?.select(index, animate = true)
        renderTab()
        if (::dashboardScroll.isInitialized) dashboardScroll.scrollTo(0, 0)
        if (NavigationMotionPolicy.canAnimate(this)) {
            contentContainer.translationX = dp(10).toFloat() * direction
            contentContainer.alpha = 0.9f
            contentContainer.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(NavigationMotionPolicy.CONTENT_DURATION_MS)
                .setInterpolator(android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f))
                .start()
        }
    }

    private fun renderTab() {
        // The bottom navigation persists across tabs; restore its original
        // drawable and elevation before rendering the next tutorial step.
        clearTutorialHighlights()
        contentContainer.removeAllViews()
        // All top-level headings share the same vertical position.
        // Reuse the responsive top inset already approved for "Главная";
        // do not shift the home title or change the wave/header itself.
        val topPadding = dashboardSizing().topPaddingDp
        contentContainer.setPadding(dp(18), dp(topPadding), dp(18),
            dp(if (currentTab == 0) 12 else 24))
        if (tutorialStep >= 0) {
            contentContainer.addView(tutorialCoach())
            contentContainer.addView(verticalGap(14))
        }
        when (currentTab) {
            0 -> renderHome()
            1 -> renderBalances()
            2 -> if (archiveOpen) renderArchiveScreen() else renderPaymentScreen()
            3 -> if (eventArchiveExpanded) renderEventArchiveScreen() else renderEvents()
            else -> renderSettings()
        }
        if (tutorialStep == 0 && tabViews.size > 1) {
            highlightTutorialTarget(tabViews[1])
        }
    }

    /** Undo all tutorial-only decorations, including those on persistent views. */
    private fun clearTutorialHighlights() {
        for ((view, original) in tutorialHighlightedViews) {
            view.background = original.first
            view.elevation = original.second
        }
        tutorialHighlightedViews.clear()
    }

    /** In-context coach: it never covers the actual buttons the user must tap. */
    private fun highlightTutorialTarget(target: View) {
        if (!tutorialHighlightedViews.containsKey(target)) {
            tutorialHighlightedViews[target] = target.background to target.elevation
        }
        val gold = Color.rgb(235, 200, 94)
        target.background = GradientDrawable().apply {
            setColor(if (isNight) Color.rgb(44, 75, 67) else Color.rgb(220, 238, 225))
            cornerRadius = dp(13).toFloat()
            setStroke(dp(2), gold)
        }
        target.elevation = dp(9).toFloat()
    }

    private fun completeTutorial() {
        clearTutorialHighlights()
        preferences.edit().putBoolean("onboarding_completed", true).apply()
        tutorialStep = -1
        renderTab()
        window.decorView.post {
            showWhatsNewIfNeeded()
            if (!welcomeShowing) maybeOfferAppLock()
        }
    }

    private fun restartTutorial() {
        clearTutorialHighlights()
        tutorialStep = 0
        operatorDetailsOpen = false
        mobileSettingsOpen = false
        if (currentTab == 0) renderTab() else selectTab(0)
    }

    private fun backTutorial() {
        clearTutorialHighlights()
        when (tutorialStep) {
            1 -> {
                tutorialStep = 0
                operatorDetailsOpen = false
                if (currentTab == 0) renderTab() else selectTab(0)
            }
            2 -> {
                tutorialStep = 1
                operatorDetailsOpen = false
                if (currentTab == 1) renderTab() else selectTab(1)
            }
            3 -> {
                tutorialStep = 2
                operatorDetailsOpen = true
                mobileSettingsOpen = false
                if (currentTab == 1) renderTab() else selectTab(1)
            }
            else -> {
                tutorialStep = (tutorialStep - 1).coerceAtLeast(0)
                renderTab()
            }
        }
    }

    private fun tutorialCoach(): LinearLayout {
        val gold = Color.rgb(235, 200, 94)
        val data = listOf(
            Triple("Нажмите «Балансы»",
                "Откройте вкладку с мобильными операторами в нижней панели.", "Балансы"),
            Triple("Выберите оператора",
                "Нажмите на подсвеченную карточку нужной SIM-карты.", "Карточка оператора"),
            Triple("Откройте настройки тарифа",
                "В подробной информации нажмите «Настроить абонентскую плату и дату».",
                "Настроить тариф"),
            Triple("Введите абонентскую плату",
                "Укажите сумму списания в рублях и нажмите «Сохранить сумму».",
                "Сохранить сумму"),
            Triple("Укажите дату списания",
                "Выберите дату следующего платежа и подтвердите её в календаре.",
                "Указать дату"),
            Triple("Включите напоминание",
                "Проверьте ежемесячный платёж и напоминание за один день. " +
                    "После этого завершите обучение.", "Предстоящие платежи")
        )
        val position = tutorialStep.coerceIn(0, data.lastIndex)
        val needsSimPermission = position == 1 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        val (headline, advice, target) = if (needsSimPermission) Triple(
            "Разрешите доступ к SIM-картам",
            "Это нужно, чтобы показать все SIM и определить оператора каждой из них.",
            "Разрешить доступ к SIM"
        ) else data[position]
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(14))
            elevation = dp(11).toFloat()
            background = GradientDrawable().apply {
                setColor(if (isNight) Color.rgb(31, 57, 57)
                    else Color.rgb(232, 245, 239))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), gold)
            }
            val heading = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(textLine("✦", 24f, gold, true),
                LinearLayout.LayoutParams(dp(33), -2))
            val headingText = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(textLine("Помощник BalanceHub", 16f,
                    if (isNight) gold else Color.rgb(99, 80, 11), true))
                addView(textLine("Подскажу, что сделать дальше", 12f, muted))
            }
            heading.addView(headingText, LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(textLine("Шаг " + (position + 1) + "/6", 12f, muted))
            addView(heading)
            addView(verticalGap(10))
            addView(View(this@MainActivity).apply {
                background = rounded(gold, 2)
            }, LinearLayout.LayoutParams(-1, dp(1)))
            addView(verticalGap(10))
            addView(textLine(headline, 19f, ink, true))
            addView(verticalGap(5))
            addView(textLine(advice, 14f, ink))
            addView(verticalGap(9))
            addView(textLine("✦ Нужный элемент выделен золотистой рамкой: " +
                target, 12f, if (isNight) gold else accent, true))
            addView(verticalGap(10))
            val buttons = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            buttons.addView(secondaryButton("Пропустить") {
                completeTutorial()
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            if (position > 0) {
                buttons.addView(View(this@MainActivity),
                    LinearLayout.LayoutParams(dp(8), 1))
                buttons.addView(secondaryButton("‹ Назад") {
                    backTutorial()
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            if (position == 5) {
                buttons.addView(View(this@MainActivity),
                    LinearLayout.LayoutParams(dp(8), 1))
                val sim = currentSim()
                val canFinish = sim != null && payments.any {
                    it.id == paymentIdFor(sim)
                }
                buttons.addView(primaryButton("Готово") {
                    if (canFinish) completeTutorial()
                    else Toast.makeText(this@MainActivity,
                        "Сначала сохраните сумму, дату и включите платёж",
                        Toast.LENGTH_LONG).show()
                }.apply { isEnabled = canFinish },
                    LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            addView(buttons)
        }
    }

    private fun renderHome() {
        contentContainer.addView(textLine("Главная", 23f, ink, true))
        contentContainer.addView(verticalGap(8))
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val upcomingPayments = payments.filter { it.dueAt >= startOfToday }
        val upcomingEvents = EventStore.active(this).filter {
            it.dueAt >= startOfToday
        }
        val summary = card().apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        summary.addView(textLine("Предстоящие", 14f, muted))
        summary.addView(verticalGap(9))
        val totals = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val paymentsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(6), dp(4))
            isClickable = true
            isFocusable = true
            contentDescription = "Открыть предстоящие платежи"
            addView(textLine("▣  Платежи", 13f, accent, true))
            addView(verticalGap(5))
            addView(textLine(upcomingPayments.size.toString(), 27f, ink, true))
            addView(verticalGap(3))
            val monthNow = Calendar.getInstance()
            val thisMonthCharges = payments.flatMap { payment ->
                PaymentOccurrenceEngine.month(payment,
                    monthNow.get(Calendar.YEAR), monthNow.get(Calendar.MONTH))
            }.filterNot { it.skipped }
            val amount = PaymentOverview.totalLabel(
                thisMonthCharges.map { it.amount to it.currency })
            addView(textLine(amount, if (amount.length > 17) 12f else 15f,
                ink, true).apply {
                maxLines = 6
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(verticalGap(2))
            addView(textLine("За текущий месяц", 11f, muted))
            setOnClickListener { selectTab(2) }
        }
        totals.addView(paymentsColumn, LinearLayout.LayoutParams(0, -2, 1f))
        totals.addView(View(this).apply {
            setBackgroundColor(if (isNight) Color.rgb(83, 121, 123)
                else Color.rgb(189, 220, 217))
        }, LinearLayout.LayoutParams(dp(1), dp(104)).apply {
            marginStart = dp(8)
            marginEnd = dp(13)
        })
        val eventColor = if (isNight) Color.rgb(159, 198, 221)
            else Color.rgb(67, 110, 144)
        val eventsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
            isClickable = true
            isFocusable = true
            contentDescription = "Открыть предстоящие события"
            addView(textLine("▦  События", 13f, eventColor, true))
            addView(verticalGap(5))
            addView(textLine(upcomingEvents.size.toString(), 27f, ink, true))
            addView(verticalGap(3))
            val nearest = upcomingEvents.minByOrNull { it.dueAt }
            addView(textLine(nearest?.let {
                SimpleDateFormat("d MMMM", Locale("ru")).format(Date(it.dueAt))
            } ?: "Нет событий", 14f, ink, true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(verticalGap(2))
            addView(textLine("Ближайшее событие", 11f, muted))
            setOnClickListener { selectTab(3) }
        }
        totals.addView(eventsColumn, LinearLayout.LayoutParams(0, -2, 1f))
        summary.addView(totals)
        contentContainer.addView(summary)
        contentContainer.addView(verticalGap(10))
        renderHomeCalendar(upcomingPayments, upcomingEvents)
    }

    private fun renderHomeCalendar(upcoming: List<PaymentReminder>,
        events: List<PlannedEvent>) {
        val year = homeCalendarMonth.get(Calendar.YEAR)
        val month = homeCalendarMonth.get(Calendar.MONTH)
        val offset = PaymentCalendarMath.mondayOffset(year, month)
        val monthDays = PaymentCalendarMath.daysInMonth(year, month)
        val rows = (offset + monthDays + 6) / 7
        val sizing = dashboardSizing(rows)
        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == year &&
            today.get(Calendar.MONTH) == month
        val byDay = linkedMapOf<Int, MutableList<PaymentReminder>>()
        for (payment in payments) {
            for (charge in PaymentOccurrenceEngine.month(payment, year, month)) {
                if (charge.skipped) continue
                val day = Calendar.getInstance().apply {
                    timeInMillis = charge.dueAt
                }.get(Calendar.DAY_OF_MONTH)
                byDay.getOrPut(day) { mutableListOf() }.add(payment)
            }
        }
        // Event occurrences follow the same calendar month as payment occurrences.
        val eventByDay = linkedMapOf<Int, MutableList<PlannedEvent>>()
        val monthStart = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monthEnd = Calendar.getInstance().apply {
            timeInMillis = monthStart
            add(Calendar.MONTH, 1)
        }.timeInMillis
        for (event in events) {
            if (event.recurrence == "custom") {
                for (visit in EventOccurrenceEngine.between(event, monthStart, monthEnd)) {
                    if (visit.skipped) continue
                    val day = Calendar.getInstance().apply {
                        timeInMillis = visit.dueAt
                    }.get(Calendar.DAY_OF_MONTH)
                    eventByDay.getOrPut(day) { mutableListOf() }.add(event)
                }
                continue
            }
            var occurrence = if (event.dueAt < monthStart)
                EventDateRules.nextOccurrence(event.dueAt, event.recurrence,
                    monthStart - 1L) ?: continue
            else event.dueAt
            var steps = 0
            while (occurrence < monthEnd && steps++ < 40) {
                if (occurrence >= monthStart) {
                    val day = Calendar.getInstance().apply {
                        timeInMillis = occurrence
                    }.get(Calendar.DAY_OF_MONTH)
                    eventByDay.getOrPut(day) { mutableListOf() }.add(event)
                }
                if (event.recurrence == "once") break
                occurrence = EventDateRules.nextOccurrence(
                    occurrence, event.recurrence, occurrence) ?: break
            }
        }
        val calendar = card().apply { setPadding(dp(11), dp(9), dp(11), dp(10)) }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(secondaryButton("‹") {
            homeCalendarMonth.add(Calendar.MONTH, -1)
            homeSelectedDay = null
            renderTab()
        }.apply { contentDescription = "Предыдущий месяц" },
            LinearLayout.LayoutParams(dp(40), dp(sizing.monthControlHeightDp)))
        val title = SimpleDateFormat("LLLL yyyy", Locale("ru"))
            .format(homeCalendarMonth.time).replaceFirstChar { it.uppercase() }
        controls.addView(textLine(title, 16f, ink, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(sizing.monthControlHeightDp), 1f))
        controls.addView(secondaryButton("›") {
            homeCalendarMonth.add(Calendar.MONTH, 1)
            homeSelectedDay = null
            renderTab()
        }.apply { contentDescription = "Следующий месяц" },
            LinearLayout.LayoutParams(dp(40), dp(sizing.monthControlHeightDp)))
        calendar.addView(controls)
        calendar.addView(verticalGap(4))

        val weekdays = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (name in listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")) {
            weekdays.addView(textLine(name, 11f, muted, true).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, dp(20), 1f))
        }
        calendar.addView(weekdays)
        for (rowIndex in 0 until rows) {
            val daysRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (column in 0..6) {
                val day = rowIndex * 7 + column - offset + 1
                if (day !in 1..monthDays) {
                    daysRow.addView(View(this),
                        LinearLayout.LayoutParams(0, dp(sizing.dayCellHeightDp), 1f))
                    continue
                }
                val paymentCount = byDay[day]?.size ?: 0
                val eventCount = eventByDay[day]?.size ?: 0
                val marked = paymentCount + eventCount > 0
                val isToday = isCurrentMonth &&
                    today.get(Calendar.DAY_OF_MONTH) == day
                val chosen = homeSelectedDay == day
                val cell = android.widget.FrameLayout(this).apply {
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                    isClickable = true
                    isFocusable = true
                    contentDescription = day.toString() + " " + title +
                        ", платежей: " + paymentCount +
                        ", событий: " + eventCount +
                        (if (isToday) ", сегодня" else "")
                }
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = BalanceCalendarDayDrawable(this@MainActivity,
                        isNight || winterActive, paymentCount > 0, eventCount > 0,
                        chosen, isToday)
                    addView(textLine(day.toString(), 13f,
                        if ((isNight || winterActive) && (marked || chosen)) Color.WHITE else ink,
                        chosen || marked || isToday).apply {
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    })
                    addView(textLine(
                        if (paymentCount + eventCount > 1)
                            (paymentCount + eventCount).toString()
                        else if (marked) "•" else " ",
                        9f, if ((isNight || winterActive) && (marked || chosen)) Color.WHITE else accent,
                        marked
                    ).apply {
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    })
                }
                cell.addView(tile, android.widget.FrameLayout.LayoutParams(
                    -1, -1))
                cell.setOnClickListener {
                    homeSelectedDay = if (chosen) null else day
                    renderTab()
                }
                daysRow.addView(cell,
                    LinearLayout.LayoutParams(0, dp(sizing.dayCellHeightDp), 1f)
                        .apply { marginStart = dp(1); marginEnd = dp(1) })
            }
            calendar.addView(daysRow)
        }
        calendar.addView(verticalGap(6))
        calendar.addView(View(this).apply {
            setBackgroundColor(if (isNight) Color.rgb(68, 104, 104)
                else Color.rgb(196, 222, 218))
        }, LinearLayout.LayoutParams(-1, dp(1)))
        calendar.addView(verticalGap(6))

        // Always show the nearest current payment, not the nearest in the
        // month currently browsed in the calendar.
        val nearest = upcoming.minByOrNull { it.dueAt }
        val nearestRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        nearestRow.addView(textLine("▣", 22f, accent, true).apply {
            gravity = Gravity.CENTER
            background = rounded(softAccent, 12)
        }, LinearLayout.LayoutParams(dp(40), dp(42)))
        nearestRow.addView(View(this), LinearLayout.LayoutParams(dp(9), 1))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine("Ближайший платёж", 12f, muted))
            addView(verticalGap(3))
            addView(textLine(nearest?.title ?: "Платежей пока нет",
                14f, ink, true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (nearest != null) {
                addView(verticalGap(3))
                addView(textLine(DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(nearest.dueAt)) + " · " +
                    PaymentCurrencyFormatter.amountForList(nearest.amount), 11f, muted))
            }
        }
        nearestRow.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        if (nearest != null) {
            val remaining = PaymentCalendarMath.daysUntil(
                System.currentTimeMillis(), nearest.dueAt).coerceAtLeast(0)
            val word = when {
                remaining % 100 in 11..14 -> "дней"
                remaining % 10 == 1 -> "день"
                remaining % 10 in 2..4 -> "дня"
                else -> "дней"
            }
            nearestRow.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
            nearestRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                background = rounded(softAccent, 12)
                addView(textLine(remaining.toString(), 22f, accent, true).apply {
                    gravity = Gravity.CENTER
                })
                addView(textLine(word, 11f, muted, true).apply {
                    gravity = Gravity.CENTER
                })
            }, LinearLayout.LayoutParams(dp(58), -2))
            nearestRow.isClickable = true
            nearestRow.isFocusable = true
            nearestRow.contentDescription = "Открыть ближайший платёж " + nearest.title
            nearestRow.setOnClickListener {
                upcomingListExpanded = true
                expandedPaymentId = nearest.id
                preferences.edit().putBoolean("payments_list_expanded", true).apply()
                selectTab(2)
            }
        }
        calendar.addView(nearestRow)

        val chosenDay = homeSelectedDay
        if (chosenDay != null) {
            calendar.addView(verticalGap(10))
            val selectedDate = (homeCalendarMonth.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, chosenDay)
            }
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(softAccent, 12)
                setPadding(dp(11), dp(10), dp(11), dp(10))
                addView(textLine("Платежи и события на " +
                    SimpleDateFormat("d MMMM", Locale("ru"))
                        .format(selectedDate.time), 13f, ink, true))
            }
            val selectedPayments = byDay[chosenDay].orEmpty()
            val selectedEvents = eventByDay[chosenDay].orEmpty()
            if (selectedPayments.isEmpty() && selectedEvents.isEmpty()) {
                details.addView(verticalGap(4))
                details.addView(textLine("На эту дату записей нет", 12f, muted))
            }
            if (selectedPayments.isNotEmpty()) {
                details.addView(verticalGap(6))
                details.addView(textLine("Платежи", 12f, accent, true))
            }
            for (payment in selectedPayments) {
                details.addView(verticalGap(4))
                details.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(iconTile(payment.iconId ?: IconRegistry.select(
                        payment.title, "credit-card"), 26),
                        LinearLayout.LayoutParams(dp(26), dp(26)))
                    addView(View(this@MainActivity),
                        LinearLayout.LayoutParams(dp(7), 1))
                    addView(textLine(payment.title + " · " +
                        PaymentCurrencyFormatter.amountForList(payment.amount), 12f, ink))
                })
            }
            if (selectedEvents.isNotEmpty()) {
                details.addView(verticalGap(7))
                details.addView(textLine("События", 12f,
                    if (isNight) Color.rgb(159, 198, 221)
                    else Color.rgb(67, 110, 144), true))
            }
            for (event in selectedEvents) {
                details.addView(verticalGap(4))
                details.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(iconTile(event.iconId ?: IconRegistry.select(
                        event.title, "calendar-days"), 26),
                        LinearLayout.LayoutParams(dp(26), dp(26)))
                    addView(View(this@MainActivity),
                        LinearLayout.LayoutParams(dp(7), 1))
                    addView(textLine(event.title + " · " +
                        SimpleDateFormat("HH:mm", Locale("ru"))
                            .format(Date(event.dueAt)), 12f, ink))
                })
            }
            calendar.addView(details)
        }
        contentContainer.addView(calendar)
    }

    private fun connectedSims(): List<MobileSim> = T2Balance.sims(this)

    private fun currentSim(): MobileSim? {
        val sims = connectedSims()
        return sims.firstOrNull { it.subscriptionId == selectedOperatorId }
            ?: sims.firstOrNull { it.isT2 } ?: sims.firstOrNull()
    }

    private fun renderBalances() {
        if (operatorDetailsOpen) { renderOperatorDetails(); return }
        contentContainer.addView(textLine("Балансы", 23f, ink, true))
        contentContainer.addView(verticalGap(if (winterActive) 12 else 8))
        if (!winterActive) {
            contentContainer.addView(textLine("Мобильная связь", 14f, muted))
            contentContainer.addView(verticalGap(14))
        }

        val sims = connectedSims()
        val orderedSims = BalanceCardOrder.withBalanceFirst(sims) {
            MobileBalanceService.cached(this, it) != null
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = card()
            permissions.addView(textLine("Доступ к SIM-картам", 17f, ink, true))
            permissions.addView(verticalGap(6))
            permissions.addView(textLine(
                "Разрешите определение SIM, чтобы видеть каждого оператора отдельно. " +
                    "Без разрешения доступен ручной ввод.",
                13f, muted
            ))
            permissions.addView(verticalGap(9))
            val request = primaryButton("Разрешить доступ к SIM") {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 511
                )
            }
            if (tutorialStep == 1) highlightTutorialTarget(request)
            permissions.addView(request)
            contentContainer.addView(permissions)
            contentContainer.addView(verticalGap(12))
        }
        if (sims.isEmpty()) {
            contentContainer.addView(card().apply {
                addView(textLine("Активные SIM-карты не обнаружены", 15f, ink))
            })
            contentContainer.addView(verticalGap(12))
        }
        for (sim in orderedSims) {
            val op = MobileBalanceService.operator(this, sim)
            val name = MobileBalanceService.displayName(this, sim)
            val cached = MobileBalanceService.cached(this, sim)
            syncMobilePayment(sim)
            val mobile = card()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            // Use the bundled graphic for every provider; never a text tile.
            val emblem = android.widget.ImageView(this).apply {
                setImageDrawable(OperatorBrandLogoDrawable(op))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = "Логотип оператора " + name
            }
            row.addView(emblem, LinearLayout.LayoutParams(dp(54), dp(54)))
            row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(textLine(name, 16f, ink, true))
                addView(verticalGap(3))
                addView(textLine(sim.label + " · мобильный баланс", 12f, muted))
            }
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val amount = balanceAmountPill(
                cached?.first?.let { it + " ₽" } ?: "Нет данных",
                knownBalance = cached != null)
            val amountBelow = balancePillNeedsOwnLine(amount,
                BalanceAmountStyle.MOBILE_RESERVED_DP)
            if (!amountBelow) {
                row.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
                row.addView(amount)
            }
            mobile.addView(row)
            if (amountBelow) {
                mobile.addView(verticalGap(7))
                mobile.addView(LinearLayout(this).apply {
                    gravity = Gravity.END
                    addView(amount)
                })
            }
            mobile.setOnClickListener {
                selectedOperatorId = sim.subscriptionId
                if (tutorialStep == 1) tutorialStep = 2
                operatorDetailsOpen = true
                renderTab()
            }
            if (tutorialStep == 1 && sim == orderedSims.first() &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) highlightTutorialTarget(mobile)
            contentContainer.addView(mobile)
            contentContainer.addView(verticalGap(12))
        }
        renderSberBalances()
        if (!sessionAutoRefreshStarted &&
            preferences.getBoolean("mobile_auto_refresh_on_open", true) &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            sessionAutoRefreshStarted = true
            refreshSimsInSequence(sims, 0)
        }
    }

    private fun sberLogo(size: Int = 45): android.widget.ImageView =
        android.widget.ImageView(this).apply {
            setImageDrawable(SberBrandLogoDrawable())
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = "Логотип Сбербанка"
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }

    private fun sberHeading(text: String, size: Float = 18f): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sberLogo())
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(12), 1))
            addView(textLine(text, size, ink, true),
                LinearLayout.LayoutParams(0, -2, 1f))
        }

    private fun rubles(kopecks: Long): String =
        java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU"))
            .format(java.math.BigDecimal.valueOf(kopecks, 2))

    private fun renderSberBalances() {
        contentContainer.addView(verticalGap(6))
        contentContainer.addView(textLine("Банковские счета", 14f, muted))
        contentContainer.addView(verticalGap(14))

        val visibleBanks = RussianBankCatalog.available(this).filter {
            BankCardPreferences.visible(this, it.id)
        }
        val orderedBanks = BalanceCardOrder.withBalanceFirst(visibleBanks) { bank ->
            visibleBankBalances(bank).isNotEmpty()
        }
        for (bank in orderedBanks) renderBankBalance(bank)
        if (visibleBanks.isEmpty()) {
            contentContainer.addView(textLine(
                "Все банковские карточки скрыты. Верните их через Настройки → Банки.",
                13f, muted))
            contentContainer.addView(verticalGap(12))
        }
    }

    /** Existing account stores are preserved; only presentation is grouped. */
    private fun bankBalances(bank: RussianBank): List<BankAccountBalance> {
        val stored = if (bank.id == "sber") {
            SberBalanceStore.all(this).map {
                BankAccountBalance(it.kopecks, it.lastFour, it.observedAt)
            }
        } else {
            RussianBankBalanceStore.all(this, bank.id).map {
                BankAccountBalance(it.kopecks, it.lastFour, it.observedAt)
            }
        }
        return BankAccountRows.newestPerAccount(stored)
    }

    private fun visibleBankBalances(bank: RussianBank): List<BankAccountBalance> =
        if (!BankCardPreferences.enabled(this, bank.id)) emptyList()
        else bankBalances(bank).filter {
            BankAccountVisibility.visible(this, bank.id, it.lastFour)
        }

    private fun bankBalanceAmount(kopecks: Long): TextView =
        balanceAmountPill(rubles(kopecks))

    /** Rows share the same margins; narrow screens place the amount below. */
    private fun bankBalanceAccountRow(item: BankAccountBalance): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(3), dp(0), dp(3))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(textLine(BankAccountRows.label(item.lastFour), 14f, ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val amount = bankBalanceAmount(item.kopecks)
        val amountBelow = balancePillNeedsOwnLine(amount,
            BalanceAmountStyle.BANK_RESERVED_DP)
        if (!amountBelow) {
            row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            row.addView(amount)
            container.addView(row)
        } else {
            container.addView(row)
            container.addView(verticalGap(6))
            container.addView(LinearLayout(this).apply {
                gravity = Gravity.END
                addView(amount)
            })
        }
        container.addView(verticalGap(2))
        val updated = DateFormat.getDateTimeInstance(
            DateFormat.SHORT, DateFormat.SHORT).format(Date(item.observedAt))
        val stale = System.currentTimeMillis() - item.observedAt !in 0L..86_400_000L
        container.addView(textLine(
            "Подтверждено Push: $updated" +
                (if (stale) " · данные могут быть устаревшими" else ""),
            11f, muted))
        return container
    }

    private fun renderBankBalance(bank: RussianBank) {
        val enabled = BankCardPreferences.enabled(this, bank.id)
        val granted = SberBalanceStore.isAccessGranted(this)
        val all = if (enabled) bankBalances(bank) else emptyList()
        val visible = all.filter {
            BankAccountVisibility.visible(this, bank.id, it.lastFour)
        }
        val bankCard = card().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(bankLogo(bank, 40),
            LinearLayout.LayoutParams(dp(40), dp(40)))
        header.addView(View(this), LinearLayout.LayoutParams(dp(9), 1))
        val caption = when {
            !enabled -> "Push не подключён"
            !granted -> "Нет доступа к уведомлениям"
            all.isEmpty() -> "Ожидание банковского Push"
            visible.isEmpty() -> "Все карты и счета скрыты"
            else -> "Карты и счета: ${visible.size}"
        }
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine(bank.name, 16f, ink, true))
            addView(verticalGap(3))
            addView(textLine(caption, 12f, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bankCard.addView(header)
        if (visible.isNotEmpty()) {
            bankCard.addView(verticalGap(7))
            visible.forEachIndexed { i, item ->
                if (i > 0) {
                    bankCard.addView(View(this).apply {
                        setBackgroundColor(if (isNight) Color.rgb(63, 100, 102)
                            else Color.rgb(201, 228, 224))
                    }, LinearLayout.LayoutParams(-1, dp(1)))
                }
                bankCard.addView(bankBalanceAccountRow(item))
            }
        } else {
            bankCard.addView(verticalGap(12))
            val message = when {
                !enabled -> "Подключите Push в Настройки → Банки"
                !granted -> "Разрешите BalanceHub доступ к уведомлениям"
                all.isNotEmpty() -> "Остатки продолжают сохраняться. Покажите карты в Настройки → Банки."
                bank.id == "sber" &&
                    SberPushDiagnostics.latest(this)?.outcome ==
                        SberPushDiagnostics.Outcome.SYSTEM_REDACTED ->
                    "Android скрыл текст банковского Push"
                else -> "Ожидание Push с подтверждённым остатком в рублях"
            }
            bankCard.addView(textLine(message, 12f, muted))
        }
        bankCard.isClickable = true
        bankCard.isFocusable = true
        bankCard.contentDescription = "Сведения о балансах: ${bank.name}"
        bankCard.setOnClickListener { showBankBalancesDetails(bank) }
        contentContainer.addView(bankCard)
        contentContainer.addView(verticalGap(12))
    }

    private fun showBankBalancesDetails(bank: RussianBank) {
        val items = visibleBankBalances(bank)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(13), dp(19), dp(15))
        }
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bankLogo(bank, 46), LinearLayout.LayoutParams(dp(46), dp(46)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(12), 1))
            addView(textLine(bank.name, 19f, ink, true))
        })
        panel.addView(verticalGap(13))
        if (items.isEmpty()) {
            panel.addView(textLine("Нет отображаемых остатков. Проверьте Настройки → Банки.",
                13f, muted))
        } else {
            items.forEachIndexed { i, item ->
                if (i > 0) panel.addView(verticalGap(9))
                panel.addView(bankBalanceAccountRow(item))
            }
        }
        val dialog = themedDialogBuilder()
            .setTitle("Сведения о балансах")
            .setView(android.widget.ScrollView(this).apply { addView(panel) })
            .setNeutralButton("Скрыть карточку банка") { _, _ ->
                confirmHideBankCard(bank)
            }
            .setPositiveButton("Закрыть", null)
            .create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun bankLogo(bank: RussianBank, size: Int): View {
        if (bank.id == "sber") return sberLogo(size)
        val installed = RussianBankCatalog.installedPackage(this, bank)
        if (installed != null) {
            try {
                return ImageView(this).apply {
                    setImageDrawable(packageManager.getApplicationIcon(installed))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Значок установленного приложения: " + bank.name
                    layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Bank was uninstalled while the card was being constructed.
            }
        }
        return textLine(bank.monogram, 19f, accent, true).apply {
            gravity = Gravity.CENTER
            background = rounded(softAccent, 14)
            contentDescription = "Обозначение банка " + bank.name
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
    }

    private fun confirmHideBankCard(bank: RussianBank) {
        themedDialogBuilder().setTitle("Скрыть " + bank.name + "?")
            .setMessage("Карточка исчезнет из раздела «Балансы». " +
                "Сохранённый баланс не удалится, получение Push продолжится. " +
                "Вернуть банк можно через Настройки → Банки.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Скрыть") { _, _ ->
                BankCardPreferences.setVisible(this, bank.id, false)
                renderTab()
            }.show()
    }

    private fun showBankCardManager() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(15))
        }
        val dialog = themedDialogBuilder()
            .setTitle("Банковские карточки")
            .setView(android.widget.ScrollView(this).apply { addView(content) })
            .setPositiveButton("Готово", null).create()
        content.addView(textLine(
            "Показ карточки не влияет на сохранённый баланс или получение Push. " +
                "Подключение Push требует отдельного разрешения Android.",
            13f, muted))
        for (bank in RussianBankCatalog.available(this)) {
            content.addView(verticalGap(12))
            val entry = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(softAccent, 14)
            }
            entry.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(bankLogo(bank, 39),
                    LinearLayout.LayoutParams(dp(39), dp(39)))
                addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
                addView(textLine(bank.name, 15f, ink, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
            })
            val showToggle = SwitchCompat(this).apply {
                text = "Показывать карточку"
                textSize = 13f
                setTextColor(ink)
                isChecked = BankCardPreferences.visible(this@MainActivity, bank.id)
                setOnCheckedChangeListener { _, shown ->
                    BankCardPreferences.setVisible(this@MainActivity, bank.id, shown)
                }
            }
            entry.addView(showToggle)
            val accounts = bankBalances(bank)
            if (accounts.isNotEmpty()) {
                entry.addView(verticalGap(9))
                entry.addView(textLine("Карты и счета", 14f, ink, true))
                for (item in accounts) {
                    entry.addView(verticalGap(5))
                    entry.addView(SwitchCompat(this).apply {
                        text = BankAccountRows.label(item.lastFour)
                        textSize = 13f
                        setTextColor(ink)
                        isChecked = BankAccountVisibility.visible(
                            this@MainActivity, bank.id, item.lastFour)
                        setOnCheckedChangeListener { _, show ->
                            BankAccountVisibility.setVisible(
                                this@MainActivity, bank.id, item.lastFour, show)
                        }
                    })
                }
                entry.addView(textLine(
                    "Скрытые счета обновляются через Push и не удаляются.",
                    12f, muted))
            }
            entry.addView(verticalGap(5))
            entry.addView(textLine(when {
                BankCardPreferences.enabled(this, bank.id) ->
                    "Получение Push включено"
                RussianBankCatalog.installedPackage(this, bank) != null ->
                    "Приложение найдено · Push не подключён"
                else -> "Приложение не найдено"
            }, 12f, muted))
            entry.addView(verticalGap(8))
            entry.addView(secondaryButton(if (BankCardPreferences.enabled(this, bank.id))
                "Отключить получение Push" else "Подключить Push") {
                if (BankCardPreferences.enabled(this, bank.id)) {
                    themedDialogBuilder().setTitle("Отключить Push " + bank.name + "?")
                        .setMessage("Будет удалён сохранённый остаток этого банка. " +
                            "Карточку можно скрыть отдельно, не отключая Push.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Отключить") { _, _ ->
                            BankCardPreferences.setEnabled(this, bank.id, false)
                            dialog.dismiss()
                            renderTab()
                        }.show()
                } else {
                    themedDialogBuilder().setTitle("Подключить " + bank.name + "?")
                        .setMessage("Для получения баланса BalanceHub нужен широкий " +
                            "системный доступ к уведомлениям. Приложение обрабатывает " +
                            "Push выбранного банка локально; распознавание возможно " +
                            "только при наличии однозначного остатка в рублях.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Подключить") { _, _ ->
                            BankCardPreferences.setEnabled(this, bank.id, true)
                            dialog.dismiss()
                            renderTab()
                            if (!SberBalanceStore.isAccessGranted(this))
                                openSberListenerSettings()
                        }.show()
                }
            })
            content.addView(entry)
        }
        dialog.setOnDismissListener { if (currentTab == 4) renderTab() }
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun refreshSimsInSequence(sims: List<MobileSim>, index: Int) {
        if (index >= sims.size) {
            if (currentTab == 1 && !operatorDetailsOpen) renderTab()
            return
        }
        val sim = sims[index]
        MobileBalanceService.check(this, sim, false) { _ ->
            runOnUiThread {
                syncMobilePayment(sim)
                refreshSimsInSequence(sims, index + 1)
            }
        }
    }

    private fun formatT2Date(instant: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("ru")).format(Date(instant))

    private fun formatT2DateTime(instant: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", java.util.Locale("ru")).format(Date(instant))

    private fun isT2(sim: MobileSim): Boolean =
        MobileBalanceService.operator(this, sim)?.displayName == "T2 (Tele2)"

    private fun paymentIdFor(sim: MobileSim): String =
        (if (isT2(sim)) "t2_subscription_" else "mobile_subscription_") + sim.subscriptionId

    private fun feeKeyFor(sim: MobileSim): String =
        (if (isT2(sim)) "t2_fee_" else "mobile_fee_") + sim.subscriptionId

    private fun dateKeyFor(sim: MobileSim): String =
        (if (isT2(sim)) "t2_manual_debit_" else "mobile_manual_debit_") + sim.subscriptionId

    private fun enabledKeyFor(sim: MobileSim): String =
        (if (isT2(sim)) "t2_payment_enabled_" else "mobile_payment_enabled_") + sim.subscriptionId

    private fun operatorDueAt(sim: MobileSim): Long? {
        val manual = preferences.getLong(dateKeyFor(sim), 0L).takeIf { it > 0 }
        val estimated = if (isT2(sim)) T2UssdParser.estimatedNextDebit(
            T2Balance.packages(this, sim.subscriptionId)?.packagesExpireAt
        ) else null
        val firstDue = manual ?: estimated ?: return null
        return Calendar.getInstance().apply {
            timeInMillis = firstDue
            while (timeInMillis < System.currentTimeMillis()) add(Calendar.MONTH, 1)
        }.timeInMillis
    }

    /** One stable reminder ID per SIM; preserve T2 records from previous versions. */
    private fun syncMobilePayment(sim: MobileSim?) {
        if (sim == null) return
        val id = paymentIdFor(sim)
        val legacyId = "t2_subscription_" + sim.subscriptionId
        if (!isT2(sim) && legacyId != id) {
            payments.firstOrNull { it.id == legacyId }?.let {
                cancelReminder(it)
                payments.remove(it)
                if (!persistPayments()) {
                    payments.clear()
                    loadPayments()
                    return
                }
            }
        }
        val old = payments.firstOrNull { it.id == id }
        if (!preferences.getBoolean(enabledKeyFor(sim), true)) {
            if (old != null) {
                cancelReminder(old)
                payments.remove(old)
                if (!persistPayments()) {
                    payments.clear()
                    loadPayments()
                }
            }
            return
        }
        val fee = preferences.getString(feeKeyFor(sim), null)?.trim()?.replace(',', '.')
        if (fee.isNullOrBlank() || !fee.matches(Regex("""\d{1,7}(?:\.\d{1,2})?""")) ||
            fee.toBigDecimalOrNull()?.signum() != 1) return
        val due = operatorDueAt(sim) ?: old?.dueAt ?: return
        val name = if (isT2(sim)) "T2" else MobileBalanceService.displayName(this, sim)
        val newPayment = PaymentReminder(id, "Абонентская плата " + name,
            fee, "RUB", due, 1, "monthly")
        if (old == newPayment) return
        if (old != null) {
            cancelReminder(old)
            payments.remove(old)
        }
        payments.add(newPayment)
        if (!persistPayments()) {
            payments.clear()
            loadPayments()
            return
        }
        scheduleReminder(newPayment)
    }

    private fun renderOperatorDetails() {
        contentContainer.addView(secondaryButton("‹  К балансам") {
            operatorDetailsOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(14))
        val sim = currentSim()
        if (sim == null) {
            contentContainer.addView(textLine("SIM-карта не найдена", 16f, ink))
            return
        }
        val name = MobileBalanceService.displayName(this, sim)
        val op = MobileBalanceService.operator(this, sim)
        val balance = MobileBalanceService.cached(this, sim)
        val due = operatorDueAt(sim)
        val fee = preferences.getString(feeKeyFor(sim), null)
        val manualDate = preferences.getLong(dateKeyFor(sim), 0L) > 0L

        contentContainer.addView(textLine(name + " · Подробная информация", 22f, ink, true))
        contentContainer.addView(verticalGap(14))

        val summary = card()
        summary.addView(textLine(name + " · " + sim.label, 18f, ink, true))
        summary.addView(verticalGap(7))
        summary.addView(textLine("Текущий известный баланс", 13f, muted))
        summary.addView(textLine(balance?.first?.let { it + " ₽" }
            ?: "Упс, пока нет данных", if (balance == null) 19f else 28f, accent, true))
        if (balance == null) {
            summary.addView(verticalGap(7))
            summary.addView(textLine("Баланс ещё не получен. Проверьте SIM-карту, " +
                "разрешения и попробуйте обновить данные.", 13f, muted))
        }
        balance?.let {
            summary.addView(textLine("Обновлён: " + formatT2DateTime(it.second), 12f, muted))
        }
        contentContainer.addView(summary)
        contentContainer.addView(verticalGap(12))

        val billing = card()
        billing.addView(textLine("Тариф и списание", 18f, ink, true))
        billing.addView(verticalGap(10))
        billing.addView(textLine("Абонентская плата: " +
            (fee?.let { it + " ₽" } ?: "не указана"), 15f, ink, true))
        billing.addView(verticalGap(8))
        billing.addView(textLine(
            (if (manualDate) "Дата списания (указана вручную): "
             else "Предполагаемое списание: ") +
            (due?.let { formatT2Date(it) } ?: "нет данных"),
            14f, accent, true
        ))
        if (!manualDate && isT2(sim)) {
            billing.addView(verticalGap(6))
            billing.addView(textLine(
                "Расчёт по окончанию пакетов не подтверждён оператором.",
                12f, muted
            ))
        }
        contentContainer.addView(billing)
        contentContainer.addView(verticalGap(12))

        val controls = card()
        controls.addView(textLine("Обновление и управление", 18f, ink, true))
        controls.addView(verticalGap(8))
        val code = op?.balanceUssd
        val status = textLine(
            if (code != null) "Проверка баланса: $code"
            else "USSD этого оператора не подтверждён. Доступен ручной ввод.",
            13f, muted
        )
        controls.addView(status)
        if (code != null) {
            controls.addView(verticalGap(10))
            controls.addView(primaryButton("Проверить баланс · " + code) {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        this, arrayOf(android.Manifest.permission.CALL_PHONE,
                            android.Manifest.permission.READ_PHONE_STATE), 510)
                    status.text = "Разрешите доступ к телефону и повторите проверку"
                } else {
                    status.text = "Проверяем баланс…"
                    MobileBalanceService.check(this, sim, true) { message ->
                        runOnUiThread {
                            syncMobilePayment(sim)
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            if (currentTab == 1 && operatorDetailsOpen) renderTab()
                        }
                    }
                }
            })
        }
        controls.addView(verticalGap(9))
        controls.addView(secondaryButton("Ввести баланс вручную") {
            showManualBalanceDialog(sim)
        })
        controls.addView(verticalGap(9))
        val configureButton = secondaryButton("Настроить абонентскую плату и дату") {
            if (tutorialStep == 2) tutorialStep = 3
            operatorDetailsOpen = false
            selectedOperatorId = sim.subscriptionId
            mobileSettingsOpen = true
            selectTab(4)
        }
        if (tutorialStep == 2) highlightTutorialTarget(configureButton)
        controls.addView(configureButton)
        contentContainer.addView(controls)
    }

    private fun showManualBalanceDialog(sim: MobileSim) {
        val value = field("Баланс в рублях").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(MobileBalanceService.cached(this@MainActivity, sim)?.first ?: "")
        }
        themedDialogBuilder()
            .setTitle("Указать мобильный баланс")
            .setView(LinearLayout(this).apply {
                setPadding(dp(22), dp(12), dp(22), dp(12))
                addView(value)
            })
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                if (MobileBalanceService.setManual(this, sim, value.text.toString())) {
                    if (currentTab == 1) renderTab()
                    else if (currentTab == 4 && mobileSettingsOpen) renderTab()
                } else {
                    Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun renderPaymentScreen() {
        if (winterActive) {
            contentContainer.addView(textLine("Платежи", 23f, ink, true))
            contentContainer.addView(verticalGap(11))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = cardGradient()
                setPadding(dp(3), dp(3), dp(3), dp(3))
                addView(textLine("Активные  ${payments.size}", 13f,
                    Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    background = filledButtonGradient()
                }, LinearLayout.LayoutParams(0, dp(38), 1f))
                addView(textLine("Архив", 13f, ink, true).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "Архив платежей"
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        archiveOpen = true
                        expandedArchivePaymentId = null
                        renderTab()
                    }
                }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            contentContainer.addView(row)
            contentContainer.addView(verticalGap(12))
            listContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            contentContainer.addView(listContainer)
            renderPayments()
            contentContainer.addView(verticalGap(8))
            val add = primaryButton("＋") { showNewPaymentDialog() }.apply {
                contentDescription = "Добавить платёж"
                minWidth = dp(48)
            }
            val position = LinearLayout(this).apply {
                gravity = Gravity.END
                addView(add, LinearLayout.LayoutParams(dp(51), dp(51)))
            }
            contentContainer.addView(position)
            return
        }
        contentContainer.addView(textLine("Платежи", 23f, ink, true))
        contentContainer.addView(verticalGap(6))
        contentContainer.addView(textLine(
            "Все ваши платежи и расходы под контролем", 13f, muted
        ))
        contentContainer.addView(verticalGap(13))
        contentContainer.addView(primaryButton("＋  Добавить платёж") {
            showNewPaymentDialog()
        })
        contentContainer.addView(verticalGap(17))
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contentContainer.addView(listContainer)
        renderPayments()
    }


    /**
     * Custom payment dates use a dedicated multi-select calendar. Other date
     * pickers remain single-select and retain their original behavior.
     */
    private fun installCustomPaymentControls(
        form: LinearLayout, recurrence: Spinner, initial: PaymentPlan?
    ) {
        var mode = initial?.mode ?: "dates"
        val dates = initial?.selectedDates?.toMutableSet() ?: linkedSetOf()
        val monthlyDays = initial?.monthlyDays?.toMutableSet() ?: linkedSetOf()
        var monthlyEnabled = initial?.monthlyEnabled ?: false
        var endsAt = initial?.endsAt
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(6), dp(7), dp(6))
            background = rounded(softAccent, 12)
        }
        val modeSpinner = styledSpinner(
            listOf("Конкретные даты", "Числа каждого месяца"),
            if (mode == "monthly") 1 else 0, horizontalInsetDp = 7
        )
        val datePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val monthlyPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dateInfo = customRecurrenceCaption(horizontalInsetDp = 7)
        val endInfo = customRecurrenceCaption(horizontalInsetDp = 7)
        fun store() {
            pendingPaymentPlan = PaymentPlan(
                mode, dates.toSet(), monthlyDays.toSet(), monthlyEnabled,
                initial?.startsAt ?: 0L, endsAt,
                initial?.edits ?: emptyMap(),
                initial?.history ?: emptyList(),
                initial?.bulkEdits ?: emptyList()
            )
        }
        fun refresh() {
            dateInfo.text = if (dates.isEmpty()) "Даты не выбраны"
                else dates.sorted().joinToString(", ") {
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it))
                }
            endInfo.text = if (endsAt == null) "Дата окончания не задана"
                else "До " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(endsAt!!))
            datePanel.visibility = if (mode == "dates") View.VISIBLE else View.GONE
            monthlyPanel.visibility = if (mode == "monthly") View.VISIBLE else View.GONE
            store()
        }
        datePanel.addView(dateInfo)
        datePanel.addView(verticalGap(6))
        datePanel.addView(secondaryButton("＋ Добавить дату") {
            showThemedMultiDateCalendar(dates) { selected ->
                dates.clear()
                dates.addAll(selected)
                refresh()
            }
        })
        datePanel.addView(verticalGap(6))
        datePanel.addView(secondaryButton("Очистить выбранные даты") {
            dates.clear()
            refresh()
        })
        val monthlySwitch = SwitchCompat(this).apply {
            text = "Повторять каждый месяц"
            textSize = 14f
            isChecked = monthlyEnabled
            setTextColor(ink)
            setPadding(dp(7), dp(8), dp(4), dp(8))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(accent, if (isNight) Color.rgb(76, 113, 112)
                    else Color.rgb(172, 205, 200))
            )
            setOnCheckedChangeListener { _, checked ->
                monthlyEnabled = checked
                store()
            }
        }
        monthlyPanel.addView(monthlySwitch)
        monthlyPanel.addView(verticalGap(6))
        monthlyPanel.addView(customRecurrenceCaption("Выберите числа месяца", horizontalInsetDp = 7))
        monthlyPanel.addView(verticalGap(8))
        val selectionSummary = customRecurrenceCaption(horizontalInsetDp = 7)
        fun refreshSelectionSummary() {
            selectionSummary.text = if (monthlyDays.isEmpty()) "Выбрано: 0"
                else "Выбрано: ${monthlyDays.size} · ${monthlyDays.sorted().joinToString(", ")}"
        }
        val dayGrid = recurringChoiceRows(
            (1..31).map { it.toString() to it }, 7,
            "Число месяца", monthlyDays
        ) { number, enabled ->
            if (enabled) monthlyDays.add(number) else monthlyDays.remove(number)
            refreshSelectionSummary()
            store()
        }
        monthlyPanel.addView(dayGrid)
        monthlyPanel.addView(verticalGap(8))
        monthlyPanel.addView(selectionSummary)
        refreshSelectionSummary()
        panel.addView(modeSpinner)
        panel.addView(verticalGap(8))
        panel.addView(datePanel)
        panel.addView(monthlyPanel)
        panel.addView(verticalGap(8))
        panel.addView(endInfo)
        panel.addView(secondaryButton("Выбрать дату окончания") {
            val selected = Calendar.getInstance().apply {
                timeInMillis = endsAt ?: System.currentTimeMillis()
            }
            showThemedCalendar(selected) {
                endsAt = PaymentOccurrenceEngine.day(selected.timeInMillis)
                refresh()
            }
        })
        panel.addView(verticalGap(5))
        panel.addView(secondaryButton("Без даты окончания") {
            endsAt = null
            refresh()
        })
        modeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                mode = if (position == 1) "monthly" else "dates"
                refresh()
            }
        }
        recurrence.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                panel.visibility = if (position == 3) View.VISIBLE else View.GONE
            }
        }
        form.addView(panel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        panel.visibility = if (recurrence.selectedItemPosition == 3) View.VISIBLE else View.GONE
        refresh()
    }

    private fun validCustomPaymentPlan(plan: PaymentPlan?): Boolean {
        if (plan == null) return false
        return when (plan.mode) {
            "dates" -> plan.selectedDates.isNotEmpty()
            "monthly" -> plan.monthlyEnabled && plan.monthlyDays.isNotEmpty()
            else -> false
        }
    }

    /** Payment creation uses the same controls as before, now in a scrollable dialog. */
    private fun showNewPaymentDialog() {
        editingId = null
        pendingPaymentPlan = null
        selectedPaymentIcon = null
        dueDate = ReminderDateTimePolicy.atOpening()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(18))
        }
        titleInput = field("Название платежа")
        amountInput = field("Сумма").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        currencyInput = field("Валюта (RUB, EUR...)").apply { setText("RUB") }
        leadDaysInput = field("Напомнить за сколько дней").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("3")
        }
        val iconRow = iconSelector(titleInput, "credit-card", null) {
            selectedPaymentIcon = it
        }
        dateButton = Button(this).apply {
            isAllCaps = false
            setTextColor(accent)
            background = rounded(softAccent, 12)
            setOnClickListener { chooseDate() }
        }
        updateDateButton()
        val timeButton = secondaryButton("Время: " +
            java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(dueDate.time)) {}
        timeButton.setOnClickListener {
            BalanceTimePicker.show(this, dueDate.get(Calendar.HOUR_OF_DAY),
                dueDate.get(Calendar.MINUTE)) { hour, minute ->
                dueDate.set(Calendar.HOUR_OF_DAY, hour)
                dueDate.set(Calendar.MINUTE, minute)
                dueDate.set(Calendar.SECOND, 0)
                dueDate.set(Calendar.MILLISECOND, 0)
                timeButton.text = "Время: " +
                    java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(dueDate.time)
            }
        }
        val recOptions = listOf("Единоразовый", "Еженедельный", "Ежемесячный", "Пользовательское")
        recurrenceInput = styledSpinner(recOptions)
        form.addView(formFieldCaption("Название"))
        form.addView(titleInput)
        form.addView(verticalGap(10))
        form.addView(formFieldCaption("Иконка"))
        form.addView(iconRow)
        form.addView(verticalGap(10))
        form.addView(formFieldCaption("Сумма"))
        form.addView(amountInput)
        form.addView(verticalGap(10))
        form.addView(formFieldCaption("Валюта"))
        form.addView(currencyInput)
        form.addView(verticalGap(12))
        val paymentDateTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        paymentDateTime.addView(dateButton, LinearLayout.LayoutParams(0, dp(50), 3f))
        paymentDateTime.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        paymentDateTime.addView(timeButton, LinearLayout.LayoutParams(0, dp(50), 2f))
        form.addView(paymentDateTime)
        form.addView(verticalGap(12))
        form.addView(formFieldCaption("Повторение платежа"))
        form.addView(verticalGap(6))
        form.addView(recurrenceInput)
        installCustomPaymentControls(form, recurrenceInput, null)
        form.addView(verticalGap(12))
        form.addView(formFieldCaption("Напомнить за сколько дней"))
        form.addView(leadDaysInput)
        val dialog = themedDialogBuilder().setTitle("Новый платёж")
            .setView(android.widget.ScrollView(this).apply {
                isFillViewport = false
                addView(form)
            })
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(dialogGradient())
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ink)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (saveReminder()) dialog.dismiss()
            }
        }
        dialog.show()
    }


    private fun settingsScreen() = settingsStack.lastOrNull() ?: "root"
    private fun openSettingsPage(page: String) {
        if (!SettingsNavigationPolicy.canOpen(settingsScreen(), page)) return
        settingsScrollPositions[settingsScreen()] = dashboardScroll.scrollY
        settingsStack.add(page)
        renderTab()
        dashboardScroll.post { if (currentTab == 4) dashboardScroll.scrollTo(0, 0) }
    }
    private fun backSettingsPage() {
        if (settingsStack.isEmpty()) return
        settingsStack.removeAt(settingsStack.lastIndex)
        val previous = settingsScrollPositions[settingsScreen()] ?: 0
        renderTab()
        dashboardScroll.post { if (currentTab == 4) dashboardScroll.scrollTo(0, previous) }
    }

    private fun compactSettingsCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = cardGradient()
    }
    private fun settingsPanel(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (winterActive) {
            // The reference uses an individually frosted tile for each setting.
            views.forEach { view ->
                val tile = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = cardGradient()
                    addView(view)
                }
                addView(tile, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(7)
                })
            }
        } else {
            background = cardGradient()
            views.forEachIndexed { i, view ->
                if (i > 0) addView(View(this@MainActivity).apply {
                    setBackgroundColor(if (isNight) Color.rgb(63, 100, 102)
                        else Color.rgb(201, 228, 224))
                }, LinearLayout.LayoutParams(-1, dp(1)).apply {
                    marginStart = dp(14)
                    marginEnd = dp(14)
                })
                addView(view)
            }
        }
    }
    private fun settingsGroup(panel: View) {
        contentContainer.addView(panel)
        contentContainer.addView(verticalGap(12))
    }
    private fun settingsRow(glyph: String, name: String, summary: String,
        action: (() -> Unit)? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(60)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        addView(iconTile(glyph, 33), LinearLayout.LayoutParams(dp(33), dp(33)))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(11), 1))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine(name, 15f, ink, true).apply { maxLines = 2 })
            if (summary.isNotBlank()) {
                addView(verticalGap(3))
                addView(textLine(summary, 12f, muted).apply { maxLines = 2 })
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null) {
            addView(textLine("›", 24f, muted, true))
            isClickable = true
            isFocusable = true
            contentDescription = name + if (summary.isEmpty()) "" else ", " + summary
            setOnClickListener { action() }
        }
    }
    private fun settingsSwitch(glyph: String, name: String, summary: String,
        checked: Boolean, change: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(60)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        addView(iconTile(glyph, 33), LinearLayout.LayoutParams(dp(33), dp(33)))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(11), 1))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine(name, 15f, ink, true))
            addView(verticalGap(3))
            addView(textLine(summary, 12f, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val control = SwitchCompat(this@MainActivity).apply {
            isChecked = checked
            contentDescription = name
            setOnCheckedChangeListener { _, value -> change(value) }
        }
        addView(control)
        isClickable = true
        isFocusable = true
        setOnClickListener { control.isChecked = !control.isChecked }
    }
    private fun settingsHeader(title: String, summary: String = "") {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
        }
        row.addView(textLine("‹", 32f, accent, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Назад"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                mobileSettingsOpen = false
                backSettingsPage()
            }
        }, LinearLayout.LayoutParams(dp(43), dp(48)))
        row.addView(textLine(title, 23f, ink, true).apply { maxLines = 2 },
            LinearLayout.LayoutParams(0, -2, 1f))
        contentContainer.addView(row)
        if (summary.isNotBlank()) contentContainer.addView(textLine(summary, 12f, muted))
        contentContainer.addView(verticalGap(13))
    }

    private fun openAndroidNotificationSettings(category: String? = null) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ("package:" + packageName).toUri())
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(if (category == null) Settings.ACTION_APP_NOTIFICATION_SETTINGS
                else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                if (category != null) putExtra(Settings.EXTRA_CHANNEL_ID,
                    BalanceNotificationSounds.ensureChannel(this@MainActivity, category))
            }
        } else fallback
        try { startActivity(intent) }
        catch (_: Exception) {
            try { startActivity(fallback) }
            catch (_: Exception) {
                Toast.makeText(this, "Откройте уведомления BalanceHub в Android",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderSettings() {
        if (engineeringMenuOpen) {
            when {
                bankSettingsOpen -> renderBankSettings()
                engineeringOtherBankId != null -> renderOtherBankEngineering()
                engineeringBanksOpen -> renderEngineeringBanks()
                engineeringNotificationsOpen -> renderEngineeringNotifications()
                engineeringDeliveryOpen -> renderEngineeringDelivery()
                engineeringWinterOpen -> renderEngineeringWinter()
                engineeringChannelCategory != null -> renderEngineeringChannelCategory()
                engineeringChannelsOpen -> renderEngineeringChannels()
                else -> renderEngineeringMenu()
            }
            return
        }
        bankSettingsOpen = false
        if (mobileSettingsOpen || settingsScreen() == "mobile") {
            renderMobileSettings()
            return
        }
        when (settingsScreen()) {
            "appearance" -> renderAppearanceSettings()
            "security" -> renderSecuritySettings()
            "banks" -> renderBanksSettings()
            "notifications" -> renderNotificationsMenu()
            "payment", "event", "update_sound" -> renderNotificationCategory()
            "updates" -> renderUpdateSettings()
            "history" -> renderReleaseHistorySettings()
            "help" -> renderHelpSettings()
            "about" -> renderAboutSettings()
            else -> renderSettingsHome()
        }
    }
    private fun renderSettingsHome() {
        contentContainer.addView(textLine("Настройки", 23f, ink, true).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Настройки"
            setOnClickListener {
                val now = SystemClock.elapsedRealtime()
                if (now - engineeringTapStartedAt > 1_500L || engineeringTapCount == 0) {
                    engineeringTapStartedAt = now
                    engineeringTapCount = 0
                }
                engineeringTapCount++
                if (engineeringTapCount >= 5) {
                    engineeringTapCount = 0
                    engineeringMenuOpen = true
                    renderTab()
                }
            }
        })
        contentContainer.addView(verticalGap(5))
        contentContainer.addView(textLine("Управление BalanceHub", 12f, muted))
        contentContainer.addView(verticalGap(16))
        val appInfo = packageManager.getPackageInfo(packageName, 0)
        if (winterActive) {
            settingsGroup(settingsPanel(
                settingsRow("credit-card", "Банки и счета",
                    "Подключение и управление") { openSettingsPage("banks") },
                settingsRow("smartphone", "Мобильные операторы",
                    "Баланс и обновление") { openSettingsPage("mobile") },
                settingsRow("bell", "Уведомления",
                    "Напоминания и оповещения") { openSettingsPage("notifications") },
                settingsRow("settings", "Оформление",
                    "Тема, цвета и сезонный дизайн") { openSettingsPage("appearance") },
                settingsRow("download", "Обновления",
                    "Версия приложения и автообновление") {
                    openSettingsPage("updates")
                },
                settingsRow("shield-check", "Безопасность",
                    "PIN-код и защита данных") {
                    if (AppLockSecurity.supported()) openSettingsPage("security")
                    else Toast.makeText(this, "Защита доступна с Android 11",
                        Toast.LENGTH_LONG).show()
                },
                settingsRow("info", "О приложении",
                    "Версия, лицензии и контакты") { openSettingsPage("about") },
                settingsRow("help-circle", "Помощь и диагностика",
                    "Проверки и инструкции") { openSettingsPage("help") }
            ))
            return
        }
        settingsGroup(settingsPanel(
            settingsRow("settings", "Оформление", "Авто, светлая или тёмная") {
                openSettingsPage("appearance")
            },
            settingsRow("shield-check", "Безопасность",
                if (AppLockSecurity.supported()) "Код доступа и отпечаток пальца"
                else "Доступно с Android 11") {
                if (AppLockSecurity.supported()) openSettingsPage("security")
                else Toast.makeText(this, "Защита доступна с Android 11",
                    Toast.LENGTH_LONG).show()
            },
            settingsRow("smartphone", "Мобильная связь", "SIM, баланс и абонентская плата") {
                openSettingsPage("mobile")
            },
            settingsRow("credit-card", "Банки", "Карточки и банковские Push") {
                openSettingsPage("banks")
            },
            settingsRow("music", "Уведомления", "Звуки, громкость и вибрация") {
                openSettingsPage("notifications")
            },
            settingsRow("download", "Обновления",
                "Версия " + (appInfo.versionName ?: "неизвестна")) {
                openSettingsPage("updates")
            },
            settingsRow("settings", "Помощь и диагностика", "Проверки и инструкции") {
                openSettingsPage("help")
            },
            settingsRow("settings", "О приложении", "Сведения о BalanceHub") {
                openSettingsPage("about")
            }
        ))
    }
    private fun renderAppearanceSettings() {
        settingsHeader("Оформление", "Тема приложения")
        val current = preferences.getString("appearance", "system") ?: "system"
        val options = listOf(
            Triple("system", "Автоматически", "Тема устройства"),
            Triple("day", "Светлая", "Светлый интерфейс"),
            Triple("night", "Тёмная", "Тёмный интерфейс")
        )
        settingsGroup(settingsPanel(*options.map { (mode, title, description) ->
            settingsRow(if (mode == "day") "sun" else if (mode == "night") "moon"
                else "settings", title,
                description + if (mode == current) " · выбрано ✓" else "") {
                if (mode != preferences.getString("appearance", "system")) {
                    preferences.edit().putString("appearance", mode).apply()
                    AppCompatDelegate.setDefaultNightMode(when (mode) {
                        "day" -> AppCompatDelegate.MODE_NIGHT_NO
                        "night" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    })
                    recreate()
                }
            }
        }.toTypedArray()))
    }
    private fun toggleBankPush(bank: RussianBank) {
        val enabled = BankCardPreferences.enabled(this, bank.id)
        themedDialogBuilder()
            .setTitle((if (enabled) "Отключить " else "Подключить ") +
                "Push " + bank.name + "?")
            .setMessage(if (enabled)
                "Отключение удалит сохранённый остаток банка. " +
                    "Чтобы скрыть карточку, используйте переключатель видимости."
                else "BalanceHub распознаёт только подтверждённые остатки из " +
                    "Push выбранного банка. Нужен системный доступ Android к уведомлениям.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton(if (enabled) "Отключить" else "Подключить") { _, _ ->
                BankCardPreferences.setEnabled(this, bank.id, !enabled)
                renderTab()
                if (!enabled && !SberBalanceStore.isAccessGranted(this))
                    openSberListenerSettings()
            }.show()
    }

    private fun renderBanksSettings() {
        settingsHeader("Банки")
        for (bank in RussianBankCatalog.available(this)) {
            // One heading and one continuous panel for every bank. The account
            // switches use the same original callbacks and preference keys.
            contentContainer.addView(textLine(
                "Карты и счета · " + bank.name, 14f, ink, true))
            contentContainer.addView(verticalGap(7))

            val rows = mutableListOf<View>(
                settingsSwitch("credit-card", bank.name,
                    "Показывать блок банка в «Балансах»",
                    BankCardPreferences.visible(this, bank.id)) { show ->
                    BankCardPreferences.setVisible(this, bank.id, show)
                    refreshSettingsWithoutJump()
                },
                settingsRow("settings", "Получение Push",
                    if (BankCardPreferences.enabled(this, bank.id))
                        "Включено · нажмите для отключения"
                    else "Не подключено · нажмите для подключения") {
                    toggleBankPush(bank)
                }
            )
            val accounts = bankBalances(bank)
            if (accounts.isEmpty()) {
                rows.add(settingsRow("credit-card", "Карты и счета пока не найдены",
                    "Новые появятся после Push"))
            } else {
                for (item in accounts) {
                    rows.add(settingsSwitch("credit-card",
                        BankAccountRows.label(item.lastFour),
                        "Баланс: " + rubles(item.kopecks) +
                            " · скрытие не отключает Push",
                        BankAccountVisibility.visible(this, bank.id, item.lastFour)) { show ->
                        BankAccountVisibility.setVisible(this, bank.id, item.lastFour, show)
                        refreshSettingsWithoutJump()
                    })
                }
            }
            settingsGroup(settingsPanel(*rows.toTypedArray()))
        }
        settingsGroup(settingsPanel(settingsRow("settings", "Доступ Android",
            "Разрешить чтение банковских уведомлений") {
            openSberListenerSettings()
        }))
    }
    private fun refreshSettingsWithoutJump() {
        val at = dashboardScroll.scrollY
        renderTab()
        dashboardScroll.post { if (currentTab == 4) dashboardScroll.scrollTo(0, at) }
    }

    private fun renderNotificationsMenu() {
        settingsHeader("Уведомления")
        settingsGroup(settingsPanel(
            settingsRow("credit-card", "Платежи",
                BalanceNotificationSounds.effectiveSummary(this,
                    BalanceNotificationSounds.PAYMENT)) { openSettingsPage("payment") },
            settingsRow("calendar-days", "События",
                BalanceNotificationSounds.effectiveSummary(this,
                    BalanceNotificationSounds.EVENT)) { openSettingsPage("event") },
            settingsRow("download", "Обновления",
                BalanceNotificationSounds.effectiveSummary(this,
                    BalanceNotificationSounds.UPDATE)) { openSettingsPage("update_sound") }
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settingsGroup(settingsPanel(
                settingsSwitch("smartphone", "Совместимость с ColorOS",
                    "Для OnePlus: звук через Android, вибрация напрямую. На Xiaomi не включать.",
                    BalanceNotificationSounds.colorOsCompatibility(this)) { enable ->
                    val dialog = themedDialogBuilder()
                        .setTitle(if (enable) "Включить обход ColorOS?"
                            else "Выключить обход ColorOS?")
                        .setMessage(
                            "BalanceHub создаст по одному новому каналу для платежей, " +
                            "событий и обновлений. Старые настройки этих каналов " +
                            "Android (в том числе мелодии, выбранные вручную) " +
                            "могут сброситься. Сами платежи, события и напоминания " +
                            "останутся.\n\n" +
                            if (enable)
                                "Системная вибрация новых каналов будет выключена, " +
                                    "а пользовательский сигнал запустит BalanceHub. " +
                                    "В режиме «Без звука», при «Не беспокоить» и " +
                                    "отключённых уведомлениях дополнительная вибрация " +
                                    "не запускается."
                            else "Будут созданы стандартные каналы Android. " +
                                "Ручные настройки существующих каналов могут сброситься.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Подтвердить") { _, _ ->
                            val error = BalanceNotificationSounds.changeColorOsCompatibility(
                                this, enable)
                            if (error != null) {
                                themedDialogBuilder().setTitle("Режим не изменён")
                                    .setMessage(error)
                                    .setPositiveButton("Закрыть", null).show()
                            } else {
                                Toast.makeText(this,
                                    if (enable) "Совместимость с ColorOS включена"
                                    else "Стандартные каналы восстановлены",
                                    Toast.LENGTH_LONG).show()
                            }
                            refreshSettingsWithoutJump()
                        }.create()
                    dialog.setOnDismissListener { refreshSettingsWithoutJump() }
                    dialog.show()
                }
            ))
        }
        settingsGroup(settingsPanel(settingsRow("settings",
            "Общие настройки уведомлений Android",
            "Разрешение, звук, вибрация и категории приложения") {
            openAndroidNotificationSettings()
        }))
    }

    private fun notificationCategory() = when (settingsScreen()) {
        "event" -> BalanceNotificationSounds.EVENT
        "update_sound" -> BalanceNotificationSounds.UPDATE
        else -> BalanceNotificationSounds.PAYMENT
    }
    private fun notificationTitle(category: String) = when (category) {
        BalanceNotificationSounds.EVENT -> "События"
        BalanceNotificationSounds.UPDATE -> "Обновления"
        else -> "Платежи"
    }
    private fun renderNotificationCategory() {
        val category = notificationCategory()
        settingsHeader(notificationTitle(category))
        val current = BalanceNotificationSounds.volume(this, category)
        val level = textLine("$current%", 13f, accent, true)
        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(7))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(textLine("Громкость", 15f, ink, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(level)
            })
            addView(SeekBar(this@MainActivity).apply {
                max = 100
                progress = current
                progressTintList = android.content.res.ColorStateList.valueOf(accent)
                thumbTintList = android.content.res.ColorStateList.valueOf(accent)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int,
                        fromUser: Boolean) { level.text = "$value%" }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) {
                        val gain = (bar?.progress ?: current).coerceIn(0, 100)
                        // Slider changes apply directly. A WAV's gain is stored
                        // for automatic playback or verified in a native channel.
                        // The melody-selection trial remains a separate flow.
                        val error = BalanceNotificationSounds.applyVolumeChoice(
                            this@MainActivity, category, gain)
                        if (error != null) {
                            Toast.makeText(this@MainActivity, error,
                                Toast.LENGTH_LONG).show()
                        }
                        // Re-read the committed value (or the unchanged one on
                        // failure). No test/confirmation dialog is displayed.
                        refreshSettingsWithoutJump()
                    }
                })
            })
        }
        val rows = mutableListOf<View>()
        if (category == BalanceNotificationSounds.PAYMENT &&
            PaymentToneChange.pending(this) != null) {
            rows.add(settingsRow("music", "Проверить новую мелодию платежей",
                "Рабочий канал сохранён; подтвердите звук после теста") {
                showPaymentSoundTrialDialog()
            })
        }
        if (category != BalanceNotificationSounds.PAYMENT &&
            CategoryToneChange.pending(this, category) != null) {
            rows.add(settingsRow("music",
                "Проверить новую мелодию " + notificationTitle(category).lowercase(),
                "Рабочий канал сохранён; подтвердите звук после теста") {
                showCategorySoundTrialDialog(category)
            })
        }
        // Everyday settings retain safe restoration of a confirmed melody.
        // Experimental sound switching is available in engineering channels.
        if (category != BalanceNotificationSounds.PAYMENT &&
            PaymentAutomaticSound.shouldOffer(this) &&
            PaymentAutomaticSound.enabled(this, category) &&
            CategoryToneChange.canRestorePrevious(this, category)) {
            val previousTitle = CategoryToneChange.previousSoundTitle(this, category)
            rows.add(settingsRow("music", "Вернуть прежнюю мелодию: " + previousTitle,
                "Восстановить ранее проверенный канал Android") {
                themedDialogBuilder()
                    .setTitle("Вернуть прежний звук: " + notificationTitle(category) + "?")
                    .setMessage("Восстановить «" + previousTitle +
                        "»? Текущая вибрация останется прежней.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Вернуть") { _, _ ->
                        val error = CategoryToneChange.restorePrevious(this, category)
                        Toast.makeText(this,
                            error ?: "Предыдущая мелодия восстановлена",
                            Toast.LENGTH_LONG).show()
                        refreshSettingsWithoutJump()
                    }.show()
            })
        }
        if (category == BalanceNotificationSounds.PAYMENT &&
            PaymentAutomaticSound.shouldOffer(this) &&
            PaymentAutomaticSound.enabled(this) &&
            PaymentToneChange.canRestorePrevious(this)) {
            val previousTitle = PaymentToneChange.previousSoundTitle(this)
            rows.add(settingsRow("music", "Вернуть прежнюю мелодию: " + previousTitle,
                "Без нового канала: восстановить проверенный звук Android") {
                themedDialogBuilder()
                    .setTitle("Вернуть подтверждённый звук платежей?")
                    .setMessage("Вернуть «" + previousTitle + "» и сохранить " +
                        "действующий режим вибрации? Автоматический звук BalanceHub " +
                        "будет выключен. Прежний проверенный канал Android сохранён.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Вернуть") { _, _ ->
                        val error = PaymentToneChange.restorePrevious(this)
                        Toast.makeText(this,
                            error ?: "Предыдущий звук платежей восстановлен",
                            Toast.LENGTH_LONG).show()
                        refreshSettingsWithoutJump()
                    }.show()
            })
        }
        rows.add(settingsRow("music", "Звук",
            BalanceNotificationSounds.title(this, category)) {
            showSoundChooser(category, notificationTitle(category))
        })
        // Android owns the volume of its system ringtone. The in-app slider
        // applies to BalanceHub's bundled WAV tones only.
        val selectedSound = BalanceNotificationSounds.selected(this, category)
        if (BalanceNotificationSounds.tones.any { it.key == selectedSound })
            rows.add(volumeRow)
        rows.add(settingsRow("settings", "Вибросигнал",
            BalanceNotificationSounds.vibrationModeTitle(this, category)) {
            showVibrationChooser(category)
        })
        rows.add(settingsRow("settings", "Проверить уведомление",
            "Проверить реальные звук и вибрацию через Android") {
            sendNotificationTest(category)
        })
        settingsGroup(settingsPanel(*rows.toTypedArray()))
    }

    private fun showPaymentDeliverySettings() {
        val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val exact = ExactReminderAlarms.allowed(this)
        val exception = power.isIgnoringBatteryOptimizations(packageName)
        val message = "Точные напоминания Android: " +
            (if (exact) "разрешены" else "нет разрешения") +
            "\nИсключение из оптимизации батареи: " +
            (if (exception) "есть" else "нет") +
            "\n\nТочный сигнал без значка будильника работает через " +
            "setExactAndAllowWhileIdle(). При Doze и ограничениях OxygenOS " +
            "Android всё же может задержать запуск. Фоновая активность и " +
            "исключение из оптимизации — разные настройки.\n\n" +
            "В параметрах батареи можно вручную разрешить работу без " +
            "ограничений. BalanceHub не меняет системные настройки сам."
        themedDialogBuilder()
            .setTitle("Надёжность доставки платежей")
            .setMessage(message)
            .setNegativeButton("Закрыть", null)
            .setNeutralButton("Настройки батареи") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setPositiveButton(if (exact) "Настройки приложения" else "Точные сигналы") {
                _, _ ->
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    ("package:" + packageName).toUri())
                val target = if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        ("package:" + packageName).toUri())
                else fallback
                try { startActivity(target) }
                catch (_: Exception) { startActivity(fallback) }
            }.show()
    }

    private fun openBatteryOptimizationSettings() {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ("package:" + packageName).toUri())
        try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        catch (_: Exception) { startActivity(fallback) }
    }

    private fun showChannelRepairDialog(category: String, testAfterRepair: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val issue = if (category == BalanceNotificationSounds.PAYMENT)
                PaymentToneChange.begin(this,
                    BalanceNotificationSounds.selected(this, category),
                    BalanceNotificationSounds.volume(this, category))
            else CategoryToneChange.begin(this, category,
                BalanceNotificationSounds.selected(this, category),
                BalanceNotificationSounds.volume(this, category))
            if (issue != null) Toast.makeText(this, issue, Toast.LENGTH_LONG).show()
            else if (category == BalanceNotificationSounds.PAYMENT)
                showPaymentSoundTrialDialog()
            else showCategorySoundTrialDialog(category)
            return
        }
        themedDialogBuilder()
            .setTitle("Применить настройки: " + notificationTitle(category) + "?")
            .setMessage("Android отдельно хранит звук и вибрацию канала. " +
                "По вашему подтверждению BalanceHub создаст новый канал с выбранными " +
                "здесь сигналами. Настройки, заданные вручную в Android, могут " +
                "сброситься только для этой категории. Платежи и события сохранятся. " +
                "Если Android отклонит новый канал, прежний останется." +
                (if (testAfterRepair) "\n\nПосле применения отправим тест уведомления."
                 else ""))
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Канал Android") { _, _ ->
                openAndroidNotificationSettings(category)
            }
            .setPositiveButton("Применить") { _, _ ->
                val error = BalanceNotificationSounds.repairChannel(this, category)
                if (error != null) {
                    themedDialogBuilder()
                        .setTitle("Android не применил настройки")
                        .setMessage(error + "\n\nПроверьте звук и вибрацию " +
                            "непосредственно в системном канале.")
                        .setNegativeButton("Закрыть", null)
                        .setPositiveButton("Настройки Android") { _, _ ->
                            openAndroidNotificationSettings(category)
                        }.show()
                } else {
                    val remaining = BalanceNotificationSounds.signalIssues(this, category)
                    if (remaining.isNotEmpty()) {
                        themedDialogBuilder()
                            .setTitle("Проверьте канал Android")
                            .setMessage(remaining.joinToString("\n") +
                                "\n\nAndroid может переопределять параметры канала. " +
                                "Проверьте звук и вибрацию в системных настройках.")
                            .setNegativeButton("Закрыть", null)
                            .setPositiveButton("Настройки Android") { _, _ ->
                                openAndroidNotificationSettings(category)
                            }.show()
                    } else if (testAfterRepair) {
                        postDiagnosticNotification(category,
                            BalanceNotificationSounds.channelId(this, category))
                    } else {
                        Toast.makeText(this,
                            "Новый канал создан. Повторная проверка состояния Android...",
                            Toast.LENGTH_LONG).show()
                    }
                    if (remaining.isEmpty()) scheduleSignalRepairVerification(category)
                }
                refreshSettingsWithoutJump()
            }.show()
    }

    /**
     * The immediate getNotificationChannel response is not proof that an OEM
     * settings UI or later delivery will keep the same sound and vibration.
     */
    private fun scheduleSignalRepairVerification(category: String) {
        pendingSignalRepair = category to BalanceNotificationSounds.channelId(this, category)
        signalRepairCheckAfter = SystemClock.elapsedRealtime() + 2400L
        Handler(Looper.getMainLooper()).postDelayed({ verifyPendingSignalRepair() }, 2700L)
    }

    private fun verifyPendingSignalRepair() {
        val pending = pendingSignalRepair ?: return
        if (SystemClock.elapsedRealtime() < signalRepairCheckAfter ||
            !window.decorView.hasWindowFocus() || isFinishing || isDestroyed) return
        pendingSignalRepair = null
        val (category, expectedId) = pending
        val currentId = BalanceNotificationSounds.channelId(this, category)
        val problems = BalanceNotificationSounds.signalIssues(this, category)
        if (currentId != expectedId || problems.isNotEmpty()) {
            val report = buildString {
                if (currentId != expectedId) {
                    append("Активный канал изменился после восстановления.\n")
                }
                append(problems.joinToString("\n"))
                append("\n\nАктивный ID: ").append(currentId)
                append("\nОжидался ID: ").append(expectedId)
                append("\nПроверьте фактические настройки в Android.")
            }
            themedDialogBuilder()
                .setTitle("Android не сохранил выбранные сигналы")
                .setMessage(report)
                .setNegativeButton("Закрыть", null)
                .setPositiveButton("Открыть канал Android") { _, _ ->
                    openAndroidNotificationSettings(category)
                }.show()
        } else {
            Toast.makeText(this,
                "Android подтвердил параметры канала повторно. " +
                "Реальные звук и вибрацию нужно проверить на телефоне.",
                Toast.LENGTH_LONG).show()
        }
        if (currentTab == 4) refreshSettingsWithoutJump()
    }

    private fun openAndroidProbeSettings(
        mode: AndroidSignalProbe.Mode = AndroidSignalProbe.Mode.SYSTEM
    ) {
        val id = AndroidSignalProbe.channelId(this, mode) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openAndroidNotificationSettings()
            return
        }
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, id)
        }
        try { startActivity(intent) }
        catch (_: Exception) { openAndroidNotificationSettings() }
    }

    private fun showAndroidSignalProbeState(
        mode: AndroidSignalProbe.Mode = AndroidSignalProbe.Mode.SYSTEM
    ) {
        val id = AndroidSignalProbe.channelId(this, mode)
        themedDialogBuilder()
            .setTitle("Диагностика: " + mode.label)
            .setMessage(AndroidSignalProbe.report(this, mode) +
                "\n\nСравните переключатели в настройках именно этого канала.")
            .setNegativeButton("Закрыть", null)
            .setPositiveButton("Настройки Android") { _, _ ->
                if (id != null) openAndroidProbeSettings(mode)
                else openAndroidNotificationSettings()
            }.show()
    }

    private fun runAndroidSignalProbe(
        mode: AndroidSignalProbe.Mode = AndroidSignalProbe.Mode.SYSTEM
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded()
            return
        }
        val result = AndroidSignalProbe.send(this, mode)
        themedDialogBuilder()
            .setTitle("Проверка: " + mode.label)
            .setMessage(result.report)
            .setNegativeButton("Закрыть", null)
            .setPositiveButton(if (result.id == null) "Настройки уведомлений"
                else "Настройки тестового канала") { _, _ ->
                if (result.id == null) openAndroidNotificationSettings()
                else openAndroidProbeSettings(mode)
            }.show()
        refreshSettingsWithoutJump()
    }

    private fun showVibrationChooser(category: String) {
        val options = NotificationVibrationMode.options
        val labels = options.map { NotificationVibrationMode.title(it) }
        val selected = options.indexOf(BalanceNotificationSounds.vibrationMode(this, category))
        showThemedChoices("Вибросигнал", labels, selected) { index ->
            val mode = options[index]
            val error = BalanceNotificationSounds.applyVibrationModeChoice(
                this, category, mode)
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            } else if (mode != NotificationVibrationMode.OFF) {
                BalanceVibration.preview(this, category)
            }
            refreshSettingsWithoutJump()
        }
    }

    private fun renderUpdateSettings() {
        settingsHeader("Обновления", "Версия и проверка новых сборок")
        val installed = packageManager.getPackageInfo(packageName, 0)
        val found = GithubUpdateEngine.cached(this)
        settingsGroup(settingsPanel(
            settingsRow("download", "Установленная версия",
                installed.versionName ?: "неизвестна"),
            settingsSwitch("settings", "Уведомлять о версиях",
                "Автоматическая проверка примерно раз в 6 часов",
                GithubUpdateEngine.enabled(this)) {
                GithubUpdateEngine.setEnabled(this, it)
                if (it) requestNotificationPermissionIfNeeded()
            },
            settingsRow("download", "Проверить обновления",
                found?.let { "Доступна " + it.tag } ?: "Проверить сейчас") {
                checkGithubUpdate(manual = true, offer = true)
            },
            settingsRow("clock", "История изменений",
                "Изменения официальных версий приложения") {
                openSettingsPage("history")
            }
        ))
        if (found != null) settingsGroup(settingsPanel(
            settingsRow("download", "Скачать новую версию", found.tag) {
                showGithubUpdateDialog(found)
            }
        ))
    }
    private fun renderReleaseHistorySettings() {
        settingsHeader("История изменений", "Только стабильные версии приложения")
        val local = PublishedReleaseHistory.offline(this)
        if (!releaseHistoryRefreshStarted) {
            releaseHistoryRefreshStarted = true
            PublishedReleaseHistory.fetchAsync(this) { published ->
                // A stable release can appear in the feed after this APK was installed.
                releaseHistoryVersions = PublishedReleaseHistory.ordered(published + local)
                if (!isFinishing && !isDestroyed && currentTab == 4 &&
                    settingsScreen() == "history") refreshSettingsWithoutJump()
            }
        }
        val versions = if (releaseHistoryVersions.isNotEmpty()) releaseHistoryVersions
            else local
        if (versions.isEmpty()) {
            contentContainer.addView(textLine("История пока недоступна.", 13f, muted))
            return
        }
        for (version in versions) {
            val isExpanded = expandedReleaseHistoryVersion == version
            val panel = settingsPanel(
                settingsRow("clock", "Версия " + version,
                    if (isExpanded) "Свернуть изменения" else "Показать изменения") {
                    expandedReleaseHistoryVersion = if (isExpanded) null else version
                    refreshSettingsWithoutJump()
                })
            if (isExpanded) {
                panel.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(1), dp(16), dp(14))
                    for (note in AppReleaseNotes.forStableRelease(version)) {
                        addView(textLine("• " + note.title, 13f, ink))
                        addView(verticalGap(6))
                    }
                })
            }
            settingsGroup(panel)
        }
    }

    private fun renderHelpSettings() {
        settingsHeader("Помощь и диагностика", "Инструкции и системные проверки")
        settingsGroup(settingsPanel(
            settingsRow("settings", "Справка",
                "Балансы, платежи, банки и обновления") { showHelpCenter() },
         ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ExactReminderAlarms.allowed(this)) {
            settingsGroup(settingsPanel(settingsRow("calendar-days",
                "Разрешить точные напоминания",
                "Android может задерживать сигналы без разрешения") {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        ("package:" + packageName).toUri()))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        ("package:" + packageName).toUri()))
                }
            }))
        }
    }
    private fun renderAboutSettings() {
        settingsHeader("О приложении")
        val info = packageManager.getPackageInfo(packageName, 0)
        settingsGroup(settingsPanel(
            settingsRow("settings", "BalanceHub",
                "Балансы, платежи и события"),
            settingsRow("download", "Версия",
                (info.versionName ?: "неизвестна") + " · сборка " +
                    PackageInfoCompat.getLongVersionCode(info))
        ))
    }

    private fun closeEngineeringMenu() {
        engineeringMenuOpen = false
        engineeringTapCount = 0
        engineeringTapStartedAt = 0L
        bankSettingsOpen = false
        engineeringChannelsOpen = false
        engineeringChannelCategory = null
        engineeringBanksOpen = false
        engineeringNotificationsOpen = false
        engineeringDeliveryOpen = false
        engineeringWinterOpen = false
        engineeringOtherBankId = null
    }

    private fun renderEngineeringMenu() {
        contentContainer.addView(secondaryButton("‹  Назад в настройки") {
            closeEngineeringMenu()
            renderTab()
        })
        contentContainer.addView(verticalGap(16))
        contentContainer.addView(textLine("Инженерное меню", 23f, ink, true))
        contentContainer.addView(verticalGap(5))
        contentContainer.addView(textLine(
            "Технические инструменты BalanceHub", 13f, muted))
        contentContainer.addView(verticalGap(16))

        fun section(title: String, description: String, action: () -> Unit) {
            val item = card().apply {
                isClickable = true
                isFocusable = true
                addView(textLine(title + "   ›", 17f, ink, true))
                addView(verticalGap(5))
                addView(textLine(description, 13f, muted))
                setOnClickListener {
                    action()
                    renderTab()
                }
            }
            contentContainer.addView(item)
            contentContainer.addView(verticalGap(12))
        }
        section("Диагностика банков", "Сбербанк, ВТБ и Т-Банк: Push и распознавание") {
            engineeringBanksOpen = true
        }
        section("Диагностика уведомлений", "Звук, вибромотор и проверочные каналы") {
            engineeringNotificationsOpen = true
        }
        section("Системные каналы", "Состояние каналов и разрешения Android") {
            engineeringChannelsOpen = true
        }
        section("Проверка доставки", "Отложенные тесты, журналы и питание") {
            engineeringDeliveryOpen = true
        }
        section("Новогодняя тема · тест", "Автоматически по датам или принудительно") {
            engineeringWinterOpen = true
        }
        contentContainer.addView(textLine("Техническая помощь", 16f, ink, true))
        val adb = card()
        adb.addView(textLine("Техническая помощь", 17f, ink, true))
        adb.addView(verticalGap(6))
        adb.addView(textLine("Настройка банковских Push через Termux / ADB",
            13f, muted))
        adb.addView(verticalGap(9))
        adb.addView(secondaryButton("Открыть руководство") {
            showBankPushHelp()
        })
        contentContainer.addView(adb)

        contentContainer.addView(verticalGap(10))
        contentContainer.addView(textLine(
            "При выходе из инженерного меню доступ закрывается.", 12f, muted))
    }

    private fun renderEngineeringWinter() {
        contentContainer.addView(secondaryButton("‹  Инженерное меню") {
            engineeringWinterOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(14))
        contentContainer.addView(textLine("Новогодняя тема · тест", 23f, ink, true))
        contentContainer.addView(verticalGap(6))
        contentContainer.addView(textLine(
            "Автоматически: с 20 декабря по 10 января включительно. " +
                "Для проверки можно включить или выключить оформление независимо от даты.",
            13f, muted))
        contentContainer.addView(verticalGap(14))
        val current = WinterThemeState.mode(this)
        val modes = listOf(
            Triple(WinterThemePolicy.AUTO, "Автоматически по датам",
                "Включается 20.12, отключается 11.01"),
            Triple(WinterThemePolicy.FORCE_ON, "Включить принудительно",
                "Новогодняя тема видна даже вне праздников"),
            Triple(WinterThemePolicy.FORCE_OFF, "Отключить принудительно",
                "Всегда показывать обычное оформление")
        )
        settingsGroup(settingsPanel(*modes.map { (value, title, description) ->
            settingsRow("calendar-days", title,
                description + if (current == value) " · выбрано ✓" else "") {
                if (current != value) {
                    WinterThemeState.setMode(this, value)
                    showDashboard()
                }
            }
        }.toTypedArray()))
        contentContainer.addView(textLine(
            if (winterActive) "Сейчас: новогоднее оформление включено"
            else "Сейчас: обычное оформление",
            13f, accent, true))
    }

    private fun renderEngineeringBanks() {
        contentContainer.addView(secondaryButton("‹  Инженерное меню") {
            engineeringBanksOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(14))
        contentContainer.addView(textLine("Диагностика банков", 23f, ink, true))
        contentContainer.addView(textLine(
            "Технические сведения о банковских Push. Пользовательские настройки — в разделе «Банки».",
            12f, muted))
        contentContainer.addView(verticalGap(14))
        for (bank in RussianBankCatalog.banks) {
            val status = if (BankCardPreferences.enabled(this, bank.id))
                "Получение Push включено" else "Получение Push отключено"
            val item = card().apply {
                isClickable = true
                isFocusable = true
                addView(textLine(
                    if (bank.id == "tbank") "Т-Банк (Тинькофф)   ›"
                    else bank.name + "   ›", 17f, ink, true))
                addView(verticalGap(5))
                addView(textLine(status, 13f, muted))
                setOnClickListener {
                    if (bank.id == "sber") bankSettingsOpen = true
                    else engineeringOtherBankId = bank.id
                    renderTab()
                }
            }
            contentContainer.addView(item)
            contentContainer.addView(verticalGap(12))
        }
    }

    private fun renderEngineeringNotifications() {
        contentContainer.addView(secondaryButton("‹  Инженерное меню") {
            engineeringNotificationsOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(14))
        contentContainer.addView(textLine("Диагностика уведомлений", 23f, ink, true))
        contentContainer.addView(textLine(
            "Проверки звука, вибрации и системных каналов Android", 12f, muted))
        contentContainer.addView(verticalGap(14))
        val notifications = card()
        notifications.addView(textLine("Диагностика уведомлений", 17f, ink, true))
        notifications.addView(verticalGap(7))
        notifications.addView(textLine(
            "Тесты звука, вибромотора и настоящих системных уведомлений", 13f, muted))
        notifications.addView(verticalGap(11))
        notifications.addView(primaryButton("Проверить звук") {
            selectDiagnosticCategory { category ->
                Toast.makeText(this,
                    BalanceNotificationSounds.previewChannel(this, category),
                    Toast.LENGTH_LONG).show()
            }
        })
        notifications.addView(verticalGap(9))
        notifications.addView(secondaryButton("Проверить вибромотор") {
            selectDiagnosticCategory { category -> sendVibrationTest(category) }
        })
        notifications.addView(verticalGap(9))
        for (mode in listOf(NotificationVibrationMode.SHORT,
            NotificationVibrationMode.LONG, NotificationVibrationMode.DOUBLE)) {
            notifications.addView(secondaryButton(
                "Прямой вибротест: " + NotificationVibrationMode.title(mode)) {
                val started = BalanceVibration.directProbe(this, mode)
                Toast.makeText(this,
                    if (started) "Команда вибромотору отправлена: " +
                        NotificationVibrationMode.title(mode) +
                        ". Проверьте, совпадает ли физический отклик."
                    else "Не удалось запустить вибромотор. Проверьте настройки Android.",
                    Toast.LENGTH_LONG).show()
            })
            notifications.addView(verticalGap(9))
        }
        notifications.addView(secondaryButton("Проверить уведомление") {
            selectDiagnosticCategory { category -> sendNotificationTest(category) }
        })
        notifications.addView(verticalGap(9))
        notifications.addView(secondaryButton("Состояние звука и вибрации Android") {
            selectDiagnosticCategory { category ->
                themedDialogBuilder()
                    .setTitle("Диагностика: " + notificationTitle(category))
                    .setMessage(NotificationSignalAudit.report(this, category)
                        .joinToString("\n"))
                    .setNegativeButton("Закрыть", null)
                    .setPositiveButton("Настройки канала") { _, _ ->
                        openAndroidNotificationSettings(category)
                    }.show()
            }
        })
        notifications.addView(verticalGap(9))
        for (mode in listOf(AndroidSignalProbe.Mode.SYSTEM,
            AndroidSignalProbe.Mode.SHORT, AndroidSignalProbe.Mode.LONG,
            AndroidSignalProbe.Mode.DOUBLE, AndroidSignalProbe.Mode.TONE)) {
            notifications.addView(secondaryButton("Тест: " + mode.label) {
                runAndroidSignalProbe(mode)
            })
            notifications.addView(verticalGap(9))
            notifications.addView(secondaryButton("Диагностика: " + mode.label) {
                showAndroidSignalProbeState(mode)
            })
            notifications.addView(verticalGap(9))
        }

        if (AndroidSignalProbe.channelId(this, AndroidSignalProbe.Mode.PATTERN) != null) {
            notifications.addView(secondaryButton("Результат старого теста rc6") {
                showAndroidSignalProbeState(AndroidSignalProbe.Mode.PATTERN)
            })
            notifications.addView(verticalGap(9))
        }
        notifications.addView(secondaryButton("Диагностика прямой вибрации ColorOS") {
            themedDialogBuilder()
                .setTitle("Прямые уведомления ColorOS")
                .setMessage(listOf(BalanceNotificationSounds.PAYMENT,
                    BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)
                    .joinToString("\n\n") { category ->
                        notificationTitle(category) + ": " +
                            ColorOsDirectVibration.lastResult(this, category)
                    })
                .setPositiveButton("Закрыть", null).show()
        })
        notifications.addView(verticalGap(9))
        notifications.addView(secondaryButton("Диагностика автоматического звука") {
            selectDiagnosticCategory { category ->
                themedDialogBuilder()
                    .setTitle("Автоматический звук: " + notificationTitle(category))
                    .setMessage("Последний результат: " +
                        PaymentAutomaticSound.lastResult(this, category))
                    .setPositiveButton("Закрыть", null).show()
            }
        })
        contentContainer.addView(notifications)
    }

    private fun renderEngineeringDelivery() {
        contentContainer.addView(secondaryButton("‹  Инженерное меню") {
            engineeringDeliveryOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(14))
        contentContainer.addView(textLine("Проверка доставки", 23f, ink, true))
        contentContainer.addView(textLine(
            "Отложенные сигналы, условия питания и время доставки", 12f, muted))
        contentContainer.addView(verticalGap(14))
        val delivery = card()
        delivery.addView(secondaryButton("Доставка платежей") {
            showPaymentDeliverySettings()
        })
        delivery.addView(verticalGap(9))
        delivery.addView(secondaryButton("Журнал доставки платежей") {
            themedDialogBuilder().setTitle("Доставка платежей")
                .setMessage(PaymentDeliveryLog.report(this).joinToString("\n"))
                .setPositiveButton("Закрыть", null).show()
        })
        delivery.addView(verticalGap(9))
        delivery.addView(secondaryButton("Тест платежа через 2 минуты") {
            scheduleDiagnosticReminder(BalanceNotificationSounds.PAYMENT)
        })
        delivery.addView(verticalGap(8))
        delivery.addView(secondaryButton("Тест события через 2 минуты") {
            scheduleDiagnosticReminder(BalanceNotificationSounds.EVENT)
        })
        val log = getSharedPreferences("balancehub_reminder_diagnostics", MODE_PRIVATE)
        delivery.addView(verticalGap(8))
        delivery.addView(secondaryButton("Настройки батареи Android") {
            openBatteryOptimizationSettings()
        })
        delivery.addView(verticalGap(9))
        for ((key, title) in listOf("payment" to "Платёж", "event" to "Событие")) {
            val planned = log.getLong("${key}_planned", 0L)
            val delivered = log.getLong("${key}_delivered", 0L)
            val posted = log.getLong("${key}_posted", 0L)
            if (planned > 0L) {
                val delta = if (delivered >= planned) {
                    " · запуск +" + ((delivered - planned) / 1000L) + " сек"
                } else " · ожидается"
                delivery.addView(textLine("$title: " +
                    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(planned)) +
                    delta, 12f, muted))
                val method = when (log.getString("${key}_mode", "")) {
                    "exact_idle" -> "точный сигнал без значка будильника"
                    "inexact_idle" -> "резервный неточный"
                    "expired" -> "время уже прошло"
                    else -> "неизвестен"
                }
                delivery.addView(textLine(
                    "Метод: $method · разрешение точных: " +
                        (if (log.getBoolean("${key}_exact_allowed", false))
                            "да" else "нет") +
                        " · без ограничений батареи: " +
                        (if (log.getBoolean("${key}_battery_unrestricted", false))
                            "да" else "нет"),
                    12f, muted))
                if (posted >= delivered && delivered > 0L)
                    delivery.addView(textLine("Отправка в Android спустя " +
                        ((posted - delivered) / 1000L) + " сек после запуска",
                        12f, muted))
                if (key == "payment" && delivered >= planned && delivered > 0L) {
                    delivery.addView(textLine(
                        "При запуске: экран " +
                            (if (log.getBoolean("payment_fired_interactive", true))
                                "включён" else "выключен") +
                            " · Doze: " +
                            (if (log.getBoolean("payment_fired_idle", false)) "да" else "нет") +
                            " · экономия энергии: " +
                            (if (log.getBoolean("payment_fired_power_saver", false))
                                "да" else "нет"), 12f, muted))
                }
            }
        }

        contentContainer.addView(delivery)
    }

    private fun renderOtherBankEngineering() {
        val bank = RussianBankCatalog.byId(engineeringOtherBankId.orEmpty())
        if (bank == null || bank.id == "sber") {
            engineeringOtherBankId = null
            renderEngineeringBanks()
            return
        }
        contentContainer.addView(secondaryButton("‹  Диагностика банков") {
            engineeringOtherBankId = null
            renderTab()
        })
        contentContainer.addView(verticalGap(14))
        contentContainer.addView(textLine(
            if (bank.id == "tbank") "Т-Банк (Тинькофф)" else bank.name,
            23f, ink, true))
        contentContainer.addView(verticalGap(8))

        val enabled = BankCardPreferences.enabled(this, bank.id)
        val granted = SberBalanceStore.isAccessGranted(this)
        val installed = RussianBankCatalog.installedPackage(this, bank)
        val summary = card()
        summary.addView(textLine(
            if (enabled) "Получение Push включено" else "Получение Push отключено",
            16f, ink, true))
        summary.addView(verticalGap(8))
        summary.addView(textLine(
            "Доступ к уведомлениям: " +
                (if (granted) "предоставлен" else "не предоставлен"), 13f, muted))
        summary.addView(verticalGap(6))
        summary.addView(textLine(
            "Приложение-источник: " +
                (if (installed != null) "обнаружено" else "не обнаружено"),
            13f, muted))
        summary.addView(textLine(
            "Ожидаемый пакет: " + bank.packages.joinToString(), 12f, muted))
        summary.addView(verticalGap(8))
        summary.addView(secondaryButton("Настроить доступ Android") {
            openSberListenerSettings()
        })
        contentContainer.addView(summary)
        contentContainer.addView(verticalGap(12))

        val events = if (enabled) RussianBankDiagnostics.recent(this, bank)
            else emptyList()
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val diagnostics = card()
        diagnostics.addView(textLine("Диагностика Push", 16f, ink, true))
        diagnostics.addView(verticalGap(8))
        val lastEvent = events.firstOrNull()
        if (lastEvent == null) {
            diagnostics.addView(textLine(
                if (enabled) "Диагностических событий пока нет"
                else "Получение Push отключено", 13f, muted))
        } else {
            diagnostics.addView(textLine(
                "Последний Push: " + formatter.format(Date(lastEvent.receivedAt)),
                13f, ink))
            diagnostics.addView(verticalGap(6))
            diagnostics.addView(textLine(
                "Распознавание: " + lastEvent.status, 13f, muted))
            val fields = SberNotificationText.Field.entries.filter {
                (lastEvent.fieldsMask and it.mask) != 0
            }
            diagnostics.addView(verticalGap(6))
            diagnostics.addView(textLine(
                "Доступные поля: " +
                    if (fields.isEmpty()) "нет" else fields.joinToString(", ") { it.label },
                12f, muted))
            for (field in fields) {
                val size = lastEvent.fieldLengths.getOrNull(field.ordinal) ?: 0
                val money = (lastEvent.rubleFieldsMask and field.mask) != 0
                val cue = (lastEvent.cueFieldsMask and field.mask) != 0
                val account = (lastEvent.accountFieldsMask and field.mask) != 0
                diagnostics.addView(textLine(
                    field.label + ": длина " + size + " · рубли " +
                        (if (money) "да" else "нет") +
                        " · остаток " + (if (cue) "да" else "нет") +
                        " · маска счёта " + (if (account) "да" else "нет"),
                    11f, muted))
            }
            diagnostics.addView(verticalGap(7))
            val environment = when {
                (lastEvent.deliveryState and
                    SberPushNotificationListener.STATE_SCREEN_OFF) != 0 ->
                    "экран выключен"
                (lastEvent.deliveryState and
                    SberPushNotificationListener.STATE_LOCKED) != 0 ->
                    "экран заблокирован"
                lastEvent.deliveryState < 0 -> "не определено"
                else -> "экран активен"
            }
            diagnostics.addView(textLine("При получении: " + environment, 12f, muted))
            diagnostics.addView(textLine(
                "Скрыто системой: " +
                    if (lastEvent.privacy == "SYSTEM_REDACTION") "да" else "нет",
                12f, muted))
            val success = RussianBankDiagnostics.lastSuccess(this, bank)
            diagnostics.addView(textLine(
                success?.let { "Последнее обновление: " + formatter.format(Date(it)) }
                    ?: "Успешного обновления ещё не было", 12f, muted))
            if (events.size > 1) {
                diagnostics.addView(verticalGap(10))
                diagnostics.addView(textLine("Последние события", 14f, ink, true))
                events.take(6).forEach { event ->
                    diagnostics.addView(textLine(
                        formatter.format(Date(event.receivedAt)) + " — " + event.status,
                        12f, muted))
                }
            }
        }
        diagnostics.addView(verticalGap(9))
        diagnostics.addView(textLine(
            "Хранятся только времена, признаки полей и результаты шести последних " +
                "событий. Тексты Push, суммы и номера счетов не сохраняются.",
            12f, muted))
        diagnostics.addView(verticalGap(10))
        diagnostics.addView(secondaryButton("Проверить обработчик на примере") {
            val positive = RussianBankPushParser.analyze(bank.packages.first(),
                listOf("Остаток 40,89 ₽, карта ••9667"))
            val negative = RussianBankPushParser.analyze(bank.packages.first(),
                listOf("Перевод 10 ₽"))
            val accepted = positive.result
            val success = accepted?.kopecks == 4089L &&
                accepted.lastFour == "9667" && negative.result == null
            themedDialogBuilder().setTitle("Локальный тест распознавания")
                .setMessage(if (success)
                    "Тест пройден: остаток распознан, сумма перевода отвергнута. " +
                        "Банковский баланс не изменён."
                else "Тест не пройден. Причины: " + positive.reason + ", " +
                    negative.reason + ". Банковский баланс не изменён.")
                .setPositiveButton("Закрыть", null).show()
        })
        contentContainer.addView(diagnostics)
    }

    /** Advanced channel controls stay behind the non-persistent engineering menu. */
    private fun renderEngineeringChannelCategory() {
        val category = engineeringChannelCategory?.takeIf {
            it == BalanceNotificationSounds.PAYMENT ||
                it == BalanceNotificationSounds.EVENT ||
                it == BalanceNotificationSounds.UPDATE
        }
        if (!engineeringMenuOpen || !engineeringChannelsOpen || category == null) {
            engineeringChannelCategory = null
            renderEngineeringChannels()
            return
        }
        contentContainer.addView(secondaryButton("‹  Системные каналы") {
            engineeringChannelCategory = null
            renderTab()
        })
        contentContainer.addView(verticalGap(13))
        contentContainer.addView(textLine(notificationTitle(category), 23f, ink, true))
        contentContainer.addView(verticalGap(5))
        contentContainer.addView(textLine(
            "Инженерные параметры канала уведомлений Android", 12f, muted))
        contentContainer.addView(verticalGap(14))

        val rows = mutableListOf<View>()
        if (PaymentAutomaticSound.shouldOffer(this)) {
            val automatic = PaymentAutomaticSound.enabled(this, category)
            rows.add(settingsRow("music",
                if (automatic) "Вернуться к системному звуку"
                else "Испытать автоматический звук BalanceHub",
                if (automatic) "Проверить новый канал перед возвратом"
                else "ColorOS: проверка автоматического воспроизведения WAV") {
                val key = BalanceNotificationSounds.selected(this, category)
                val gain = BalanceNotificationSounds.volume(this, category)
                val issue = if (category == BalanceNotificationSounds.PAYMENT)
                    PaymentToneChange.begin(this, key, gain, automatic = !automatic)
                else CategoryToneChange.begin(this, category, key, gain,
                    automatic = !automatic)
                if (issue != null) Toast.makeText(this, issue, Toast.LENGTH_LONG).show()
                else if (category == BalanceNotificationSounds.PAYMENT)
                    showPaymentSoundTrialDialog()
                else showCategorySoundTrialDialog(category)
                refreshSettingsWithoutJump()
            })
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rows.add(settingsRow("settings", "Канал уведомлений Android",
                BalanceNotificationSounds.effectiveSummary(this, category)) {
                openAndroidNotificationSettings(category)
            })
            val needsRepair = BalanceNotificationSounds.needsRepair(this, category)
            rows.add(settingsRow("settings",
                if (needsRepair) "Применить звук и вибрацию"
                else "Повторно применить сигналы",
                if (needsRepair) "Выбранные настройки отличаются от канала Android"
                else "Только по подтверждению: новый канал, настройки Android могут сброситься") {
                showChannelRepairDialog(category)
            })
        }
        rows.add(settingsRow("settings", "Состояние звука и вибрации Android",
            "Технический отчёт о текущем канале") {
            themedDialogBuilder()
                .setTitle("Канал: " + notificationTitle(category))
                .setMessage(NotificationSignalAudit.report(this, category)
                    .joinToString("\n"))
                .setNegativeButton("Закрыть", null)
                .setPositiveButton("Настройки Android") { _, _ ->
                    openAndroidNotificationSettings(category)
                }.show()
        })
        settingsGroup(settingsPanel(*rows.toTypedArray()))
        contentContainer.addView(textLine(
            "Применение сигналов может пересоздать канал Android. " +
                "Данные платежей, событий и выбранные звуки сохраняются.",
            12f, muted))
    }

    private fun renderEngineeringChannels() {
        contentContainer.addView(secondaryButton("‹  Инженерное меню") {
            engineeringChannelsOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(13))
        contentContainer.addView(textLine("Системные каналы", 23f, ink, true))
        contentContainer.addView(verticalGap(4))
        contentContainer.addView(textLine(
            "Действующие параметры Android без сброса пользовательского выбора",
            12f, muted))
        contentContainer.addView(verticalGap(14))
        settingsGroup(settingsPanel(*listOf(
            BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT,
            BalanceNotificationSounds.UPDATE
        ).map { category ->
            val status = BalanceNotificationSounds.effectiveSummary(this, category)
            settingsRow("settings", notificationTitle(category), status) {
                engineeringChannelCategory = category
                renderTab()
            }
        }.toTypedArray()))
        settingsGroup(settingsPanel(settingsRow("settings",
            "Восстановить каналы",
            "Повторно применить сохранённые звуки и вибрацию") {
            themedDialogBuilder()
                .setTitle("Восстановить уведомления?")
                .setMessage("Будут пересозданы только каналы BalanceHub. " +
                    "Их настройки Android могут измениться. " +
                    "Текущие настройки приложения останутся без изменений.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Восстановить") { _, _ ->
                    val errors = listOf(
                        BalanceNotificationSounds.PAYMENT,
                        BalanceNotificationSounds.EVENT,
                        BalanceNotificationSounds.UPDATE
                    ).mapNotNull { category ->
                        BalanceNotificationSounds.repairChannel(this, category)?.let {
                            notificationTitle(category) + ": " + it
                        }
                    }
                    Toast.makeText(this,
                        if (errors.isEmpty()) "Настройки каналов применены"
                        else errors.joinToString("; "), Toast.LENGTH_LONG).show()
                    renderTab()
                }.show()
        }))
        settingsGroup(settingsPanel(
            settingsRow("settings", "Разрешения Android",
                "Общие уведомления BalanceHub") {
                openAndroidNotificationSettings()
            }
        ))
        val unused = BalanceNotificationSounds.unusedChannelIds(this).size
        if (unused > 0) {
            settingsGroup(settingsPanel(settingsRow("archive",
                "Неиспользуемые каналы: $unused",
                "Очистить старые варианты мелодий и громкости") {
                themedDialogBuilder()
                    .setTitle("Очистить старые каналы?")
                    .setMessage("Будут удалены только $unused неактивных каналов " +
                        "BalanceHub. Их индивидуальные системные настройки могут быть " +
                        "утрачены. Действующие каналы, звук и вибрация останутся.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Очистить") { _, _ ->
                        val removed = BalanceNotificationSounds.deleteUnusedChannels(this)
                        Toast.makeText(this, "Удалено старых каналов: $removed",
                            Toast.LENGTH_LONG).show()
                        renderTab()
                    }.show()
            }))
        }
        contentContainer.addView(textLine(
            "Все каналы создаются включёнными. Если канал выключен вручную " +
                "в Android, BalanceHub не изменяет его без вашего разрешения.",
            12f, muted))
    }


    /**
     * Payment-only sound change. Unlike the old picker, selecting a tone does
     * not immediately replace the confirmed Android channel.
     */
    private fun showPaymentSoundTrialDialog() {
        val trial = PaymentToneChange.pending(this) ?: return
        val label = when (trial.key) {
            "system" -> "Системный звук"
            "silent" -> "Без звука"
            else -> BalanceNotificationSounds.tones.firstOrNull { it.key == trial.key }
                ?.title ?: trial.key
        }
        val problem = PaymentToneChange.problem(this, trial)
        val message = if (trial.automatic) {
            "Новая мелодия: " + label + "\n\nПРОБНЫЙ автоматический звук BalanceHub. " +
                "Он воспроизводится отдельной кратковременной службой. " +
                "Действующий канал платежей пока НЕ изменён. " +
                "На Android 17 после подтверждения обязательно проверьте " +
                "настоящее напоминание при выключенном экране." +
                (if (problem != null) "\n\nAndroid: " + problem else "") +
                "\n\nПодтверждайте только после фактического звука и вибрации."
        } else {
            "Новая мелодия: " + label +
                "\n\nДействующий канал платежей пока НЕ изменён. " +
                "Проверьте пробное уведомление на слух. " +
                "При необходимости выберите файл BalanceHub_" + trial.key +
                "_" + trial.gain + ".wav в настройках Android именно пробного канала." +
                (if (problem != null) "\n\nAndroid: " + problem else "") +
                "\n\nПосле правильного звука подтвердите результат. Вибрация останется прежней."
        }
        themedDialogBuilder()
            .setTitle("Проверка мелодии платежей")
            .setMessage(message)
            .setNegativeButton("Отмена") { _, _ ->
                PaymentToneChange.cancel(this)
                refreshSettingsWithoutJump()
            }
            .setNeutralButton("Настройки Android") { _, _ ->
                openPaymentTrialAndroidSettings()
            }
            .setPositiveButton("Тест") { _, _ ->
                sendPaymentSoundTrial()
            }.show()
    }

    private fun openPaymentTrialAndroidSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openAndroidNotificationSettings(BalanceNotificationSounds.PAYMENT)
            return
        }
        val trial = PaymentToneChange.pending(this) ?: return
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, trial.id)
        }
        try { startActivity(intent) }
        catch (_: Exception) { openAndroidNotificationSettings(BalanceNotificationSounds.PAYMENT) }
    }

    private fun sendPaymentSoundTrial() {
        val trial = PaymentToneChange.pending(this) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded()
            return
        }
        if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(this, "Разрешите уведомления BalanceHub в Android",
                Toast.LENGTH_LONG).show()
            return
        }
        try {
            val builder = androidx.core.app.NotificationCompat.Builder(this, trial.id)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle("Проверка новой мелодии платежей")
                .setContentText("Слушайте выбранную мелодию и проверьте вибрацию")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(ReminderNoticeIds.test(), builder.build())
            ColorOsDirectVibration.afterNotification(
                this, BalanceNotificationSounds.PAYMENT, trial.id)
            if (trial.automatic) {
                val result = PaymentAutomaticSound.afterNotification(this, trial.id,
                    trial.key, trial.gain, trial = true)
                val silent = trial.key == "silent" || (trial.key != "system" && trial.gain == 0)
                if (result != PaymentAutomaticSound.Result.SERVICE_REQUESTED &&
                    !(silent && result == PaymentAutomaticSound.Result.SILENT_SELECTION)) {
                    Toast.makeText(this, "Автоматический звук не запущен: " + result,
                        Toast.LENGTH_LONG).show()
                    return
                }
            }
            if (!PaymentToneChange.markTested(this, trial.id)) {
                Toast.makeText(this, "Не удалось записать результат теста",
                    Toast.LENGTH_LONG).show()
                return
            }
        } catch (error: Exception) {
            Toast.makeText(this, "Не удалось отправить тест: " +
                error.javaClass.simpleName, Toast.LENGTH_LONG).show()
            return
        }
        themedDialogBuilder()
            .setTitle("Новая мелодия воспроизвелась?")
            .setMessage(if (trial.automatic) {
                "Прозвучала ли нужная мелодия BalanceHub с правильной вибрацией? " +
                    "Ответ Android не доказывает физическое воспроизведение.\n\n" +
                    "Если звук отсутствует, не подтверждайте и оставьте старый канал."
            } else {
                "Вы действительно услышали нужный звук на OnePlus? " +
                    "Проверка API сама по себе не доказывает воспроизведение.\n\n" +
                    "Если звука нет или он другой, оставьте прежний канал и " +
                    "выберите нужную мелодию в настройках пробного канала Android."
            })
            .setNegativeButton("Нет") { _, _ ->
                PaymentToneChange.cancel(this)
                refreshSettingsWithoutJump()
            }
            .setNeutralButton("Канал Android") { _, _ ->
                openPaymentTrialAndroidSettings()
            }
            .setPositiveButton("Да, правильно") { _, _ ->
                val issue = PaymentToneChange.accept(this)
                if (issue != null) {
                    themedDialogBuilder()
                        .setTitle("Новая мелодия не применена")
                        .setMessage(issue + "\n\nРабочий канал платежей сохранён.")
                        .setNegativeButton("Закрыть", null)
                        .setPositiveButton("Канал Android") { _, _ ->
                            openPaymentTrialAndroidSettings()
                        }.show()
                } else {
                    Toast.makeText(this,
                        "Новая мелодия платежей подтверждена и сохранена",
                        Toast.LENGTH_LONG).show()
                }
                refreshSettingsWithoutJump()
            }.show()
    }

    /**
     * The event/update trial follows the proven payment flow without changing
     * the currently confirmed channel or the corresponding payment settings.
     */
    private fun showCategorySoundTrialDialog(category: String) {
        val trial = CategoryToneChange.pending(this, category) ?: return
        val label = when (trial.key) {
            "system" -> "Системный звук"
            "silent" -> "Без звука"
            else -> BalanceNotificationSounds.tones.firstOrNull { it.key == trial.key }
                ?.title ?: trial.key
        }
        val problem = CategoryToneChange.problem(this, category, trial)
        val description = if (trial.automatic)
            "Пробный автоматический звук BalanceHub через кратковременную службу. " +
                "Действующий канал пока НЕ изменён. После подтверждения " +
                "проверьте настоящее уведомление с выключенным экраном."
        else
            "Действующий канал пока НЕ изменён. Проверьте звук в Android. " +
                "При необходимости выберите WAV в настройках пробного канала."
        themedDialogBuilder()
            .setTitle("Проверка мелодии: " + notificationTitle(category))
            .setMessage("Новая мелодия: " + label + "\n\n" + description +
                (if (problem != null) "\n\nAndroid: " + problem else "") +
                "\n\nПодтверждайте только после фактической проверки звука и вибрации.")
            .setNegativeButton("Отмена") { _, _ ->
                CategoryToneChange.cancel(this, category)
                refreshSettingsWithoutJump()
            }
            .setNeutralButton("Настройки Android") { _, _ ->
                openCategoryTrialAndroidSettings(category)
            }
            .setPositiveButton("Тест") { _, _ ->
                sendCategorySoundTrial(category)
            }.show()
    }

    private fun openCategoryTrialAndroidSettings(category: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openAndroidNotificationSettings(category)
            return
        }
        val trial = CategoryToneChange.pending(this, category) ?: return
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, trial.id)
        }
        try { startActivity(intent) }
        catch (_: Exception) { openAndroidNotificationSettings(category) }
    }

    private fun sendCategorySoundTrial(category: String) {
        val trial = CategoryToneChange.pending(this, category) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded()
            return
        }
        if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(this, "Разрешите уведомления BalanceHub в Android",
                Toast.LENGTH_LONG).show()
            return
        }
        val silent = trial.key == "silent" || (trial.key != "system" && trial.gain == 0)
        try {
            val notification = androidx.core.app.NotificationCompat.Builder(this, trial.id)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle("Проверка мелодии: " + notificationTitle(category))
                .setContentText("Проверьте выбранный звук и вибрацию")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(ReminderNoticeIds.test(), notification)
            ColorOsDirectVibration.afterNotification(this, category, trial.id)
            if (trial.automatic) {
                val result = PaymentAutomaticSound.afterNotification(this, trial.id,
                    trial.key, trial.gain, trial = true, category = category)
                if (result != PaymentAutomaticSound.Result.SERVICE_REQUESTED &&
                    !(silent && result == PaymentAutomaticSound.Result.SILENT_SELECTION)) {
                    Toast.makeText(this, "Автоматический звук не запущен: " + result,
                        Toast.LENGTH_LONG).show()
                    return
                }
            }
            if (!CategoryToneChange.markTested(this, category, trial.id)) {
                Toast.makeText(this, "Не удалось сохранить проверку",
                    Toast.LENGTH_LONG).show()
                return
            }
        } catch (error: Exception) {
            Toast.makeText(this,
                "Не удалось отправить тест: " + error.javaClass.simpleName,
                Toast.LENGTH_LONG).show()
            return
        }
        themedDialogBuilder()
            .setTitle(if (silent) "Уведомление пришло без звука?"
                else "Новая мелодия воспроизвелась?")
            .setMessage(if (silent)
                "Появилось ли уведомление без звука и с выбранной вибрацией?"
                else "Прозвучала ли нужная мелодия с правильной вибрацией? " +
                    "Если звук отсутствует или отличается, оставьте прежний канал.")
            .setNegativeButton("Нет") { _, _ ->
                CategoryToneChange.cancel(this, category)
                refreshSettingsWithoutJump()
            }
            .setNeutralButton("Канал Android") { _, _ ->
                openCategoryTrialAndroidSettings(category)
            }
            .setPositiveButton("Да, правильно") { _, _ ->
                val issue = CategoryToneChange.accept(this, category)
                if (issue != null) {
                    themedDialogBuilder()
                        .setTitle("Новая мелодия не применена")
                        .setMessage(issue + "\nРабочий канал сохранён.")
                        .setNegativeButton("Закрыть", null)
                        .setPositiveButton("Канал Android") { _, _ ->
                            openCategoryTrialAndroidSettings(category)
                        }.show()
                } else {
                    Toast.makeText(this, "Мелодия подтверждена и сохранена",
                        Toast.LENGTH_LONG).show()
                }
                refreshSettingsWithoutJump()
            }.show()
    }

    private fun showSoundChooser(category: String, title: String) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(8), dp(11), dp(10))
        }
        val dialog = themedDialogBuilder()
            .setTitle("Звук: " + title)
            .setView(android.widget.ScrollView(this).apply { addView(content) })
            .setNegativeButton("Отмена", null).create()
        val options = listOf("system" to "Системный звук") +
            BalanceNotificationSounds.tones.map { it.key to it.title } +
            listOf("silent" to "Без звука")
        val selected = BalanceNotificationSounds.selected(this, category)
        for ((key, label) in options) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(11), dp(5), dp(11), dp(5))
                background = rounded(if (key == selected) softAccent else surface, 11)
                isClickable = true
                isFocusable = true
                contentDescription = "Выбрать мелодию: " + label
            }
            val name = textLine(
                (if (key == selected) "✓  " else "     ") + label,
                15f, if (key == selected) accent else ink, key == selected)
            row.addView(name, LinearLayout.LayoutParams(0, dp(48), 1f))
            name.gravity = Gravity.CENTER_VERTICAL
            // Listening never selects a tone, closes the picker or changes a channel.
            if (key != "silent") {
                val listen = textLine("▶", 22f, accent, true).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "Прослушать мелодию: " + label
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        BalanceNotificationSounds.preview(
                            this@MainActivity, key, category)?.let { error ->
                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                row.addView(listen, LinearLayout.LayoutParams(dp(52), dp(48)))
            }
            row.setOnClickListener {
                if (category == BalanceNotificationSounds.PAYMENT &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // On supported ColorOS, always test a new payment tone through
                    // BalanceHub playback, even before automatic audio is enabled.
                    // The active channel and saved selection change only on confirmation.
                    val issue = PaymentToneChange.begin(this, key,
                        BalanceNotificationSounds.volume(this, category),
                        automatic = PaymentAutomaticSound.shouldOffer(this))
                    if (issue != null) Toast.makeText(this, issue, Toast.LENGTH_LONG).show()
                    else {
                        dialog.dismiss()
                        refreshSettingsWithoutJump()
                        showPaymentSoundTrialDialog()
                    }
                    return@setOnClickListener
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val issue = CategoryToneChange.begin(this, category, key,
                        BalanceNotificationSounds.volume(this, category),
                        automatic = PaymentAutomaticSound.shouldOffer(this))
                    if (issue != null) Toast.makeText(this, issue, Toast.LENGTH_LONG).show()
                    else {
                        dialog.dismiss()
                        refreshSettingsWithoutJump()
                        showCategorySoundTrialDialog(category)
                    }
                    return@setOnClickListener
                }
                val error = BalanceNotificationSounds.applyUserChoice(
                    this, category, key,
                    BalanceNotificationSounds.vibrationFor(this, category))
                if (error == null) {
                    dialog.dismiss()
                    refreshSettingsWithoutJump()
                } else if (error.contains("настройки канала", ignoreCase = true) ||
                    error.contains("настройках канала", ignoreCase = true)) {
                    themedDialogBuilder()
                        .setTitle("Настройки звука Android")
                        .setMessage(error)
                        .setNegativeButton("Позже", null)
                        .setPositiveButton("Открыть канал") { _, _ ->
                            openAndroidNotificationSettings(category)
                        }.show()
                } else Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
            content.addView(row)
            content.addView(verticalGap(3))
        }
        dialog.setOnDismissListener { BalanceNotificationSounds.stopPreview() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(dialogGradient())
        }
        dialog.show()
    }

    /**
     * An accessible local help center. All topics remain on this screen,
     * and the bank explanation is honest about the Push-only data source.
     */
    private fun showHelpCenter() {
        val sections = listOf(
            HelpSection("Как узнаём баланс Сбербанка", "Источник данных и ограничения",
                "credit-card", listOf(
                    "BalanceHub не подключается к банковскому счёту и не запрашивает " +
                        "логин, пароль, SMS-коды или банковский API.",
                    "Если СберБанк Онлайн прислал Push об операции и указал остаток " +
                        "в рублях, Android может передать его BalanceHub через " +
                        "разрешённый доступ к уведомлениям.",
                    "BalanceHub локально ищет именно обозначенный остаток, а не " +
                        "сумму покупки. Если значения неоднозначны, баланс не обновляется.",
                    "Показывается последний подтверждённый Push-баланс, время его " +
                        "получения и последние четыре цифры карты или счёта, если они есть. " +
                        "Это не онлайн-выписка и не гарантия текущего остатка."
                )),
            HelpSection("Подключение Сбербанка", "Пошаговая настройка доступа",
                "settings", listOf(
                    "1. Установите СберБанк Онлайн и включите банковские Push " +
                        "об операциях с информацией об остатке.",
                    "2. Включите получение банковских Push в BalanceHub.",
                    "3. Разрешите BalanceHub доступ к уведомлениям в настройках Android. " +
                        "Это широкое системное разрешение ко всем уведомлениям, хотя " +
                        "BalanceHub обрабатывает для баланса Push только трёх " +
                        "поддерживаемых банков: Сбербанка, ВТБ и Т-Банка.",
                    "4. Дождитесь реального Push с остатком: приложение не запрашивает " +
                        "банковские данные самостоятельно."
                )),
            HelpSection("ВТБ и Т-Банк (Тинькофф)", "Подключение Push и ограничения",
                "credit-card", listOf(
                    "BalanceHub обнаруживает установленные приложения ВТБ Онлайн и " +
                        "Т-Банка (бывший Тинькофф) локально. Увидеть и подключить " +
                        "их можно в Настройки → Банки.",
                    "После включения получения Push предоставьте Android доступ к " +
                        "уведомлениям для BalanceHub. Это широкое системное " +
                        "разрешение; приложению не нужны логин и пароль банка.",
                    "Для ВТБ и Т-Банка используются отдельные консервативные правила. " +
                        "Сумма покупки, перевода, бонусов или кредитного лимита " +
                        "никогда не должна приниматься за подтверждённый остаток.",
                    "Форматы уведомлений меняются. Если Push не содержит " +
                        "однозначного остатка в рублях или Android скрывает текст, " +
                        "данные останутся прежними или появится «Нет данных».",
                    "Сбербанк продолжает работать на своём прежнем проверенном " +
                        "обработчике. Скрытие банковской карточки не прекращает " +
                        "получение Push и не удаляет сохранённый баланс."
                )),
            HelpSection("Почему баланс не обновляется?", "Причины и способы проверки",
                "alarm-clock", listOf(
                    "Проверьте, включены ли банковские Push и доступ BalanceHub к уведомлениям.",
                    "Уведомление может содержать только сумму покупки без остатка. " +
                        "BalanceHub не будет выдавать расчётный остаток за настоящий.",
                    "Android может скрывать чувствительный банковский текст, особенно " +
                        "на новых версиях и отдельных прошивках. Скрытые данные " +
                        "приложение не восстанавливает.",
                    "Если баланс не меняется, дождитесь другого реального Push " +
                        "или проверьте диагностику в инженерном меню."
                )),
            HelpSection("Платежи и события", "Планирование, повторения и архив",
                "calendar-days", listOf(
                    "Добавьте дату и выберите, за сколько времени напоминать. " +
                        "Предстоящие платежи и события раскрываются нажатием на заголовок.",
                    "Повторяющиеся записи продолжают расписание автоматически; " +
                        "завершённые одноразовые записи попадают в архив.",
                    "Для точного срабатывания разрешите точные напоминания Android. " +
                        "Режим энергосбережения иногда откладывает уведомления."
                )),
            HelpSection("Звуки и громкость", "Настройки уведомлений BalanceHub",
                "music", listOf(
                    "Откройте Настройки → Уведомления. На Android 8 и новее " +
                        "мелодия и вибрация каждого канала меняются в настройках Android.",
                    "Общая или раздельная громкость BalanceHub уменьшает уровень " +
                        "фирменных сигналов относительно системного. Приложение " +
                        "не меняет громкость телефона.",
                    "Если звук отключён системой, включён режим «Не беспокоить» " +
                        "или запрещён канал уведомлений, настройки BalanceHub " +
                        "не обходят эти ограничения."
                )),
            HelpSection("Обновления приложения", "Скачивание и сохранность данных",
                "download", listOf(
                    "Новые одобренные версии проверяются на GitHub. Фоновая проверка " +
                        "обычно выполняется примерно раз в шесть часов.",
                    "При обнаружении обновления откройте сведения о версии, " +
                        "загрузите APK и подтвердите установку в Android.",
                    "Не удаляйте BalanceHub перед обновлением: подписанный тем же " +
                        "ключом APK с более высоким номером версии устанавливается " +
                        "поверх приложения с сохранением локальных данных."
                )),
            HelpSection("Конфиденциальность", "Как защищаются банковские данные",
                "archive", listOf(
                    "Обработка банковского Push происходит на самом телефоне. " +
                        "Полный текст уведомления и суммы отдельных покупок не сохраняются.",
                    "BalanceHub сохраняет подтверждённый остаток, время и " +
                        "необязательные последние четыре цифры карты или счёта. " +
                        "В диагностике хранятся технические признаки обработки.",
                    "Приложение не отправляет содержимое банковских Push на сервер. " +
                        "Отключение получения Push очищает сохранённые банковские остатки.",
                    "Не передавайте посторонним доступ к уведомлениям, ADB " +
                        "или содержимое банковских сообщений."
                ))
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(18))
        }
        val scroll = android.widget.ScrollView(this).apply { addView(content) }
        val dialog = themedDialogBuilder().setTitle("Помощь BalanceHub")
            .setView(scroll).setNegativeButton("Закрыть", null).create()
        lateinit var showIndex: () -> Unit
        fun showTopic(section: HelpSection) {
            content.removeAllViews()
            content.addView(secondaryButton("‹  Все разделы") { showIndex() })
            content.addView(verticalGap(15))
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(iconTile(section.icon, 38),
                    LinearLayout.LayoutParams(dp(38), dp(38)))
                addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
                addView(textLine(section.title, 18f, ink, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
            })
            content.addView(verticalGap(7))
            content.addView(textLine(section.subtitle, 13f, muted))
            for (paragraph in section.paragraphs) {
                content.addView(verticalGap(13))
                content.addView(textLine(paragraph, 14f, ink))
            }
            if (section.title == "Подключение Сбербанка") {
                content.addView(verticalGap(17))
                content.addView(primaryButton(
                    if (SberBalanceStore.isEnabled(this)) "Открыть разрешения Android"
                    else "Подключить банковские Push"
                ) {
                    if (SberBalanceStore.isEnabled(this)) {
                        openSberListenerSettings()
                    } else {
                        themedDialogBuilder()
                            .setTitle("Доступ к уведомлениям")
                            .setMessage("Android предоставит BalanceHub широкий доступ " +
                                "к уведомлениям телефона. Для банковского баланса " +
                                "обрабатываются только уведомления Сбербанка. " +
                                "Продолжить?")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Продолжить") { _, _ ->
                                SberBalanceStore.setEnabled(this, true)
                                openSberListenerSettings()
                            }.show()
                    }
                })
            }
            if (section.title == "Почему баланс не обновляется?") {
                content.addView(verticalGap(15))
                val status = when {
                    !SberBalanceStore.isEnabled(this) ->
                        "Получение банковских Push отключено."
                    !SberBalanceStore.isAccessGranted(this) ->
                        "Нет разрешения Android на чтение уведомлений."
                    SberBalanceStore.latest(this) == null ->
                        "Разрешения есть, ожидаем Push с подтверждённым остатком."
                    else -> "Последний подтверждённый баланс получен: " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT,
                            DateFormat.SHORT).format(Date(
                            SberBalanceStore.latest(this)!!.observedAt))
                }
                content.addView(textLine("Сейчас: " + status, 13f, accent, true))
            }
            scroll.post { scroll.scrollTo(0, 0) }
        }
        showIndex = {
            content.removeAllViews()
            content.addView(textLine("Чем помочь?", 21f, ink, true))
            content.addView(verticalGap(7))
            content.addView(textLine(
                "Выберите тему. Инструкции доступны без интернета.",
                13f, muted))
            for (section in sections) {
                content.addView(verticalGap(11))
                content.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(11), dp(11), dp(11), dp(11))
                    background = rounded(softAccent, 12)
                    isClickable = true
                    isFocusable = true
                    addView(iconTile(section.icon, 34),
                        LinearLayout.LayoutParams(dp(34), dp(34)))
                    addView(View(this@MainActivity),
                        LinearLayout.LayoutParams(dp(10), 1))
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(textLine(section.title, 14f, ink, true))
                        addView(verticalGap(3))
                        addView(textLine(section.subtitle, 12f, muted))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(textLine("›", 23f, accent, true))
                    setOnClickListener { showTopic(section) }
                })
            }
            content.addView(verticalGap(15))
            content.addView(secondaryButton("Пройти обучение повторно") {
                dialog.dismiss()
                restartTutorial()
            })
            scroll.post { scroll.scrollTo(0, 0) }
        }
        showIndex()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(dialogGradient())
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
        }
        dialog.show()
    }

    /**
     * Local, opt-in guide: display ADB commands for copying only. The app never
     * grants protected AppOps itself or changes system optimization.
     */
    private fun showBankPushHelp() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(12))
            addView(textLine("Получение Push Сбербанка", 19f, ink, true))
            addView(verticalGap(7))
            addView(textLine(
                "Пошаговая настройка для собственного телефона через Termux и ADB. " +
                    "Подходит не для всех прошивок Android. Выполняйте действия " +
                    "осознанно: расширенный доступ уменьшает защиту чувствительных " +
                    "уведомлений.",
                13f, muted
            ))
        }
        for (step in BankPushHelp.steps(packageName)) {
            content.addView(verticalGap(15))
            content.addView(textLine(step.heading, 15f, ink, true))
            content.addView(verticalGap(5))
            content.addView(textLine(step.detail, 13f, muted))
            val command = step.command
            if (command != null) {
                content.addView(verticalGap(8))
                content.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(softAccent, 12)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    addView(textLine(command, 12f, ink).apply {
                        setTextIsSelectable(true)
                        typeface = Typeface.MONOSPACE
                    })
                    addView(verticalGap(6))
                    addView(secondaryButton("Копировать команду") {
                        val manager = getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        manager.setPrimaryClip(android.content.ClipData.newPlainText(
                            "Команда ADB", command))
                        Toast.makeText(this@MainActivity, "Команда скопирована",
                            Toast.LENGTH_SHORT).show()
                    }, LinearLayout.LayoutParams(-1, dp(43)))
                })
            }
        }
        val dialog = themedDialogBuilder().setView(
            android.widget.ScrollView(this).apply {
                isFillViewport = false
                addView(content)
            })
            .setPositiveButton("Закрыть", null).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(dialogGradient())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        }
        dialog.show()
    }

    private fun openSberListenerSettings() {
        val component = ComponentName(this, SberPushNotificationListener::class.java)
        val detailed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component.flattenToString())
        } else null
        val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        // ColorOS/OPPO sometimes ignores the direct-detail screen: open the
        // common notification-access list first on those manufacturers.
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val preferGeneral = listOf("oppo", "oneplus", "realme").any {
            manufacturer.contains(it)
        }
        val candidates = if (preferGeneral) listOfNotNull(general, detailed)
            else listOfNotNull(detailed, general)
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Fall back to the other Android settings action.
            } catch (_: SecurityException) {
                // The manufacturer may restrict this settings page.
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData("package:$packageName".toUri()))
            Toast.makeText(this,
                "Откройте «Специальный доступ → Доступ к уведомлениям»",
                Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this,
                "Откройте настройки телефона → Приложения → Специальный доступ → Доступ к уведомлениям",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun renderBankSettings() {
        if (!engineeringMenuOpen) {
            bankSettingsOpen = false
            renderSettings()
            return
        }
        contentContainer.addView(secondaryButton("‹  Инженерное меню") {
            bankSettingsOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(16))
        contentContainer.addView(sberHeading("Сбербанк", 23f))
        contentContainer.addView(verticalGap(8))
        val bankCard = card()
        val enabled = SberBalanceStore.isEnabled(this)
        val granted = SberBalanceStore.isAccessGranted(this)
        bankCard.addView(sberHeading(
            if (enabled) "Получение Push включено" else "Подключение отключено", 16f
        ))
        bankCard.addView(verticalGap(8))
        bankCard.addView(textLine(
            "BalanceHub распознаёт явно обозначенный остаток в рублях, " +
                "включая «На лапках» и «Теперь у тебя». При сомнениях сумма не обновляется. " +
                "SMS и пароли не используются, сообщения в интернет не отправляются.",
            13f, muted
        ))
        bankCard.addView(verticalGap(10))
        bankCard.addView(textLine(
            if (granted) "Доступ Android к уведомлениям предоставлен"
            else "Доступ Android к уведомлениям не предоставлен",
            13f, if (granted) accent else muted
        ))
        bankCard.addView(verticalGap(10))
        if (!enabled) {
            bankCard.addView(primaryButton("Подключить Push Сбербанка") {
                SberBalanceStore.setEnabled(this, true)
                openSberListenerSettings()
                renderTab()
            })
        } else {
            if (!granted) {
                bankCard.addView(primaryButton("Настроить доступ к уведомлениям") {
                    openSberListenerSettings()
                })
                bankCard.addView(verticalGap(9))
            }
            bankCard.addView(secondaryButton("Отключить и удалить остатки") {
                themedDialogBuilder().setTitle("Отключить Сбербанк?")
                    .setMessage("Все сохранённые остатки Сбербанка будут удалены. " +
                        "Разрешение Android можно также отозвать в системных настройках.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Отключить") { _, _ ->
                        SberBalanceStore.setEnabled(this, false)
                        renderTab()
                    }.show()
            })
        }
        contentContainer.addView(bankCard)
        contentContainer.addView(verticalGap(12))
        val diagnostics = card()
        diagnostics.addView(sberHeading("Диагностика Push", 16f))
        diagnostics.addView(verticalGap(8))
        diagnostics.addView(textLine(
            "Доступ к уведомлениям: " +
                if (granted) "предоставлен" else "не предоставлен",
            13f, if (granted) accent else muted
        ))
        val installed = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(SberPushParser.SBER_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        diagnostics.addView(verticalGap(6))
        diagnostics.addView(textLine(
            "Приложение-источник: " +
                if (installed) "обнаружено" else "не найдено (возможна другая версия)",
            13f, muted
        ))
        diagnostics.addView(textLine(
            "Ожидаемый пакет: " + SberPushParser.SBER_PACKAGE,
            12f, muted
        ))
        diagnostics.addView(verticalGap(6))
        val events = if (enabled) SberPushDiagnostics.recent(this) else emptyList()
        val lastEvent = events.firstOrNull()
        if (lastEvent == null) {
            diagnostics.addView(textLine("Последний Push: ещё не получен", 13f, muted))
        } else {
            val formatter = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT)
            diagnostics.addView(textLine(
                "Последний Push: " + formatter.format(Date(lastEvent.receivedAt)),
                13f, ink
            ))
            val fields = SberNotificationText.Field.entries.filter {
                (lastEvent.fieldsMask and it.mask) != 0
            }
            diagnostics.addView(verticalGap(6))
            diagnostics.addView(textLine(
                "Доступные поля: " + if (fields.isEmpty()) "нет"
                    else fields.joinToString(", ") { it.label }, 12f, muted
            ))
            fields.forEach { field ->
                val size = lastEvent.lengths.getOrNull(field.ordinal)
                val money = (lastEvent.rubleFieldsMask and field.mask) != 0
                val cue = (lastEvent.cueFieldsMask and field.mask) != 0
                val account = (lastEvent.accountFieldsMask and field.mask) != 0
                diagnostics.addView(textLine(
                    field.label + " — длина: " + (size?.toString() ?: "?") +
                        " · рубли: " + if (money) "да" else "нет",
                    11f, muted
                ))
                diagnostics.addView(textLine(
                    "   Признак остатка: " + if (cue) "есть" else "нет",
                    11f, muted
                ))
                diagnostics.addView(textLine(
                    "   Маска счёта: " + if (account) "есть" else "нет",
                    11f, muted
                ))
            }
            diagnostics.addView(verticalGap(6))
            diagnostics.addView(textLine(
                "RemoteViews: " + if (lastEvent.remoteFlags != 0)
                    "обнаружены (не доказывает нестандартный макет)"
                    else "не обнаружены", 12f, muted
            ))
            val environment = when {
                (lastEvent.deliveryState and
                    SberPushNotificationListener.STATE_SCREEN_OFF) != 0 ->
                    "экран выключен"
                (lastEvent.deliveryState and
                    SberPushNotificationListener.STATE_LOCKED) != 0 ->
                    "экран заблокирован"
                lastEvent.deliveryState < 0 -> "не определено"
                else -> "экран активен"
            }
            diagnostics.addView(textLine("При получении: " + environment, 12f, muted))
            val explanation = when (lastEvent.outcome) {
                SberPushDiagnostics.Outcome.UPDATED -> "Баланс обновлён"
                SberPushDiagnostics.Outcome.SYSTEM_REDACTED ->
                    "Подтверждена системная подмена текста Android"
                SberPushDiagnostics.Outcome.POSSIBLY_REDACTED ->
                    "Возможна системная фильтрация (не доказана)"
                SberPushDiagnostics.Outcome.NO_BALANCE -> "Остаток в тексте не найден"
                SberPushDiagnostics.Outcome.NO_RUBLE_AMOUNT ->
                    "Не обнаружена подтверждённая сумма в рублях"
                SberPushDiagnostics.Outcome.NO_BALANCE_LABEL ->
                    "Есть сумма операции, но нет признака остатка"
                SberPushDiagnostics.Outcome.HISTORIC_ONLY ->
                    "Доступна лишь история сообщений, не текущая операция"
                SberPushDiagnostics.Outcome.AMBIGUOUS_BALANCE ->
                    "Неоднозначные суммы остатка"
                SberPushDiagnostics.Outcome.AMBIGUOUS_ACCOUNT ->
                    "Нельзя определить нужный счёт"
                SberPushDiagnostics.Outcome.INVALID_BALANCE -> "Некорректная сумма"
                SberPushDiagnostics.Outcome.OLD_NOTIFICATION -> "Старое уведомление"
                SberPushDiagnostics.Outcome.SAVE_FAILED -> "Не удалось сохранить остаток"
                SberPushDiagnostics.Outcome.EMPTY_NOTIFICATION -> "Нет читаемого текста"
                SberPushDiagnostics.Outcome.TITLE_ONLY -> "Доступен только заголовок"
                SberPushDiagnostics.Outcome.IGNORED_GROUP ->
                    "Групповое уведомление (не операция)"
                SberPushDiagnostics.Outcome.IGNORED_ONGOING ->
                    "Постоянное уведомление (не операция)"
            }
            diagnostics.addView(verticalGap(6))
            diagnostics.addView(textLine("Распознавание: " + explanation, 13f, muted))
            diagnostics.addView(verticalGap(5))
            diagnostics.addView(textLine(
                lastEvent.lastSuccessAt?.let {
                    "Последний подтверждённый баланс: " + formatter.format(Date(it))
                } ?: "Успешного обновления ещё не было",
                12f, muted
            ))
            if (lastEvent.outcome == SberPushDiagnostics.Outcome.SYSTEM_REDACTED) {
                diagnostics.addView(verticalGap(7))
                diagnostics.addView(textLine(
                    "Android передал скрытую версию уведомления. BalanceHub не " +
                        "может восстановить остаток из такого Push.",
                    12f, muted
                ))
            }
            if (events.size > 1) {
                diagnostics.addView(verticalGap(10))
                diagnostics.addView(textLine("Последние события", 14f, ink, true))
                events.take(5).forEach { item ->
                    val status = when (item.outcome) {
                        SberPushDiagnostics.Outcome.UPDATED -> "баланс обновлён"
                        SberPushDiagnostics.Outcome.SYSTEM_REDACTED -> "текст скрыт"
                        SberPushDiagnostics.Outcome.POSSIBLY_REDACTED ->
                            "вероятная фильтрация"
                        SberPushDiagnostics.Outcome.NO_BALANCE -> "остаток не найден"
                        SberPushDiagnostics.Outcome.NO_RUBLE_AMOUNT -> "нет суммы в рублях"
                        SberPushDiagnostics.Outcome.NO_BALANCE_LABEL -> "нет признака остатка"
                        SberPushDiagnostics.Outcome.HISTORIC_ONLY -> "только история"
                        SberPushDiagnostics.Outcome.IGNORED_GROUP -> "группа"
                        else -> item.outcome.name.lowercase(Locale.ROOT)
                    }
                    diagnostics.addView(textLine(
                        formatter.format(Date(item.receivedAt)) + " — " + status,
                        12f, muted
                    ))
                }
            }
        }
        diagnostics.addView(verticalGap(8))
        diagnostics.addView(textLine(
            "Хранятся только времена, длины полей и результаты до шести событий. " +
                "Тексты Push, суммы операций и номера счетов не сохраняются.",
            12f, muted
        ))
        diagnostics.addView(verticalGap(10))
        diagnostics.addView(secondaryButton("Проверить обработчик на примере") {
            val example = SberNotificationText.Payload(
                listOf("Сбербанк"),
                listOf("+ 10 ₽ — Теперь у тебя:\n40,89 ₽ Плат. счёт ••9667"),
                SberNotificationText.Field.TITLE.mask or
                    SberNotificationText.Field.TEXT.mask
            )
            val result = SberPushEvaluation.analyze(
                SberPushParser.SBER_PACKAGE, example
            )
            themedDialogBuilder().setTitle("Локальный тест распознавания")
                .setMessage(if (result.result?.kopecks == 4089L &&
                    result.result.lastFour == "9667") {
                    "Тест пройден: остаток 40,89 ₽, счёт ••9667. " +
                        "Сохранённый банковский баланс не изменён."
                } else {
                    "Тест не пройден: " + result.reason.name +
                        ". Сохранённый банковский баланс не изменён."
                })
                .setPositiveButton("Закрыть", null).show()
        })
        contentContainer.addView(diagnostics)
        contentContainer.addView(verticalGap(12))

        val help = card()
        help.addView(textLine("Как обновляется баланс", 16f, ink, true))
        help.addView(verticalGap(7))
        help.addView(textLine(
            "При следующем уведомлении с «Остаток», «Баланс», «Доступно», " +
                "«На лапках», «Теперь у тебя», «Теперь у вас» или другой фразой покажем " +
                "подтверждённую сумму. Сумма самой операции никогда не " +
                "подставляется вместо остатка. Если данные отсутствуют или " +
                "неоднозначны, прежний баланс сохраняется.",
            13f, muted
        ))
        help.addView(verticalGap(10))
        help.addView(textLine(
            "Если переход к разрешениям не открывается, используйте: " +
                "Настройки телефона → Приложения → Специальный доступ → Доступ к уведомлениям.",
            12f, muted
        ))
        contentContainer.addView(help)
    }

    private fun renderMobileSettings() {
        if (settingsStack.lastOrNull() == "mobile") {
            settingsHeader("Мобильная связь", "Настройки каждой SIM-карты")
        } else {
            contentContainer.addView(secondaryButton("‹  Все настройки") {
                mobileSettingsOpen = false
                renderTab()
            })
            contentContainer.addView(verticalGap(12))
            contentContainer.addView(textLine("Мобильная связь", 23f, ink, true))
            contentContainer.addView(verticalGap(12))
        }
        val sims = connectedSims()
        val sim = currentSim()
        if (sim == null) {
            contentContainer.addView(textLine("SIM-карта не обнаружена", 16f, ink))
            return
        }
        val id = sim.subscriptionId
        val op = MobileBalanceService.operator(this, sim)
        val name = MobileBalanceService.displayName(this, sim)
        val balance = MobileBalanceService.cached(this, sim)

        if (sims.size > 1) {
            val simsCard = compactSettingsCard()
            simsCard.addView(textLine("Выбранная SIM: " + sim.label, 17f, ink, true))
            simsCard.addView(verticalGap(9))
            simsCard.addView(secondaryButton("Выбрать другую SIM") {
                showThemedChoices(
                    "SIM-карта",
                    sims.map {
                        it.label + " · " + MobileBalanceService.displayName(this, it)
                    },
                    sims.indexOfFirst { it.subscriptionId == sim.subscriptionId }
                ) { index ->
                    selectedOperatorId = sims[index].subscriptionId
                    renderTab()
                }
            })
            contentContainer.addView(simsCard)
            contentContainer.addView(verticalGap(12))
        }
        val operatorCard = compactSettingsCard()
        operatorCard.addView(textLine("Оператор и баланс", 16f, ink, true))
        operatorCard.addView(verticalGap(8))
        operatorCard.addView(textLine("Оператор: " + name, 15f, ink, true))
        operatorCard.addView(textLine("SIM: " + sim.label, 13f, muted))
        operatorCard.addView(verticalGap(7))
        operatorCard.addView(secondaryButton("Выбрать оператора вручную") {
            val entries = listOf("Определять автоматически") +
                RussianMobileOperators.all.map { it.displayName } +
                listOf("Другой оператор…")
            val selected = preferences.getString("mobile_operator_$id", null)
            showThemedChoices(
                "Оператор мобильной связи", entries,
                if (selected == null) 0 else entries.indexOf(selected).takeIf { it >= 0 }
                    ?: entries.lastIndex
            ) { index ->
                    when (index) {
                        0 -> {
                            preferences.edit().remove("mobile_operator_$id").apply()
                            syncMobilePayment(sim)
                            renderTab()
                        }
                        entries.lastIndex -> {
                            val input = field("Название оператора")
                            themedDialogBuilder().setTitle("Другой оператор")
                                .setView(LinearLayout(this).apply {
                                    setPadding(dp(20), dp(10), dp(20), dp(10))
                                    addView(input)
                                })
                                .setNegativeButton("Отмена", null)
                                .setPositiveButton("Сохранить") { _, _ ->
                                    val custom = input.text.toString().trim()
                                    if (custom.isNotBlank()) {
                                        preferences.edit()
                                            .putString("mobile_operator_$id", custom).apply()
                                        syncMobilePayment(sim)
                                        renderTab()
                                    }
                                }.show()
                        }
                        else -> {
                            preferences.edit()
                                .putString("mobile_operator_$id", entries[index]).apply()
                            syncMobilePayment(sim)
                            renderTab()
                        }
                    }
            }
        })
        operatorCard.addView(verticalGap(10))
        operatorCard.addView(textLine("Баланс: " +
            (balance?.first?.let { it + " ₽" } ?: "нет данных"),
            16f, ink, true))
        balance?.let {
            operatorCard.addView(textLine("Обновлён: " +
                formatT2DateTime(it.second), 12f, muted))
        }
        operatorCard.addView(verticalGap(9))
        operatorCard.addView(secondaryButton("Указать баланс вручную") {
            showManualBalanceDialog(sim)
        })
        val code = op?.balanceUssd
        if (code != null) {
            operatorCard.addView(verticalGap(8))
            val message = textLine("USSD: " + code, 13f, muted)
            operatorCard.addView(message)
            operatorCard.addView(primaryButton("Проверить баланс") {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.CALL_PHONE
                    ) != PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.READ_PHONE_STATE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(
                        android.Manifest.permission.CALL_PHONE,
                        android.Manifest.permission.READ_PHONE_STATE), 510)
                    message.text = "Разрешите телефонные вызовы и повторите запрос"
                } else {
                    message.text = "Получаем ответ оператора…"
                    MobileBalanceService.check(this, sim, true) { result ->
                        runOnUiThread {
                            syncMobilePayment(sim)
                            message.text = result
                            MobileBalanceService.cached(this, sim)?.let { data ->
                                Toast.makeText(this,
                                    "Баланс: " + data.first + " ₽", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            })
        } else {
            operatorCard.addView(verticalGap(6))
            operatorCard.addView(textLine(
                "Непроверенная USSD-команда не отправляется. " +
                    "Все настройки и платежи доступны вручную.", 12f, muted
            ))
        }
        contentContainer.addView(operatorCard)
        contentContainer.addView(verticalGap(12))

        val billing = compactSettingsCard()
        billing.addView(textLine("Абонентская плата и дата", 16f, ink, true))
        billing.addView(verticalGap(7))
        billing.addView(textLine(
            "Стоимость тарифа вводится вручную и не берётся из баланса.",
            13f, muted
        ))
        billing.addView(verticalGap(8))
        val feeInput = field("Абонентская плата, ₽").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(preferences.getString(feeKeyFor(sim), ""))
        }
        billing.addView(feeInput)
        billing.addView(verticalGap(9))
        val saveFeeButton = primaryButton("Сохранить сумму") {
            val fee = feeInput.text.toString().trim().replace(',', '.')
            if (!fee.matches(Regex("""\d{1,7}(?:\.\d{1,2})?""")) ||
                fee.toBigDecimalOrNull()?.signum() != 1
            ) {
                Toast.makeText(this, "Введите корректную сумму больше нуля",
                    Toast.LENGTH_LONG).show()
            } else {
                preferences.edit().putString(feeKeyFor(sim), fee).apply()
                syncMobilePayment(sim)
                if (tutorialStep == 3) tutorialStep = 4
                Toast.makeText(this, "Абонентская плата сохранена", Toast.LENGTH_SHORT).show()
                requestNotificationPermissionIfNeeded()
                renderTab()
            }
        }
        if (tutorialStep == 3) {
            highlightTutorialTarget(feeInput)
            highlightTutorialTarget(saveFeeButton)
        }
        billing.addView(saveFeeButton)
        billing.addView(verticalGap(14))
        val manualDate = preferences.getLong(dateKeyFor(sim), 0L)
        billing.addView(textLine("Следующее списание: " +
            (operatorDueAt(sim)?.let { formatT2Date(it) } ?: "нет данных"),
            15f, ink, true))
        billing.addView(verticalGap(5))
        billing.addView(textLine(
            if (manualDate > 0L) "Дата указана вручную"
            else if (isT2(sim)) "Расчёт по сроку окончания пакетов (если 23:59)"
            else "Укажите дату следующего списания вручную",
            12f, muted
        ))
        billing.addView(verticalGap(10))
        val chooseDebitButton = secondaryButton("Указать дату списания") {
            val chosen = Calendar.getInstance().apply {
                timeInMillis = operatorDueAt(sim) ?: System.currentTimeMillis()
            }
            showThemedCalendar(chosen) {
                preferences.edit().putLong(dateKeyFor(sim), chosen.timeInMillis).apply()
                syncMobilePayment(sim)
                if (tutorialStep == 4) tutorialStep = 5
                renderTab()
            }
        }
        if (tutorialStep == 4) highlightTutorialTarget(chooseDebitButton)
        billing.addView(chooseDebitButton)
        if (manualDate > 0L && isT2(sim)) {
            billing.addView(verticalGap(6))
            billing.addView(secondaryButton("Вернуть расчётную дату") {
                preferences.edit().remove(dateKeyFor(sim)).apply()
                syncMobilePayment(sim)
                renderTab()
            })
        }
        billing.addView(verticalGap(13))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(textLine("Добавлять в предстоящие платежи", 14f, ink),
            LinearLayout.LayoutParams(0, -2, 1f))
        val paymentSwitch = androidx.appcompat.widget.SwitchCompat(this).apply {
            isChecked = preferences.getBoolean(enabledKeyFor(sim), true)
            contentDescription = "Автоматически создавать ежемесячный платёж"
            setOnCheckedChangeListener { _, enabled ->
                preferences.edit().putBoolean(enabledKeyFor(sim), enabled).apply()
                syncMobilePayment(sim)
                if (enabled) requestNotificationPermissionIfNeeded()
                if (tutorialStep == 5) renderTab()
            }
        }
        if (tutorialStep == 5) highlightTutorialTarget(row)
        row.addView(paymentSwitch)
        billing.addView(row)
        billing.addView(verticalGap(7))
        billing.addView(textLine(
            "Платёж повторяется ежемесячно. Напоминание — за 1 день.",
            12f, muted
        ))
        contentContainer.addView(billing)
        contentContainer.addView(verticalGap(14))

        val refresh = compactSettingsCard()
        refresh.addView(textLine("Автоматическая проверка", 16f, ink, true))
        refresh.addView(verticalGap(8))
        val refreshRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        refreshRow.addView(textLine("Проверять доступные USSD при открытии «Балансов»",
            13f, ink), LinearLayout.LayoutParams(0, -2, 1f))
        refreshRow.addView(androidx.appcompat.widget.SwitchCompat(this).apply {
            isChecked = preferences.getBoolean("mobile_auto_refresh_on_open", true)
            setOnCheckedChangeListener { _, enabled ->
                preferences.edit().putBoolean("mobile_auto_refresh_on_open", enabled).apply()
            }
        })
        refresh.addView(refreshRow)
        refresh.addView(verticalGap(7))
        refresh.addView(textLine(
            "Интервал между запросами ограничен. Поддержка USSD зависит от оператора и телефона.",
            12f, muted
        ))
        contentContainer.addView(refresh)
    }

    private fun field(hintText: String) = EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        hint = hintText
        maxLines = 1
        textSize = 16f
        setTextColor(ink)
        setHintTextColor(muted)
        backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
    }

    private fun chooseDate() {
        showThemedCalendar(dueDate) { updateDateButton() }
    }

    private fun updateDateButton() {
        dateButton.text = getString(R.string.payment_due_date, DateFormat.getDateInstance(DateFormat.MEDIUM).format(dueDate.time))
    }

    private fun saveReminder(): Boolean {
        val title = titleInput.text.toString().trim()
        val amount = amountInput.text.toString().trim().replace(',', '.')
        val currency = currencyInput.text.toString().trim().uppercase()
        val leadDays = leadDaysInput.text.toString().toIntOrNull()
        if (title.isBlank() || amount.toBigDecimalOrNull() == null ||
            !currency.matches(Regex("[A-Z]{3}")) || leadDays == null || leadDays !in 0..30
        ) {
            Toast.makeText(this, "Проверьте название, сумму, валюту и срок напоминания", Toast.LENGTH_LONG).show()
            return false
        }
        if (dueDate.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, "Укажите будущую дату и время платежа",
                Toast.LENGTH_LONG).show()
            return false
        }
        val recurrence = listOf("once", "weekly", "monthly", "custom")[recurrenceInput.selectedItemPosition]
        if (recurrence == "custom" && !validCustomPaymentPlan(pendingPaymentPlan)) {
            Toast.makeText(this, "Выберите даты или включите повторение по числам месяца", Toast.LENGTH_LONG).show()
            return false
        }
        val old = payments.firstOrNull { it.id == editingId }
        if (old != null) {
            cancelReminder(old)
            payments.removeAll { it.id == old.id }
        }
        val reminder = PaymentReminder(old?.id ?: UUID.randomUUID().toString(), title, amount,
            currency, dueDate.timeInMillis, leadDays, recurrence, selectedPaymentIcon,
            pendingPaymentPlan?.copy(startsAt = dueDate.timeInMillis))
        payments.add(reminder)
        editingId = null
        if (!persistPayments()) {
            payments.clear()
            loadPayments()
            return false
        }
        scheduleReminder(reminder)
        upcomingListExpanded = true
        expandedPaymentId = reminder.id
        preferences.edit().putBoolean("payments_list_expanded", true).apply()
        renderPayments()
        titleInput.text.clear()
        amountInput.text.clear()
        Toast.makeText(this, "Напоминание сохранено", Toast.LENGTH_SHORT).show()
        requestNotificationPermissionIfNeeded()
        return true
    }


    private fun updatePaymentPlan(payment: PaymentReminder, plan: PaymentPlan) {
        val idx = payments.indexOfFirst { it.id == payment.id }
        if (idx < 0) return
        val updated = payment.copy(plan = plan)
        payments[idx] = updated
        if (!persistPayments()) {
            payments.clear()
            loadPayments()
            return
        }
        PaymentOccurrenceScheduler.schedule(this, updated)
        PaymentNotificationRefresh.refresh(this, updated)
        if (PaymentArchiveStore.archiveDue(this).contains(updated.id)) {
            PaymentOccurrenceScheduler.cancel(this, updated.id)
            PaymentNotificationRefresh.cancelAll(this, updated.id)
            payments.removeAll { it.id == updated.id }
        }
        renderTab()
    }

    private fun skipPaymentDate(payment: PaymentReminder,
        selected: PaymentOccurrence, skipped: Boolean) {
        val day = PaymentOccurrenceEngine.day(selected.dueAt)
        val next = Calendar.getInstance().apply {
            timeInMillis = day
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        var plan = payment.plan ?: PaymentPlan(mode = "legacy", startsAt = payment.dueAt)
        for (occ in PaymentOccurrenceEngine.occurrences(payment, day, next)) {
            for (source in occ.sourceIds) {
                val before = plan.edits[source] ?: PaymentOccurrenceEdit(source)
                plan = plan.withEdit(before.copy(skipped = skipped),
                    if (skipped) "skip_date" else "restore_date", occ.dueAt, occ.dueAt)
            }
        }
        updatePaymentPlan(payment, plan)
    }

    private fun showPaymentChargeEditor(payment: PaymentReminder,
        occurrence: PaymentOccurrence) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
        }
        val date = Calendar.getInstance().apply { timeInMillis = occurrence.dueAt }
        val reminder = Calendar.getInstance().apply {
            timeInMillis = occurrence.dueAt
            set(Calendar.HOUR_OF_DAY, occurrence.reminderHour)
            set(Calendar.MINUTE, occurrence.reminderMinute)
        }
        var moved = occurrence.manuallyMoved
        val amount = field("Индивидуальная сумма").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(occurrence.amount)
        }
        val dateButton = secondaryButton("Дата: " +
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.time)) {
            showThemedCalendar(date) {
                moved = true
                // The date button is updated by the selection callback below.
            }
        }
        dateButton.setOnClickListener {
            showThemedCalendar(date) {
                moved = true
                dateButton.text = "Дата: " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.time)
            }
        }
        val dueClock = secondaryButton("Время оплаты: " +
            java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(date.time)) {}
        dueClock.setOnClickListener {
            BalanceTimePicker.show(this, date.get(Calendar.HOUR_OF_DAY),
                date.get(Calendar.MINUTE)) { h, m ->
                date.set(Calendar.HOUR_OF_DAY, h)
                date.set(Calendar.MINUTE, m)
                dueClock.text = "Время оплаты: " +
                    java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(date.time)
            }
        }
        val reminderClock = secondaryButton("Время напоминания: " +
            String.format(Locale.ROOT, "%02d:%02d",
                occurrence.reminderHour, occurrence.reminderMinute)) {}
        reminderClock.setOnClickListener {
            BalanceTimePicker.show(this, reminder.get(Calendar.HOUR_OF_DAY),
                reminder.get(Calendar.MINUTE)) { h, m ->
                reminder.set(Calendar.HOUR_OF_DAY, h)
                reminder.set(Calendar.MINUTE, m)
                reminderClock.text = String.format(Locale.ROOT,
                    "Время напоминания: %02d:%02d", h, m)
            }
        }
        val lead = field("Напомнить за дней").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(occurrence.leadDays.toString())
        }
        val skip = android.widget.CheckBox(this).apply {
            text = "Пропустить начисление"
            isChecked = occurrence.skipped
            setTextColor(ink)
        }
        val undo = android.widget.CheckBox(this).apply {
            text = "Отменить перенос"
            isEnabled = occurrence.manuallyMoved
            setTextColor(ink)
        }
        val scope = styledSpinner(listOf("Только это начисление",
            "Будущие этого исходного числа", "Все будущие начисления"))
        val includeSkipped = android.widget.CheckBox(this).apply {
            text = "Включить пропущенные будущие"
            setTextColor(ink)
        }
        val preserve = android.widget.CheckBox(this).apply {
            text = "Сохранить индивидуальные исключения"
            isChecked = true
            setTextColor(ink)
        }
        form.addView(textLine("Исходное число: " + occurrence.sourceDay, 12f, muted))
        form.addView(amount)
        form.addView(dateButton)
        form.addView(dueClock)
        form.addView(reminderClock)
        form.addView(lead)
        form.addView(skip)
        form.addView(undo)
        form.addView(textLine("Область применения", 12f, muted))
        form.addView(scope)
        form.addView(includeSkipped)
        form.addView(preserve)
        val dialog = themedDialogBuilder().setTitle("Настройки начисления")
            .setView(android.widget.ScrollView(this).apply { addView(form) })
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Просмотреть изменения", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val sum = amount.text.toString().trim().replace(',', '.')
                val days = lead.text.toString().toIntOrNull()
                if (sum.toBigDecimalOrNull() == null || days == null || days !in 0..30) {
                    Toast.makeText(this, "Проверьте сумму и дни напоминания",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val now = System.currentTimeMillis()
                val scopeIndex = scope.selectedItemPosition
                if (scopeIndex > 0 && occurrence.dueAt <= now) {
                    Toast.makeText(this, "Массовые изменения только для будущих начислений",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val base = payment.plan ?: PaymentPlan(mode = "legacy",
                    startsAt = payment.dueAt)
                var result = base
                if (scopeIndex == 0) {
                    for (source in occurrence.sourceIds) {
                        val old = result.edits[source] ?: PaymentOccurrenceEdit(source)
                        val edit = old.copy(amount = sum,
                            movedAt = if (undo.isChecked) null
                                else if (moved) date.timeInMillis else old.movedAt,
                            paymentHour = if (undo.isChecked) null else
                                date.get(Calendar.HOUR_OF_DAY),
                            paymentMinute = if (undo.isChecked) null else
                                date.get(Calendar.MINUTE),
                            reminderHour = reminder.get(Calendar.HOUR_OF_DAY),
                            reminderMinute = reminder.get(Calendar.MINUTE),
                            leadDays = days, skipped = skip.isChecked,
                            manualMove = !undo.isChecked && (moved || old.manualMove))
                        result = result.withEdit(edit,
                            if (undo.isChecked) "undo_move" else "edit",
                            occurrence.dueAt, date.timeInMillis)
                    }
                } else {
                    val start = now + 1L
                    val rule = PaymentBulkEdit(start,
                        if (scopeIndex == 1) occurrence.sourceDay else null,
                        sum, date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE),
                        reminder.get(Calendar.HOUR_OF_DAY),
                        reminder.get(Calendar.MINUTE), days, includeSkipped.isChecked)
                    val kept = if (preserve.isChecked) result.edits else
                        result.edits.filter { (source, edit) ->
                            val actual = edit.movedAt
                                ?: PaymentOccurrenceEngine.dateForSource(source) ?: 0L
                            val matching = scopeIndex == 2 ||
                                source.substringAfterLast('-').toIntOrNull() ==
                                occurrence.sourceDay
                            actual < start || !matching ||
                                (edit.skipped && !includeSkipped.isChecked)
                        }
                    result = result.copy(edits = kept,
                        bulkEdits = result.bulkEdits + rule,
                        history = result.history + PaymentHistoryEntry(
                            occurrence.id, "bulk_edit", now, occurrence.dueAt, null,
                            rule.toString()))
                }
                val approved = result
                val overdueTransfer = moved && !undo.isChecked &&
                    date.timeInMillis < now
                val willArchive = overdueTransfer && PaymentArchiveStore.archiveDeadline(
                    payment.copy(plan = approved))?.let { now >= it } == true
                val message = (if (scopeIndex == 0) "Одно начисление"
                    else if (scopeIndex == 1) "Будущие начисления исходного числа"
                    else "Все будущие начисления") + "\\nСумма: " + sum + " " +
                    payment.currency + "\\nВремя оплаты: " +
                    String.format(Locale.ROOT, "%02d:%02d",
                        date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE)) +
                    "\\nИсключения: " +
                    (if (preserve.isChecked) "сохранить" else "сбросить") +
                    (if (willArchive) "\\nВнимание: перенос в прошлое приведёт " +
                        "к немедленному архивированию карточки."
                    else if (overdueTransfer) "\\nВнимание: дата переноса уже прошла."
                    else "")
                themedDialogBuilder().setTitle(
                        if (overdueTransfer) "Подтвердить перенос в прошлое"
                        else "Применить изменения?")
                    .setMessage(message)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Применить") { _, _ ->
                        updatePaymentPlan(payment, approved)
                        dialog.dismiss()
                    }.show()
            }
        }
        dialog.show()
    }


    /** A shared compact row for payments and events, including long names. */
    private fun compactScheduleRow(iconId: String, title: String, trailing: String,
        onOpen: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        isClickable = true
        isFocusable = true
        contentDescription = title + ", " + trailing + ", открыть календарь"
        addView(iconTile(iconId, 38), LinearLayout.LayoutParams(dp(38), dp(38)))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
        addView(textLine(title, 14f, ink, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(8), 1))
        addView(textLine(trailing, 14f, ink, true).apply {
            gravity = Gravity.END
            maxLines = 1
        })
        addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(8), 1))
        addView(iconTile("chevron-right", 24),
            LinearLayout.LayoutParams(dp(24), dp(24)))
        setOnClickListener { onOpen() }
    }

    private data class PlannedCalendarEntry(
        val sourceId: String, val at: Long, val caption: String, val skipped: Boolean
    )

    /** Both sections use exactly the same month grid, spacing and actions. */
    private fun showPlannedCalendar(
        title: String, summary: String, initialAt: Long,
        monthEntries: (Long, Long) -> List<PlannedCalendarEntry>,
        onEntry: (PlannedCalendarEntry) -> Unit,
        actions: List<Pair<String, () -> Unit>>
    ) {
        val visible = Calendar.getInstance().apply {
            timeInMillis = initialAt
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = dialogGradient()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(softAccent, 16)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            addView(textLine(title, 18f, ink, true))
            addView(verticalGap(5))
            addView(textLine(summary, 12f, muted))
        }
        wrapper.addView(header)
        wrapper.addView(verticalGap(14))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val previous = secondaryButton("‹") {}
        val monthLabel = textLine("", 16f, ink, true).apply {
            gravity = Gravity.CENTER
        }
        val following = secondaryButton("›") {}
        controls.addView(previous, LinearLayout.LayoutParams(dp(46), dp(46)))
        controls.addView(monthLabel, LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(following, LinearLayout.LayoutParams(dp(46), dp(46)))
        wrapper.addView(controls)
        wrapper.addView(verticalGap(9))
        val grid = GridLayout(this).apply { columnCount = 7 }
        wrapper.addView(grid)
        wrapper.addView(verticalGap(11))
        val hint = textLine("", 12f, muted)
        wrapper.addView(hint)
        wrapper.addView(verticalGap(12))
        val actionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(actionPanel)
        val dialog = themedDialogBuilder()
            .setView(android.widget.ScrollView(this).apply { addView(wrapper) })
            .setNegativeButton("Закрыть", null).create()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

        fun refreshMonth() {
            monthLabel.text = SimpleDateFormat("LLLL yyyy", Locale("ru"))
                .format(visible.time).replaceFirstChar { it.uppercase() }
            grid.removeAllViews()
            val start = visible.timeInMillis
            val until = (visible.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
            }.timeInMillis
            val entries = monthEntries(start, until)
            val byDay = entries.groupBy { PaymentOccurrenceEngine.day(it.at) }
            hint.text = "Запланировано: " + entries.count { !it.skipped } +
                " · Нажмите выделенную дату для изменения или пропуска"
            val width = (resources.displayMetrics.widthPixels - dp(94))
                .coerceAtMost(dp(350)) / 7
            val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            names.forEachIndexed { index, name ->
                grid.addView(textLine(name, 12f,
                    if (index >= 5) accent else muted, true).apply {
                    gravity = Gravity.CENTER
                }, android.view.ViewGroup.LayoutParams(width, dp(34)))
            }
            val offset = (visible.get(Calendar.DAY_OF_WEEK) + 5) % 7
            repeat(offset) {
                grid.addView(View(this), android.view.ViewGroup.LayoutParams(width, dp(44)))
            }
            for (dayNumber in 1..visible.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                val day = (visible.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, dayNumber)
                }.timeInMillis
                val chosen = byDay[day].orEmpty()
                val active = chosen.any { !it.skipped }
                val skippedOnly = chosen.isNotEmpty() && !active
                val weekend = (offset + dayNumber - 1) % 7 >= 5
                val cell = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                    isClickable = chosen.isNotEmpty()
                    isFocusable = chosen.isNotEmpty()
                    contentDescription = "Дата " + dateFormat.format(Date(day)) +
                        if (chosen.isEmpty()) ", нет записей"
                        else if (skippedOnly) ", пропущено" else ", запланировано"
                }
                cell.addView(textLine(dayNumber.toString(), 15f,
                    if (active) Color.WHITE else if (weekend) accent else ink,
                    active).apply {
                    gravity = Gravity.CENTER
                    background = rounded(when {
                        active -> accent
                        skippedOnly -> softAccent
                        else -> surface
                    }, 12)
                }, LinearLayout.LayoutParams(-1, -1))
                if (chosen.isNotEmpty()) cell.setOnClickListener {
                    if (chosen.size == 1) {
                        dialog.dismiss()
                        onEntry(chosen.first())
                    } else {
                        themedDialogBuilder().setTitle("Записи на " +
                            dateFormat.format(Date(day)))
                            .setItems(chosen.map { it.caption }.toTypedArray()) { _, index ->
                                dialog.dismiss()
                                onEntry(chosen[index])
                            }
                            .setNegativeButton("Отмена", null).show()
                    }
                }
                grid.addView(cell, android.view.ViewGroup.LayoutParams(width, dp(44)))
            }
        }

        previous.setOnClickListener { visible.add(Calendar.MONTH, -1); refreshMonth() }
        following.setOnClickListener { visible.add(Calendar.MONTH, 1); refreshMonth() }
        for (group in actions.chunked(2)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEachIndexed { index, action ->
                if (index > 0) row.addView(View(this),
                    LinearLayout.LayoutParams(dp(7), 1))
                row.addView(secondaryButton(action.first) {
                    dialog.dismiss()
                    action.second()
                }, LinearLayout.LayoutParams(0, dp(46), 1f))
            }
            actionPanel.addView(row)
            actionPanel.addView(verticalGap(7))
        }
        refreshMonth()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun showPaymentSchedule(payment: PaymentReminder) {
        val auto = payment.id.startsWith("t2_subscription_") ||
            payment.id.startsWith("mobile_subscription_")
        val actions: List<Pair<String, () -> Unit>> = if (auto) listOf(
            "Настроить" to {
                val simId = payment.id.removePrefix("t2_subscription_")
                    .removePrefix("mobile_subscription_").toIntOrNull()
                if (simId != null) selectedOperatorId = simId
                mobileSettingsOpen = true
                selectTab(4)
            },
            "Отключить" to {
                if (PaymentArchiveStore.archiveById(
                        this, payment.id, PaymentArchiveStore.DELETED)) {
                    val prefix = if (payment.id.startsWith("t2_subscription_"))
                        "t2_payment_enabled_" else "mobile_payment_enabled_"
                    val sim = payment.id.removePrefix("t2_subscription_")
                        .removePrefix("mobile_subscription_")
                    preferences.edit().putBoolean(prefix + sim, false).apply()
                    cancelReminder(payment)
                    payments.removeAll { it.id == payment.id }
                    expandedPaymentId = null
                    renderPayments()
                } else Toast.makeText(this, "Не удалось сохранить в архив",
                    Toast.LENGTH_LONG).show()
            }
        ) else listOf(
            "Изменить платёж" to { showEditPaymentDialog(payment) },
            "Удалить" to { showDeletePaymentDialog(payment) }
        )
        val next = PaymentOccurrenceEngine.upcoming(
            payment, System.currentTimeMillis(), 1).firstOrNull()?.dueAt ?: payment.dueAt
        showPlannedCalendar(payment.title,
            PaymentCurrencyFormatter.amountWithCurrency(payment.amount, payment.currency) +
                " · " + recurrenceLabel(payment.recurrence) +
                " · Напомнить за " + payment.leadDays + " дн.",
            next,
            monthEntries = { from, until ->
                PaymentOccurrenceEngine.occurrences(payment, from, until).map {
                    PlannedCalendarEntry(it.id, it.dueAt,
                        eventDateLabel(it.dueAt) + " · " +
                            PaymentCurrencyFormatter.amountWithCurrency(
                                it.amount, it.currency) +
                            if (it.skipped) " · пропущено" else "", it.skipped)
                }
            },
            onEntry = { entry ->
                val from = PaymentOccurrenceEngine.day(entry.at)
                val until = Calendar.getInstance().apply {
                    timeInMillis = from
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                PaymentOccurrenceEngine.occurrences(payment, from, until)
                    .firstOrNull { it.id == entry.sourceId && it.dueAt == entry.at }
                    ?.let { showPaymentChargeEditor(payment, it) }
            },
            actions = actions)
    }

    private fun showEventBulkActions(event: PlannedEvent) {
        val from = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis
        val until = Calendar.getInstance().apply {
            add(Calendar.YEAR, 2)
        }.timeInMillis
        val visits = EventOccurrenceEngine.between(event, from, until).take(24)
        if (visits.isEmpty()) {
            Toast.makeText(this, "Нет дат для изменения", Toast.LENGTH_SHORT).show()
            return
        }
        val chosen = mutableSetOf<String>()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(12))
            addView(textLine("Выберите даты для пропуска или восстановления",
                13f, muted))
        }
        for (visit in visits) {
            body.addView(android.widget.CheckBox(this).apply {
                text = eventDateLabel(visit.dueAt) +
                    if (visit.skipped) " · пропущено" else ""
                setTextColor(ink)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) chosen.add(visit.sourceId)
                    else chosen.remove(visit.sourceId)
                }
            })
        }
        val dialog = themedDialogBuilder().setTitle("Даты события")
            .setView(android.widget.ScrollView(this).apply { addView(body) }).create()
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(secondaryButton("Пропустить") {
            dialog.dismiss()
            applyEventVisitChanges(event, chosen.toSet(), skip = true)
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        footer.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        footer.addView(secondaryButton("Восстановить") {
            dialog.dismiss()
            applyEventVisitChanges(event, chosen.toSet(), skip = false)
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        body.addView(verticalGap(10))
        body.addView(footer)
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun showEventVisitActions(event: PlannedEvent, visit: EventOccurrence) {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
            addView(textLine(
                eventRepeatLabel(event.recurrence) +
                    if (visit.skipped) " · пропущено" else " · запланировано",
                13f, muted))
            addView(verticalGap(12))
        }
        val dialog = themedDialogBuilder()
            .setTitle(eventDateLabel(visit.dueAt))
            .setView(body).setNegativeButton("Закрыть", null).create()
        fun action(name: String, run: () -> Unit) {
            body.addView(secondaryButton(name) {
                dialog.dismiss()
                run()
            }, LinearLayout.LayoutParams(-1, dp(46)))
            body.addView(verticalGap(8))
        }
        action("Перенести дату") { moveEventVisit(event, visit) }
        action(if (visit.skipped) "Восстановить дату" else "Пропустить дату") {
            applyEventVisitChanges(event, setOf(visit.sourceId), skip = !visit.skipped)
        }
        if (visit.moved) action("Отменить перенос") {
            applyEventVisitChanges(event, setOf(visit.sourceId), undoMove = true)
        }
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun showEventSchedule(event: PlannedEvent) {
        val next = EventOccurrenceEngine.next(
            event, System.currentTimeMillis() - 1L)?.dueAt ?: event.dueAt
        val lead = when (event.leadMinutes) {
            0 -> "В момент события"
            10 -> "За 10 минут"
            60 -> "За 1 час"
            1_440 -> "За 1 день"
            4_320 -> "За 3 дня"
            else -> "За " + event.leadMinutes + " минут"
        }
        showPlannedCalendar(event.title, eventRepeatLabel(event.recurrence) +
            " · " + lead + if (event.details.isBlank()) "" else
                " · " + event.details, next,
            monthEntries = { from, until ->
                EventOccurrenceEngine.between(event, from, until).map {
                    PlannedCalendarEntry(it.sourceId, it.dueAt,
                        eventDateLabel(it.dueAt) +
                            if (it.skipped) " · пропущено" else "", it.skipped)
                }
            },
            onEntry = { entry ->
                val from = PaymentOccurrenceEngine.day(entry.at)
                val until = Calendar.getInstance().apply {
                    timeInMillis = from
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                EventOccurrenceEngine.between(event, from, until)
                    .firstOrNull { it.sourceId == entry.sourceId && it.dueAt == entry.at }
                    ?.let { showEventVisitActions(event, it) }
            },
            actions = listOf(
                "Изменить событие" to { showEventEditor(event) },
                "Выбрать даты" to { showEventBulkActions(event) },
                "Готово" to { completeEvent(event) },
                "В архив" to { moveEventToArchive(event) }
            ))
    }



    private fun renderPayments() {
        if (!::listContainer.isInitialized || currentTab != 2 || archiveOpen) return
        listContainer.removeAllViews()
        if (winterActive) {
            val sortedWinter = payments.sortedBy {
                PaymentOccurrenceEngine.upcoming(it, System.currentTimeMillis(), 1)
                    .firstOrNull()?.dueAt ?: Long.MAX_VALUE
            }
            if (sortedWinter.isEmpty()) {
                listContainer.addView(textLine(
                    "Пока нет платежей. Добавьте первый платёж кнопкой «＋».",
                    14f, muted).apply { setPadding(dp(12), dp(20), dp(12), dp(20)) })
            }
            sortedWinter.forEach { payment ->
                val due = PaymentOccurrenceEngine.upcoming(
                    payment, System.currentTimeMillis(), 1
                ).firstOrNull()?.dueAt ?: payment.dueAt
                val dayLabel = SimpleDateFormat("d MMM yyyy", Locale("ru"))
                    .format(Date(due))
                val until = ((due - System.currentTimeMillis() + 86_399_999L) /
                    86_400_000L).coerceAtLeast(0L)
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = cardGradient()
                    isClickable = true
                    isFocusable = true
                    contentDescription = payment.title + ", " + dayLabel +
                        ", открыть календарь"
                    addView(iconTile(payment.iconId ?: IconRegistry.select(
                        payment.title, "calendar-days"), 38),
                        LinearLayout.LayoutParams(dp(38), dp(38)))
                    addView(View(this@MainActivity),
                        LinearLayout.LayoutParams(dp(9), 1))
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(textLine(payment.title, 14f, ink, true).apply {
                            maxLines = 2
                        })
                        addView(textLine(dayLabel, 11f, muted))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.END
                        addView(textLine(
                            PaymentCurrencyFormatter.amountForList(payment.amount),
                            13f, ink, true).apply { gravity = Gravity.END })
                        addView(verticalGap(3))
                        addView(textLine("$until дн.", 11f,
                            Color.WHITE, true).apply {
                            gravity = Gravity.CENTER
                            setPadding(dp(7), dp(2), dp(7), dp(2))
                            background = filledButtonGradient()
                        })
                    })
                    setOnClickListener { showPaymentSchedule(payment) }
                }
                listContainer.addView(tile, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
            }
            return
        }
        val sorted = payments.sortedBy {
            PaymentOccurrenceEngine.upcoming(it, System.currentTimeMillis(), 1)
                .firstOrNull()?.dueAt ?: Long.MAX_VALUE
        }
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardGradient()
            elevation = dp(2).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            isClickable = true
            isFocusable = true
            contentDescription = "Предстоящие платежи, " + sorted.size +
                if (upcomingListExpanded) ", свернуть список" else ", раскрыть список"
            addView(iconTile("calendar-days", 38),
                LinearLayout.LayoutParams(dp(38), dp(38)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(textLine("Предстоящие платежи", 17f, ink, true))
                addView(verticalGap(3))
                addView(textLine(
                    if (upcomingListExpanded) "Нажмите, чтобы свернуть · " + sorted.size
                    else "Нажмите, чтобы раскрыть · " + sorted.size, 12f, muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(iconTile(if (upcomingListExpanded) "chevron-up" else
                "chevron-down", 32), LinearLayout.LayoutParams(dp(32), dp(32)))
            setOnClickListener {
                upcomingListExpanded = !upcomingListExpanded
                if (!upcomingListExpanded) expandedPaymentId = null
                preferences.edit().putBoolean("payments_list_expanded", upcomingListExpanded).apply()
                renderPayments()
            }
        }
        section.addView(header)
        if (upcomingListExpanded) {
            section.addView(View(this).apply {
                setBackgroundColor(if (isNight) Color.rgb(63, 100, 102)
                    else Color.rgb(201, 228, 224))
            }, LinearLayout.LayoutParams(-1, dp(1)))
            if (sorted.isEmpty()) section.addView(textLine(
                "Пока нет напоминаний. Добавьте первый платёж выше.", 13f, muted
            ).apply { setPadding(dp(15), dp(17), dp(15), dp(17)) })
            sorted.forEachIndexed { index, payment ->
                section.addView(compactScheduleRow(
                    payment.iconId ?: IconRegistry.select(payment.title, "credit-card"),
                    payment.title,
                    PaymentCurrencyFormatter.amountForList(payment.amount)
                ) { showPaymentSchedule(payment) })
                if (index < sorted.lastIndex) section.addView(View(this).apply {
                    setBackgroundColor(if (isNight) Color.rgb(54, 84, 89)
                        else Color.rgb(213, 231, 228))
                }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        listContainer.addView(section)
        val archivedCount = PaymentArchiveStore.archived(this).size
        listContainer.addView(verticalGap(14))
        listContainer.addView(settingsPanel(settingsRow("archive", "Архив платежей",
            archivedCount.toString() + " записей · завершённые и удалённые") {
            archiveOpen = true
            expandedArchivePaymentId = null
            renderTab()
        }))
        listContainer.addView(verticalGap(22))
    }


    /** One compact gradient panel with the same dividers as current payments. */
    private fun renderArchiveScreen() {
        contentContainer.addView(secondaryButton("‹  К платежам") {
            archiveOpen = false
            expandedArchivePaymentId = null
            renderTab()
        })
        contentContainer.addView(verticalGap(12))
        contentContainer.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconTile("archive", 36),
                LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
            addView(textLine("Архив платежей", 23f, ink, true).apply {
                maxLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        contentContainer.addView(verticalGap(5))
        contentContainer.addView(textLine(
            "Завершённые и удалённые напоминания сохраняются здесь.", 13f, muted))
        contentContainer.addView(verticalGap(14))
        val entries = PaymentArchiveStore.archived(this)
        if (entries.isEmpty()) {
            val empty = card()
            empty.addView(textLine("Архив пока пуст", 16f, ink, true))
            empty.addView(verticalGap(6))
            empty.addView(textLine(
                "Записи появятся после завершения одноразового платежа или удаления.",
                13f, muted))
            contentContainer.addView(empty)
            return
        }
        val rows = mutableListOf<View>()
        for (entry in entries) {
            val expanded = expandedArchivePaymentId == entry.id
            val amount = PaymentCurrencyFormatter.amountForList(entry.amount)
            val dueLabel = DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(Date(entry.dueAt))
            val status = if (entry.reason == PaymentArchiveStore.COMPLETED)
                "Срок истёк" else "Удалён пользователем"
            val glyph = entry.iconId ?: IconRegistry.select(entry.title, "credit-card")
            rows.add(settingsRow(glyph, entry.title,
                "$amount · $dueLabel · $status") {
                val scroll = dashboardScroll.scrollY
                expandedArchivePaymentId = if (expanded) null else entry.id
                renderTab()
                dashboardScroll.post {
                    if (currentTab == 2 && archiveOpen) dashboardScroll.scrollTo(0, scroll)
                }
            })
            if (expanded) {
                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(8), dp(14), dp(14))
                    addView(textLine(status + " · " + recurrenceLabel(entry.recurrence),
                        12f, accent, true))
                    addView(verticalGap(5))
                    addView(textLine(
                        "Напоминание за " + entry.leadDays + " дн. · в архиве с " +
                        DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(Date(entry.archivedAt)), 12f, muted))
                    val history = PaymentArchiveStore.archivedPlan(
                        this@MainActivity, entry.id)?.history.orEmpty()
                    if (history.isNotEmpty()) {
                        addView(verticalGap(12))
                        addView(textLine("История начислений и изменений",
                            13f, ink, true))
                        for (change in history) {
                            val whenChanged = DateFormat.getDateTimeInstance(
                                DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(change.timestamp))
                            val dateBefore = change.previousAt?.let {
                                DateFormat.getDateInstance(DateFormat.SHORT)
                                    .format(Date(it))
                            } ?: "—"
                            val dateAfter = change.currentAt?.let {
                                DateFormat.getDateInstance(DateFormat.SHORT)
                                    .format(Date(it))
                            } ?: "—"
                            addView(textLine(whenChanged + " · " + change.action +
                                " · " + change.sourceId + " · " + dateBefore +
                                " → " + dateAfter, 11f, muted))
                            change.details?.let { addView(textLine(it, 10f, muted)) }
                        }
                    }
                    addView(verticalGap(12))
                    val stack = resources.configuration.screenWidthDp < 365
                    val actions = LinearLayout(this@MainActivity).apply {
                        orientation = if (stack) LinearLayout.VERTICAL
                            else LinearLayout.HORIZONTAL
                    }
                    actions.addView(secondaryButton("Восстановить") {
                        restoreArchivedPayment(entry)
                    }, if (stack) LinearLayout.LayoutParams(-1, dp(46))
                        else LinearLayout.LayoutParams(0, dp(46), 1f))
                    actions.addView(View(this@MainActivity),
                        LinearLayout.LayoutParams(if (stack) 1 else dp(8),
                            if (stack) dp(8) else 1))
                    actions.addView(secondaryButton("Удалить навсегда") {
                        deleteArchivedPayment(entry)
                    }, if (stack) LinearLayout.LayoutParams(-1, dp(46))
                        else LinearLayout.LayoutParams(0, dp(46), 1f))
                    addView(actions)
                }
                rows.add(details)
            }
        }
        contentContainer.addView(settingsPanel(*rows.toTypedArray()))
        contentContainer.addView(verticalGap(18))
    }

    private fun restoreArchivedPayment(entry: ArchivedPayment) {
        val future = Calendar.getInstance().apply {
            if (entry.dueAt > System.currentTimeMillis()) {
                timeInMillis = entry.dueAt
            } else {
                add(Calendar.DAY_OF_YEAR, 7)
            }
        }
        themedDialogBuilder()
            .setTitle("Восстановить напоминание")
            .setMessage("Выберите новую дату платежа, чтобы запись снова стала активной.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Выбрать дату") { _, _ ->
                showThemedCalendar(future) {
                    val restored = PaymentArchiveStore.restore(
                        this, entry.id, future.timeInMillis
                    )
                    if (restored == null) {
                        Toast.makeText(this,
                            "Нужна будущая дата. Запись осталась в архиве.",
                            Toast.LENGTH_LONG).show()
                    } else {
                        val payment = PaymentArchiveStore.activeSnapshot(this)
                            ?.let { array ->
                                (0 until array.length()).mapNotNull { i ->
                                    array.optJSONObject(i)?.let(PaymentReminderCodec::decode)
                                }.firstOrNull { it.id == restored.id }
                            } ?: return@showThemedCalendar
                        payments.add(payment)
                        if (payment.id.startsWith("t2_subscription_") ||
                            payment.id.startsWith("mobile_subscription_")) {
                            val id = payment.id.removePrefix("t2_subscription_")
                                .removePrefix("mobile_subscription_")
                            val prefix = if (payment.id.startsWith("t2_subscription_"))
                                "t2_payment_enabled_" else "mobile_payment_enabled_"
                            preferences.edit().putBoolean(prefix + id, true).apply()
                        }
                        scheduleReminder(payment)
                        requestNotificationPermissionIfNeeded()
                        Toast.makeText(this, "Платёж восстановлен", Toast.LENGTH_SHORT).show()
                        renderTab()
                    }
                }
            }.show()
    }

    private fun deleteArchivedPayment(entry: ArchivedPayment) {
        themedDialogBuilder()
            .setTitle("Удалить навсегда?")
            .setMessage("«${entry.title}» будет безвозвратно удалён из архива.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить навсегда") { _, _ ->
                if (PaymentArchiveStore.deletePermanently(this, entry.id)) {
                    renderTab()
                } else {
                    Toast.makeText(this, "Не удалось удалить запись",
                        Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun showDeletePaymentDialog(payment: PaymentReminder) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = dialogGradient()
        }
        panel.addView(textLine("Удалить платёж?", 21f, ink, true))
        panel.addView(verticalGap(12))
        panel.addView(textLine("«${payment.title}» переместится в папку «Архив платежей». Запись можно восстановить.", 15f, muted))
        panel.addView(verticalGap(24))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dialog = themedDialogBuilder().setView(panel).create()
        buttons.addView(secondaryButton("Отмена") { dialog.dismiss() },
            LinearLayout.LayoutParams(0, dp(52), 1f))
        buttons.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        buttons.addView(primaryButton("В архив") {
            if (PaymentArchiveStore.archiveById(
                    this, payment.id, PaymentArchiveStore.DELETED
                )) {
                cancelReminder(payment)
                PaymentNotificationRefresh.cancelAll(this, payment.id)
                payments.removeAll { it.id == payment.id }
                if (editingId == payment.id) editingId = null
                renderTab()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Не удалось сохранить платёж в архив",
                    Toast.LENGTH_LONG).show()
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        panel.addView(buttons)
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    /**
     * Select several payment dates in a single dialog. Taps update a temporary
     * set; only "Готово" commits it to the payment form.
     */
    private fun showThemedMultiDateCalendar(
        selectedDates: Set<Long>, onSelected: (Set<Long>) -> Unit
    ) {
        val chosen = selectedDates.mapTo(linkedSetOf()) { PaymentOccurrenceEngine.day(it) }
        val visible = Calendar.getInstance().apply {
            timeInMillis = chosen.minOrNull() ?: System.currentTimeMillis()
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = dialogGradient()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(softAccent, 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        header.addView(textLine("Выберите даты платежей", 17f, ink, true))
        val chosenLabel = textLine("", 13f, muted)
        header.addView(chosenLabel)
        wrapper.addView(header)
        wrapper.addView(verticalGap(16))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prev = secondaryButton("‹") {}
        val month = textLine("", 16f, ink, true).apply { gravity = Gravity.CENTER }
        val next = secondaryButton("›") {}
        controls.addView(prev, LinearLayout.LayoutParams(dp(46), dp(46)))
        controls.addView(month, LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(next, LinearLayout.LayoutParams(dp(46), dp(46)))
        wrapper.addView(controls)
        wrapper.addView(verticalGap(10))
        val grid = GridLayout(this).apply { columnCount = 7 }
        wrapper.addView(grid)
        wrapper.addView(verticalGap(8))
        wrapper.addView(textLine("Нажмите дату для выбора или отмены выбора", 12f, muted))
        wrapper.addView(verticalGap(12))
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        wrapper.addView(footer)
        val dialog = themedDialogBuilder().setView(wrapper).create()
        val dayFormat = java.text.SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

        fun refresh() {
            chosenLabel.text = "Выбрано дат: ${chosen.size}"
            month.text = java.text.SimpleDateFormat("LLLL yyyy", Locale("ru"))
                .format(visible.time).replaceFirstChar { it.uppercase() }
            grid.removeAllViews()
            val availableWidth = (resources.displayMetrics.widthPixels - dp(94)).coerceAtMost(dp(350))
            val cellWidth = availableWidth / 7
            val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            dayNames.forEachIndexed { index, day ->
                grid.addView(textLine(day, 12f,
                    if (index >= 5) accent else muted, true).apply {
                    gravity = Gravity.CENTER
                }, android.view.ViewGroup.LayoutParams(cellWidth, dp(36)))
            }
            val offset = (visible.get(Calendar.DAY_OF_WEEK) + 5) % 7
            repeat(offset) {
                grid.addView(View(this), android.view.ViewGroup.LayoutParams(cellWidth, dp(46)))
            }
            val daysInMonth = visible.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (day in 1..daysInMonth) {
                val date = Calendar.getInstance().apply {
                    clear()
                    set(visible.get(Calendar.YEAR), visible.get(Calendar.MONTH), day)
                }.timeInMillis
                val active = date in chosen
                val weekend = ((offset + day - 1) % 7) >= 5
                val cell = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    contentDescription = "Дата ${dayFormat.format(Date(date))}, " +
                        if (active) "выбрана" else "не выбрана"
                    isClickable = true
                    isFocusable = true
                }
                val label = textLine(day.toString(), 15f,
                    if (active) Color.WHITE else if (weekend) accent else ink, active).apply {
                    gravity = Gravity.CENTER
                    background = rounded(if (active) accent else surface, 24)
                }
                cell.addView(label, LinearLayout.LayoutParams(-1, -1))
                cell.setOnClickListener {
                    if (!chosen.add(date)) chosen.remove(date)
                    refresh()
                }
                grid.addView(cell, android.view.ViewGroup.LayoutParams(cellWidth, dp(46)))
            }
        }
        prev.setOnClickListener { visible.add(Calendar.MONTH, -1); refresh() }
        next.setOnClickListener { visible.add(Calendar.MONTH, 1); refresh() }
        footer.addView(secondaryButton("Отмена") { dialog.dismiss() },
            LinearLayout.LayoutParams(0, dp(50), 1f))
        footer.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        footer.addView(primaryButton("Готово") {
            onSelected(chosen.toSet())
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        refresh()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun showThemedCalendar(selected: Calendar, onSelected: () -> Unit) {
        val visible = (selected.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val chosen = selected.clone() as Calendar
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = dialogGradient()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(softAccent, 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val emblem = textLine("▣", 25f, accent, true)
        header.addView(emblem)
        header.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val chosenLabel = textLine("", 17f, ink, true)
        header.addView(chosenLabel, LinearLayout.LayoutParams(0, -2, 1f))
        wrapper.addView(header)
        wrapper.addView(verticalGap(16))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val prev = secondaryButton("‹") {}
        val month = textLine("", 16f, ink, true).apply { gravity = Gravity.CENTER }
        val next = secondaryButton("›") {}
        controls.addView(prev, LinearLayout.LayoutParams(dp(46), dp(46)))
        controls.addView(month, LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(next, LinearLayout.LayoutParams(dp(46), dp(46)))
        wrapper.addView(controls)
        wrapper.addView(verticalGap(10))
        val grid = GridLayout(this).apply { columnCount = 7 }
        wrapper.addView(grid)
        wrapper.addView(verticalGap(16))
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        wrapper.addView(footer)
        val dialog = themedDialogBuilder().setView(wrapper).create()
        val format = java.text.SimpleDateFormat("EEE, d MMM yyyy 'г.'", java.util.Locale("ru"))
        fun refresh() {
            chosenLabel.text = format.format(chosen.time).replaceFirstChar { it.uppercase() }
            month.text = java.text.SimpleDateFormat("LLLL yyyy", java.util.Locale("ru"))
                .format(visible.time).replaceFirstChar { it.uppercase() }
            grid.removeAllViews()
            val availableWidth = (resources.displayMetrics.widthPixels - dp(94)).coerceAtMost(dp(350))
            val cellWidth = availableWidth / 7
            val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            dayNames.forEachIndexed { index, day ->
                grid.addView(textLine(day, 12f, if (index >= 5) accent else muted, true).apply {
                    gravity = Gravity.CENTER
                }, android.view.ViewGroup.LayoutParams(cellWidth, dp(36)))
            }
            val offset = (visible.get(Calendar.DAY_OF_WEEK) + 5) % 7
            repeat(offset) {
                grid.addView(View(this), android.view.ViewGroup.LayoutParams(cellWidth, dp(46)))
            }
            val days = visible.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (day in 1..days) {
                val active = chosen.get(Calendar.YEAR) == visible.get(Calendar.YEAR) &&
                    chosen.get(Calendar.MONTH) == visible.get(Calendar.MONTH) &&
                    chosen.get(Calendar.DAY_OF_MONTH) == day
                val weekend = ((offset + day - 1) % 7) >= 5
                val cell = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                }
                val label = textLine(day.toString(), 15f,
                    if (active) Color.WHITE else if (weekend) accent else ink, active).apply {
                    gravity = Gravity.CENTER
                    background = rounded(if (active) accent else surface, 24)
                }
                cell.addView(label, LinearLayout.LayoutParams(-1, -1))
                cell.setOnClickListener {
                    ReminderDateTimePolicy.selectDay(chosen,
                        visible.get(Calendar.YEAR), visible.get(Calendar.MONTH), day)
                    refresh()
                }
                grid.addView(cell, android.view.ViewGroup.LayoutParams(cellWidth, dp(46)))
            }
        }
        prev.setOnClickListener { visible.add(Calendar.MONTH, -1); refresh() }
        next.setOnClickListener { visible.add(Calendar.MONTH, 1); refresh() }
        footer.addView(secondaryButton("Отмена") { dialog.dismiss() },
            LinearLayout.LayoutParams(0, dp(50), 1f))
        footer.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        footer.addView(primaryButton("ОК") {
            selected.timeInMillis = chosen.timeInMillis
            onSelected()
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        refresh()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun showEditPaymentDialog(payment: PaymentReminder) {
        pendingPaymentPlan = payment.plan
        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
            background = dialogGradient()
        }
        fun editField(value: String, hintText: String): EditText =
            field(hintText).apply { setText(value) }
        val name = editField(payment.title, "Название")
        val amount = editField(payment.amount, "Сумма").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val currency = editField(payment.currency, "Валюта")
        val days = editField(payment.leadDays.toString(), "Дней до напоминания").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        var chosenIcon = payment.iconId
        val iconRow = iconSelector(name, "credit-card", chosenIcon) {
            chosenIcon = it
        }
        val date = Calendar.getInstance().apply { timeInMillis = payment.dueAt }
        val dateControl = secondaryButton("Дата: " +
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.time)) {}
        val timeControl = secondaryButton("Время: " +
            java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(date.time)) {}
        dateControl.setOnClickListener {
            showThemedCalendar(date) {
                dateControl.text = "Дата: " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.time)
            }
        }
        timeControl.setOnClickListener {
            BalanceTimePicker.show(this, date.get(Calendar.HOUR_OF_DAY),
                date.get(Calendar.MINUTE)) { hour, minute ->
                date.set(Calendar.HOUR_OF_DAY, hour)
                date.set(Calendar.MINUTE, minute)
                date.set(Calendar.SECOND, 0)
                date.set(Calendar.MILLISECOND, 0)
                timeControl.text = "Время: " +
                    java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(date.time)
            }
        }
        val options = listOf("Единоразовый", "Еженедельный", "Ежемесячный", "Пользовательское")
        val recurrence = Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(
                this@MainActivity, android.R.layout.simple_spinner_item, options
            ) {
                override fun getView(position: Int, convertView: View?,
                    parent: android.view.ViewGroup): View =
                    textLine(options[position], 16f, ink).apply {
                        setPadding(dp(14), dp(14), dp(14), dp(14))
                    }
                override fun getDropDownView(position: Int, convertView: View?,
                    parent: android.view.ViewGroup): View =
                    textLine(options[position], 16f, ink).apply {
                        setPadding(dp(14), dp(14), dp(14), dp(14))
                        setBackgroundColor(surface)
                    }
            }
            background = rounded(softAccent, 12)
            setSelection(listOf("once", "weekly", "monthly", "custom")
                .indexOf(payment.recurrence).coerceAtLeast(0))
        }
        editor.addView(formFieldCaption("Название"))
        editor.addView(name)
        editor.addView(verticalGap(10))
        editor.addView(formFieldCaption("Иконка"))
        editor.addView(iconRow)
        editor.addView(verticalGap(10))
        editor.addView(formFieldCaption("Сумма"))
        editor.addView(amount)
        editor.addView(verticalGap(10))
        editor.addView(formFieldCaption("Валюта"))
        editor.addView(currency)
        editor.addView(verticalGap(12))
        val paymentDateTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        paymentDateTime.addView(dateControl, LinearLayout.LayoutParams(0, dp(48), 3f))
        paymentDateTime.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        paymentDateTime.addView(timeControl, LinearLayout.LayoutParams(0, dp(48), 2f))
        editor.addView(paymentDateTime)
        editor.addView(verticalGap(12))
        editor.addView(formFieldCaption("Повторение"))
        editor.addView(verticalGap(6))
        editor.addView(recurrence)
        installCustomPaymentControls(editor, recurrence, payment.plan)
        editor.addView(verticalGap(12))
        editor.addView(formFieldCaption("Напомнить за (дней)"))
        editor.addView(days)
        val dialog = themedDialogBuilder()
            .setCustomTitle(textLine("Изменить платёж", 21f, ink, true).apply {
                setBackgroundColor(surface)
                setPadding(dp(24), dp(22), dp(20), dp(12))
            })
            .setView(android.widget.ScrollView(this).apply {
                setBackgroundColor(surface)
                addView(editor)
            })
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(dialogGradient())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ink)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val newTitle = name.text.toString().trim()
                val newAmount = amount.text.toString().trim().replace(',', '.')
                val newCurrency = currency.text.toString().trim().uppercase()
                val newDays = days.text.toString().toIntOrNull()
                if (newTitle.isBlank() || newAmount.toBigDecimalOrNull() == null ||
                    !newCurrency.matches(Regex("[A-Z]{3}")) ||
                    newDays == null || newDays !in 0..30) {
                    Toast.makeText(this, "Проверьте данные платежа", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val newCycle = listOf("once", "weekly", "monthly", "custom")[
                    recurrence.selectedItemPosition]
                if (newCycle == "custom" && !validCustomPaymentPlan(pendingPaymentPlan)) {
                    Toast.makeText(this, "Выберите даты или числа месяца",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val updated = payment.copy(title = newTitle, amount = newAmount,
                    currency = newCurrency, dueAt = date.timeInMillis,
                    leadDays = newDays, recurrence = newCycle,
                    iconId = chosenIcon,
                    plan = pendingPaymentPlan?.copy(startsAt = payment.plan?.startsAt
                        ?.takeIf { it > 0L } ?: date.timeInMillis))
                val oldPreview = PaymentOccurrenceEngine.upcoming(
                    payment, System.currentTimeMillis(), 4)
                    .joinToString(", ") {
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.dueAt))
                    }
                val newPreview = PaymentOccurrenceEngine.upcoming(
                    updated, System.currentTimeMillis(), 4)
                    .joinToString(", ") {
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.dueAt))
                    }
                fun commitChange(value: PaymentReminder) {
                    val index = payments.indexOfFirst { it.id == payment.id }
                    if (index < 0) return
                    payments[index] = value
                    if (!persistPayments()) {
                        payments.clear()
                        loadPayments()
                        return
                    }
                    scheduleReminder(value)
                    PaymentNotificationRefresh.refresh(this, value)
                    renderPayments()
                    dialog.dismiss()
                }
                val preview = "Ближайшие даты до изменения: " +
                    oldPreview.ifBlank { "нет" } + "\nПосле изменения: " +
                    newPreview.ifBlank { "нет" } +
                    "\n\nСохранить индивидуальные изменения, пропуски и ручные переносы?"
                themedDialogBuilder()
                    .setTitle("Проверить новое расписание")
                    .setMessage(preview)
                    .setNegativeButton("Отмена", null)
                    .setNeutralButton("Сбросить исключения") { _, _ ->
                        val revised = updated.copy(plan = updated.plan?.copy(
                            edits = emptyMap(), bulkEdits = emptyList(),
                            history = (updated.plan?.history ?: emptyList()) + PaymentHistoryEntry(
                                "", "reset_exceptions", System.currentTimeMillis(),
                                payment.dueAt, updated.dueAt)))
                        commitChange(revised)
                    }
                    .setPositiveButton("Сохранить исключения") { _, _ ->
                        commitChange(updated)
                    }.show()
            }
        }
        dialog.show()
    }

    /** Common theme-aware icon badge used by payments, events and archives. */
    private fun iconTile(id: String, size: Int = 42): ImageView =
        ImageView(this).apply {
            val color = IconRegistry.tint(id, isNight)
            setImageDrawable(AppIconDrawable(id, color))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(softAccent, 12)
            val inset = (size / 5).coerceAtLeast(6)
            setPadding(dp(inset), dp(inset), dp(inset), dp(inset))
            contentDescription = IconRegistry.find(id)?.label ?: "Иконка"
        }

    /** Keep the left edge of field labels aligned with native EditText content. */
    private fun formFieldCaption(value: String) =
        textLine(value, 13f, muted).apply {
            setPadding(dp(4), 0, dp(4), 0)
        }

    /** Custom panel captions match the selected spinner text across nesting levels. */
    private fun customRecurrenceCaption(value: String = "", horizontalInsetDp: Int = 14) =
        textLine(value, 12f, muted).apply {
            setPadding(dp(horizontalInsetDp), 0, dp(horizontalInsetDp), 0)
        }

    private fun styledSpinner(
        options: List<String>, selected: Int = 0, horizontalInsetDp: Int = 14
    ): Spinner =
        Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(
                this@MainActivity, android.R.layout.simple_spinner_item, options
            ) {
                override fun getView(position: Int, convertView: View?,
                    parent: android.view.ViewGroup): View =
                    textLine(options[position], 16f, ink).apply {
                        setPadding(dp(horizontalInsetDp), dp(12), dp(horizontalInsetDp), dp(12))
                    }
                override fun getDropDownView(position: Int, convertView: View?,
                    parent: android.view.ViewGroup): View =
                    textLine(options[position], 16f, ink).apply {
                        setBackgroundColor(surface)
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                    }
            }
            background = rounded(softAccent, 12)
            minimumHeight = dp(50)
            setSelection(selected.coerceIn(0, options.lastIndex))
        }

    private fun iconSelector(
        input: EditText, fallback: String, initial: String?,
        onChosen: (String) -> Unit
    ): LinearLayout {
        var manual = initial
        val chosen = manual ?: IconRegistry.select(input.text.toString(), fallback)
        val picture = iconTile(chosen, 42)
        val label = textLine(
            if (manual != null) "Выбрано: " + (IconRegistry.find(chosen)?.label ?: chosen)
                else "Подбор по названию · изменить", 13f, muted
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(10), dp(6))
            background = rounded(softAccent, 12)
            addView(picture, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(12), 1))
            addView(label)
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int,
                after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int,
                count: Int) {
                if (manual == null) picture.setImageDrawable(
                    AppIconDrawable(IconRegistry.select(s.toString(), fallback),
                        IconRegistry.tint(IconRegistry.select(s.toString(), fallback), isNight)))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        row.setOnClickListener {
            showIconChooser(manual) { id ->
                manual = id
                onChosen(id)
                picture.setImageDrawable(AppIconDrawable(id, IconRegistry.tint(id, isNight)))
                label.text = "Выбрано: " + (IconRegistry.find(id)?.label ?: id)
            }
        }
        return row
    }

    private fun showIconChooser(initial: String?, onSelected: (String) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val grid = GridLayout(this).apply { columnCount = 4 }
        val dialog = themedDialogBuilder().setTitle("Выберите иконку")
            .setView(android.widget.ScrollView(this).apply {
                addView(container)
            })
            .setNegativeButton("Отмена", null).create()
        val cellSize = ((resources.configuration.screenWidthDp - 62) / 4).coerceAtMost(100)
        for (icon in IconRegistry.all) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(7), dp(3), dp(7))
                background = rounded(
                    if (icon.id == initial) softAccent else Color.TRANSPARENT, 9)
                addView(iconTile(icon.id, 39),
                    LinearLayout.LayoutParams(dp(39), dp(39)))
                addView(verticalGap(4))
                addView(textLine(icon.label, 10f, ink).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                setOnClickListener {
                    onSelected(icon.id)
                    dialog.dismiss()
                }
            }
            grid.addView(item, android.view.ViewGroup.LayoutParams(dp(cellSize), dp(78)))
        }
        container.addView(grid)
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun eventDateLabel(value: Long): String =
        java.text.SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("ru"))
            .format(Date(value))

    private fun eventRepeatLabel(value: String): String = when (value) {
        "daily" -> "Каждый день"
        "weekly" -> "Каждую неделю"
        "monthly" -> "Каждый месяц"
        "yearly" -> "Каждый год"
        else -> "Не повторяется"
    }


    private fun applyEventVisitChanges(
        event: PlannedEvent, ids: Set<String>, skip: Boolean? = null,
        movedAt: Long? = null, undoMove: Boolean = false
    ) {
        if (ids.isEmpty()) return
        var plan = event.plan ?: EventPlan(anchorAt = event.dueAt)
        val now = System.currentTimeMillis()
        for (id in ids) {
            val old = plan.edits[id] ?: EventOccurrenceEdit(id)
            val updated = old.copy(skipped = skip ?: old.skipped,
                movedAt = if (undoMove) null else movedAt ?: old.movedAt)
            plan = plan.withEdit(updated,
                when {
                    undoMove -> "undo_move"
                    movedAt != null -> "move"
                    skip == true -> "skip"
                    else -> "restore"
                }, EventOccurrenceEngine.between(event, now - 400L * 86_400_000L,
                    now + 400L * 86_400_000L)
                    .firstOrNull { it.sourceId == id }?.dueAt, updated.movedAt)
        }
        var updated = event.copy(plan = plan)
        val next = EventOccurrenceEngine.next(updated, now - 1L)
        if (next == null) {
            // The last/only occurrence was skipped: do not leave a stale alarm.
            if (EventStore.archive(this, event.id, "skipped") != null) {
                EventScheduler.cancel(this, event.id)
                renderTab()
            } else Toast.makeText(this, "Не удалось сохранить дату",
                Toast.LENGTH_LONG).show()
            return
        }
        updated = updated.copy(dueAt = next.dueAt)
        if (EventStore.save(this, updated)) {
            val oldDue = event.dueAt
            androidx.core.app.NotificationManagerCompat.from(this).cancel(
                ReminderNoticeIds.event(event.id, oldDue,
                    DailyReminderPolicy.dayKey(now)))
            DailyReminderLedger.invalidate(this, "event", event.id)
            EventScheduler.cancel(this, event.id)
            EventScheduler.schedule(this, updated)
            renderTab()
        } else Toast.makeText(this, "Не удалось сохранить посещения",
            Toast.LENGTH_LONG).show()
    }

    private fun moveEventVisit(event: PlannedEvent, visit: EventOccurrence) {
        val date = Calendar.getInstance().apply { timeInMillis = visit.dueAt }
        showThemedCalendar(date) {
            val target = date.timeInMillis
            val apply = {
                applyEventVisitChanges(event, setOf(visit.sourceId), movedAt = target)
            }
            if (target < System.currentTimeMillis()) {
                themedDialogBuilder().setTitle("Перенос в прошлое")
                    .setMessage("Выбранная дата уже прошла. Перенести посещение?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Перенести") { _, _ -> apply() }.show()
            } else apply()
        }
    }


    private fun renderEvents() {
        EventStore.reconcileElapsed(this)
        if (winterActive && EventStore.active(this).isEmpty()) {
            contentContainer.addView(textLine("События", 23f, ink, true))
            contentContainer.addView(verticalGap(10))
            val archiveCount = EventStore.archived(this).size
            val segments = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = cardGradient()
                setPadding(dp(3), dp(3), dp(3), dp(3))
                addView(textLine("Предстоящие  0", 13f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    background = filledButtonGradient()
                }, LinearLayout.LayoutParams(0, dp(39), 1f))
                addView(textLine("Архив", 13f, ink, true).apply {
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Архив событий, $archiveCount записей"
                    setOnClickListener {
                        eventArchiveExpanded = true
                        renderTab()
                    }
                }, LinearLayout.LayoutParams(0, dp(39), 1f))
            }
            contentContainer.addView(segments)
            contentContainer.addView(verticalGap(14))
            contentContainer.addView(View(this).apply {
                background = WinterEmptyEventsDrawable(resources.displayMetrics.density)
                contentDescription = "Заснеженный лес и календарь со снежинкой"
            }, LinearLayout.LayoutParams(-1, dp(225)))
            contentContainer.addView(textLine("Нет предстоящих событий",
                17f, ink, true).apply { gravity = Gravity.CENTER })
            contentContainer.addView(verticalGap(7))
            contentContainer.addView(textLine(
                "Добавьте важные события,\nчтобы ничего не забыть",
                13f, muted).apply { gravity = Gravity.CENTER })
            contentContainer.addView(verticalGap(18))
            contentContainer.addView(primaryButton("＋  Добавить событие") {
                showEventEditor()
            })
            return
        }
        contentContainer.addView(textLine("События", 23f, ink, true))
        contentContainer.addView(verticalGap(6))
        contentContainer.addView(textLine(
            "Важные даты и планы под вашим контролем", 13f, muted))
        contentContainer.addView(verticalGap(13))
        contentContainer.addView(primaryButton("＋  Добавить событие") {
            showEventEditor()
        })
        contentContainer.addView(verticalGap(17))

        EventStore.reconcileElapsed(this)
        val upcoming = EventStore.active(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardGradient()
            elevation = dp(2).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            isClickable = true
            isFocusable = true
            contentDescription = "Предстоящие события, " + upcoming.size +
                if (eventListExpanded) ", свернуть список" else ", раскрыть список"
            addView(iconTile("calendar-days", 38),
                LinearLayout.LayoutParams(dp(38), dp(38)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(textLine("Предстоящие события", 17f, ink, true))
                addView(verticalGap(3))
                addView(textLine(if (eventListExpanded)
                    "Нажмите, чтобы свернуть · " + upcoming.size
                    else "Нажмите, чтобы раскрыть · " + upcoming.size, 12f, muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(iconTile(if (eventListExpanded) "chevron-up" else "chevron-down", 32),
                LinearLayout.LayoutParams(dp(32), dp(32)))
            setOnClickListener {
                eventListExpanded = !eventListExpanded
                if (!eventListExpanded) expandedEventId = null
                preferences.edit().putBoolean("event_list_expanded", eventListExpanded).apply()
                renderTab()
            }
        }
        list.addView(header)
        if (eventListExpanded) {
            list.addView(View(this).apply {
                setBackgroundColor(if (isNight) Color.rgb(63, 100, 102)
                    else Color.rgb(201, 228, 224))
            }, LinearLayout.LayoutParams(-1, dp(1)))
            if (upcoming.isEmpty()) list.addView(textLine(
                "Событий пока нет. Запланируйте первое событие.", 13f, muted
            ).apply { setPadding(dp(15), dp(17), dp(15), dp(17)) })
            upcoming.forEachIndexed { index, event ->
                val date = DateFormat.getDateInstance(DateFormat.SHORT)
                    .format(Date(event.dueAt))
                list.addView(compactScheduleRow(
                    event.iconId ?: IconRegistry.select(event.title, "calendar-days"),
                    event.title, date
                ) { showEventSchedule(event) })
                if (index < upcoming.lastIndex) list.addView(View(this).apply {
                    setBackgroundColor(if (isNight) Color.rgb(54, 84, 89)
                        else Color.rgb(213, 231, 228))
                }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        contentContainer.addView(list)
        contentContainer.addView(verticalGap(14))
        val archivedCount = EventStore.archived(this).size
        contentContainer.addView(settingsPanel(settingsRow("archive", "Архив событий",
            archivedCount.toString() + " записей · завершённые и удалённые") {
            eventArchiveExpanded = true
            expandedArchiveEventId = null
            renderTab()
        }))
        contentContainer.addView(verticalGap(22))
    }


    private fun renderEventArchiveScreen() {
        contentContainer.addView(secondaryButton("‹  К событиям") {
            eventArchiveExpanded = false
            expandedArchiveEventId = null
            renderTab()
        })
        contentContainer.addView(verticalGap(12))
        contentContainer.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconTile("archive", 36),
                LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(10), 1))
            addView(textLine("Архив событий", 23f, ink, true).apply {
                maxLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        contentContainer.addView(verticalGap(5))
        contentContainer.addView(textLine(
            "Завершённые и удалённые события сохраняются здесь.", 13f, muted))
        contentContainer.addView(verticalGap(14))
        val entries = EventStore.archived(this)
        if (entries.isEmpty()) {
            val empty = card()
            empty.addView(textLine("Архив пока пуст", 16f, ink, true))
            empty.addView(verticalGap(6))
            empty.addView(textLine(
                "Записи появятся после завершения или удаления события.",
                13f, muted))
            contentContainer.addView(empty)
            return
        }
        val rows = mutableListOf<View>()
        for (entry in entries) {
            val event = entry.event
            val expanded = expandedArchiveEventId == entry.archiveId
            val status = if (entry.reason == "completed")
                "Событие завершено" else "Удалено вручную"
            val glyph = event.iconId ?: IconRegistry.select(event.title)
            rows.add(settingsRow(glyph, event.title,
                eventDateLabel(event.dueAt) + " · " + status) {
                val scroll = dashboardScroll.scrollY
                expandedArchiveEventId = if (expanded) null else entry.archiveId
                renderTab()
                dashboardScroll.post {
                    if (currentTab == 3 && eventArchiveExpanded)
                        dashboardScroll.scrollTo(0, scroll)
                }
            })
            if (expanded) {
                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(8), dp(14), dp(14))
                    addView(textLine(status + " · " +
                        eventRepeatLabel(event.recurrence), 12f, accent, true))
                    addView(verticalGap(5))
                    val lead = when (event.leadMinutes) {
                        0 -> "В момент события"
                        10 -> "За 10 минут"
                        60 -> "За 1 час"
                        1_440 -> "За 1 день"
                        4_320 -> "За 3 дня"
                        else -> "За " + event.leadMinutes + " минут"
                    }
                    addView(textLine(lead + " · в архиве с " +
                        DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(Date(entry.archivedAt)), 12f, muted))
                    if (event.details.isNotBlank()) {
                        addView(verticalGap(7))
                        addView(textLine(event.details, 13f, muted))
                    }
                    addView(verticalGap(12))
                    val stack = resources.configuration.screenWidthDp < 365
                    val actions = LinearLayout(this@MainActivity).apply {
                        orientation = if (stack) LinearLayout.VERTICAL
                            else LinearLayout.HORIZONTAL
                    }
                    actions.addView(secondaryButton("Восстановить") {
                        restoreArchivedEvent(entry)
                    }, if (stack) LinearLayout.LayoutParams(-1, dp(46))
                        else LinearLayout.LayoutParams(0, dp(46), 1f))
                    actions.addView(View(this@MainActivity),
                        LinearLayout.LayoutParams(if (stack) 1 else dp(8),
                            if (stack) dp(8) else 1))
                    actions.addView(secondaryButton("Удалить навсегда") {
                        deleteArchivedEvent(entry)
                    }, if (stack) LinearLayout.LayoutParams(-1, dp(46))
                        else LinearLayout.LayoutParams(0, dp(46), 1f))
                    addView(actions)
                }
                rows.add(details)
            }
        }
        contentContainer.addView(settingsPanel(*rows.toTypedArray()))
        contentContainer.addView(verticalGap(18))
    }

    private fun restoreArchivedEvent(entry: ArchivedEvent) {
        val day = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        themedDialogBuilder()
            .setTitle("Восстановить событие")
            .setMessage("Выберите новую дату события, чтобы снова получать напоминания.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Выбрать дату") { _, _ ->
                showThemedCalendar(day) {
                    val restored = EventStore.restore(
                        this, entry.archiveId, day.timeInMillis)
                    if (restored != null) {
                        EventScheduler.schedule(this, restored)
                        requestNotificationPermissionIfNeeded()
                        Toast.makeText(this, "Событие восстановлено",
                            Toast.LENGTH_SHORT).show()
                        renderTab()
                    } else Toast.makeText(this, "Выберите будущую дату",
                        Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun deleteArchivedEvent(entry: ArchivedEvent) {
        themedDialogBuilder()
            .setTitle("Удалить навсегда?")
            .setMessage("«" + entry.event.title +
                "» будет удалено из архива без возможности восстановления.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить навсегда") { _, _ ->
                if (EventStore.deleteArchived(this, entry.archiveId)) renderTab()
                else Toast.makeText(this, "Не удалось удалить запись",
                    Toast.LENGTH_LONG).show()
            }.show()
    }

    private fun completeEvent(event: PlannedEvent) {
        val finished = EventStore.archive(this, event.id, "completed",
            nextForRepeated = true)
        if (finished != null) {
            EventScheduler.cancel(this, event.id)
            EventStore.active(this).firstOrNull { it.id == event.id }?.let {
                EventScheduler.schedule(this, it)
            }
            expandedEventId = null
            renderTab()
        }
    }

    private fun moveEventToArchive(event: PlannedEvent) {
        themedDialogBuilder().setTitle("В архив событий?")
            .setMessage("Событие будет сохранено в архиве. Его можно восстановить.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("В архив") { _, _ ->
                if (EventStore.archive(this, event.id, "deleted") != null) {
                    EventScheduler.cancel(this, event.id)
                    expandedEventId = null
                    renderTab()
                }
            }.show()
    }


    private fun installCustomEventControls(
        form: LinearLayout, recurrence: Spinner, initial: EventPlan?,
        startAt: Calendar, onChange: (EventPlan) -> Unit
    ) {
        var mode = initial?.mode ?: "weekdays"
        val days = initial?.weekdays?.toMutableSet() ?: linkedSetOf()
        val selected = initial?.explicitDates?.toMutableSet() ?: linkedSetOf()
        // Existing custom plans retain their original interval; newly created
        // weekday schedules always repeat weekly after removing the interval UI.
        val weeks = initial?.intervalWeeks ?: 1
        var endsAt = initial?.endsAt
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(softAccent, 12)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val modeSpinner = styledSpinner(listOf("По дням недели", "Конкретные даты"),
            if (mode == "dates") 1 else 0, horizontalInsetDp = 6)
        val weekdayPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val datePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dateText = customRecurrenceCaption(horizontalInsetDp = 6)

        val endText = customRecurrenceCaption(horizontalInsetDp = 6)
        fun value() = EventPlan(mode, days.toSet(), weeks.coerceIn(1, 104),
            selected.toSet(), endsAt,
            initial?.anchorAt?.takeIf { it > 0 } ?: startAt.timeInMillis,
            initial?.edits ?: emptyMap(), initial?.history ?: emptyList())
        fun refresh() {
            weekdayPanel.visibility = if (mode == "weekdays") View.VISIBLE else View.GONE
            datePanel.visibility = if (mode == "dates") View.VISIBLE else View.GONE
            dateText.text = if (selected.isEmpty()) "Даты не выбраны"
                else selected.sorted().joinToString(", ") {
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it))
                }
            endText.text = endsAt?.let {
                "Дата окончания: " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(it))
            } ?: "Без даты окончания"
            onChange(value())
        }
        val weekdayRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        weekdayRow.addView(customRecurrenceCaption("Выберите дни недели", horizontalInsetDp = 6))
        weekdayRow.addView(verticalGap(8))
        val columns = recurringChoiceRows(
            listOf(
                "Пн" to Calendar.MONDAY, "Вт" to Calendar.TUESDAY,
                "Ср" to Calendar.WEDNESDAY, "Чт" to Calendar.THURSDAY,
                "Пт" to Calendar.FRIDAY, "Сб" to Calendar.SATURDAY,
                "Вс" to Calendar.SUNDAY
            ), 4, "День недели", days
        ) { day, enabled ->
            if (enabled) days.add(day) else days.remove(day)
            refresh()
        }
        weekdayRow.addView(columns)
        weekdayRow.addView(verticalGap(10))
        weekdayPanel.addView(weekdayRow)
        datePanel.addView(dateText)
        datePanel.addView(secondaryButton("＋ Добавить дату") {
            val date = Calendar.getInstance()
            showThemedCalendar(date) {
                selected.add(PaymentOccurrenceEngine.day(date.timeInMillis))
                refresh()
            }
        })
        datePanel.addView(verticalGap(5))
        datePanel.addView(secondaryButton("Очистить выбранные даты") {
            selected.clear()
            refresh()
        })
        panel.addView(modeSpinner)
        panel.addView(verticalGap(8))
        panel.addView(weekdayPanel)
        panel.addView(datePanel)
        panel.addView(verticalGap(8))
        panel.addView(endText)
        panel.addView(secondaryButton("Дата окончания") {
            val end = Calendar.getInstance().apply {
                timeInMillis = endsAt ?: System.currentTimeMillis()
            }
            showThemedCalendar(end) {
                endsAt = PaymentOccurrenceEngine.day(end.timeInMillis)
                refresh()
            }
        })
        panel.addView(verticalGap(4))
        panel.addView(secondaryButton("Без ограничения срока") {
            endsAt = null
            refresh()
        })
        modeSpinner.onItemSelectedListener = object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?,
                view: View?, position: Int, id: Long) {
                mode = if (position == 1) "dates" else "weekdays"
                refresh()
            }
        }
        recurrence.onItemSelectedListener = object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?,
                view: View?, position: Int, id: Long) {
                panel.visibility = if (position == 5) View.VISIBLE else View.GONE
            }
        }
        form.addView(panel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        panel.visibility = if (recurrence.selectedItemPosition == 5)
            View.VISIBLE else View.GONE
        refresh()
    }

    private fun showEventEditor(original: PlannedEvent? = null) {
        val date = if (original == null) ReminderDateTimePolicy.atOpening()
            else Calendar.getInstance().apply { timeInMillis = original.dueAt }
        var manualIcon = original?.iconId
        var editedEventPlan: EventPlan? = original?.plan
        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(12))
        }
        val name = field("Название события").apply {
            setText(original?.title.orEmpty())
        }
        val details = field("Описание (необязательно)").apply {
            setText(original?.details.orEmpty())
            minLines = 2
        }
        val iconRow = iconSelector(name, "calendar-days", manualIcon) {
            manualIcon = it
        }
        val dateButton = secondaryButton("Дата: " +
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.time)) {}
        val timeButton = secondaryButton("Время: " +
            java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(date.time)) {}
        dateButton.setOnClickListener {
            val hour = date.get(Calendar.HOUR_OF_DAY)
            val minute = date.get(Calendar.MINUTE)
            showThemedCalendar(date) {
                date.set(Calendar.HOUR_OF_DAY, hour)
                date.set(Calendar.MINUTE, minute)
                dateButton.text = "Дата: " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.time)
            }
        }
        timeButton.setOnClickListener {
            BalanceTimePicker.show(this, date.get(Calendar.HOUR_OF_DAY),
                date.get(Calendar.MINUTE)) { hour, minute ->
                date.set(Calendar.HOUR_OF_DAY, hour)
                date.set(Calendar.MINUTE, minute)
                date.set(Calendar.SECOND, 0)
                date.set(Calendar.MILLISECOND, 0)
                timeButton.text = "Время: " +
                    java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(date.time)
            }
        }
        val recurrenceValues = listOf("once", "daily", "weekly", "monthly", "yearly", "custom")
        val recurrence = styledSpinner(listOf(
            "Не повторяется", "Ежедневно", "Еженедельно", "Ежемесячно", "Ежегодно",
            "Пользовательское"
        ), recurrenceValues.indexOf(original?.recurrence ?: "once").coerceAtLeast(0))
        val leadValues = listOf(0, 10, 60, 1_440, 4_320)
        val lead = styledSpinner(listOf(
            "В момент события", "За 10 минут", "За 1 час", "За 1 день", "За 3 дня"
        ), leadValues.indexOf(original?.leadMinutes ?: 60).coerceAtLeast(0))
        editor.addView(formFieldCaption("Название"))
        editor.addView(name)
        editor.addView(verticalGap(10))
        editor.addView(formFieldCaption("Иконка"))
        editor.addView(iconRow)
        editor.addView(verticalGap(10))
        editor.addView(formFieldCaption("Описание"))
        editor.addView(details)
        editor.addView(verticalGap(12))
        val dateTime = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dateTime.addView(dateButton, LinearLayout.LayoutParams(0, dp(48), 3f))
        dateTime.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        dateTime.addView(timeButton, LinearLayout.LayoutParams(0, dp(48), 2f))
        editor.addView(dateTime)
        editor.addView(verticalGap(12))
        editor.addView(formFieldCaption("Повторение"))
        editor.addView(verticalGap(6))
        editor.addView(recurrence)
        installCustomEventControls(editor, recurrence, original?.plan, date) {
            editedEventPlan = it
        }
        editor.addView(verticalGap(12))
        editor.addView(formFieldCaption("Напомнить"))
        editor.addView(lead)
        val dialog = themedDialogBuilder()
            .setTitle(if (original == null) "Новое событие" else "Изменить событие")
            .setView(android.widget.ScrollView(this).apply { addView(editor) })
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(dialogGradient())
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ink)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val title = name.text.toString().trim()
                if (title.isEmpty() || title.length > 150 ||
                    date.timeInMillis <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Укажите название и будущую дату события",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val cycle = recurrenceValues[recurrence.selectedItemPosition]
                val plan = editedEventPlan
                if (cycle == "custom" &&
                    (plan == null || (plan.mode == "weekdays" && plan.weekdays.isEmpty()) ||
                        (plan.mode == "dates" && plan.explicitDates.isEmpty()))) {
                    Toast.makeText(this, "Выберите дни недели или конкретные даты",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                var item = PlannedEvent(original?.id ?: UUID.randomUUID().toString(),
                    title, details.text.toString().trim().take(2_000),
                    date.timeInMillis, leadValues[lead.selectedItemPosition],
                    cycle, manualIcon,
                    plan?.copy(anchorAt = original?.plan?.anchorAt
                        ?.takeIf { it > 0 } ?: date.timeInMillis))
                if (cycle == "custom") {
                    val next = EventOccurrenceEngine.next(item,
                        System.currentTimeMillis() - 1L)?.dueAt
                    if (next == null) {
                        Toast.makeText(this, "Нет будущих посещений по этому расписанию",
                            Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    item = item.copy(dueAt = next)
                }
                if (original != null) {
                    themedDialogBuilder().setTitle("Применить изменения?")
                        .setMessage("Новое расписание заменит общее. Индивидуальные " +
                            "пропуски и переносы сохранятся. Подтвердите применение.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Применить") { _, _ ->
                            if (EventStore.save(this, item)) {
                                EventScheduler.cancel(this, original.id)
                                EventScheduler.schedule(this, item)
                                expandedEventId = item.id
                                renderTab()
                                dialog.dismiss()
                            } else Toast.makeText(this, "Не удалось сохранить событие",
                                Toast.LENGTH_LONG).show()
                        }.show()
                    return@setOnClickListener
                }
                if (EventStore.save(this, item)) {
                    if (original != null) EventScheduler.cancel(this, original.id)
                    EventScheduler.schedule(this, item)
                    expandedEventId = item.id
                    renderTab()
                    dialog.dismiss()
                    requestNotificationPermissionIfNeeded()
                } else {
                    Toast.makeText(this, "Не удалось сохранить событие",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    /**
     * GitHub is checked off the UI thread; manual checks do not depend on the
     * background notification toggle.
     */
    private fun checkGithubUpdate(manual: Boolean, offer: Boolean) {
        if (checkingGithubUpdates) return
        checkingGithubUpdates = true
        if (manual) Toast.makeText(this,
            "Проверяем последние релизы GitHub…", Toast.LENGTH_SHORT).show()
        GithubUpdateEngine.checkAsync(this, manual) { release, error ->
            checkingGithubUpdates = false
            if (isFinishing || isDestroyed) return@checkAsync
            if (currentTab == 4 && !bankSettingsOpen && !mobileSettingsOpen) renderTab()
            if (release != null) {
                GithubUpdateEngine.announceIfNew(this, release)
                if (offer && (manual || intent.getBooleanExtra("show_update", false) ||
                        GithubUpdateEngine.shouldOfferInApp(this, release))) {
                    GithubUpdateEngine.markInAppOffered(this, release)
                    if (welcomeShowing) deferredUpdate = release
                    else showGithubUpdateDialog(release)
                }
            } else if (manual) {
                Toast.makeText(this,
                    error ?: "У вас уже установлена актуальная версия.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /** DownloadManager keeps working when the app is backgrounded or closed. */
    private fun showGithubUpdateDialog(release: UpdateRelease) {
        if (updateDialogOpen || isFinishing) return
        updateDialogOpen = true
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(9), dp(17), dp(13))
        }
        content.addView(textLine("Доступна версия " + release.tag,
            19f, ink, true))
        content.addView(verticalGap(8))
        content.addView(textLine(
            "Новая версия BalanceHub готова к установке. " +
                "Посмотрите изменения и скачайте проверенный APK прямо здесь.",
            13f, muted))
        if (release.notes.isNotBlank()) {
            content.addView(verticalGap(12))
            content.addView(textLine("Что нового", 15f, ink, true))
            content.addView(verticalGap(5))
            content.addView(textLine(release.notes, 13f, muted))
        }
        content.addView(verticalGap(12))
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            .apply { max = 100; progress = 0 }
        content.addView(progress)
        val statusText = textLine("Обновление ещё не скачано.", 12f, muted)
        content.addView(verticalGap(6))
        content.addView(statusText)
        content.addView(verticalGap(12))
        val button = primaryButton("Скачать обновление") {}
        content.addView(button, LinearLayout.LayoutParams(-1, dp(50)))
        content.addView(verticalGap(8))
        content.addView(textLine(
            "После проверки файла Android запросит разрешение на установку. " +
                "Текущие данные и настройки сохранятся.",
            12f, muted))
        val dialog = themedDialogBuilder().setTitle("Обновление BalanceHub")
            .setView(android.widget.ScrollView(this).apply {
                addView(content)
            }).setNegativeButton("Позже", null).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(dialogGradient())
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ink)
        }
        dialog.setOnDismissListener { updateDialogOpen = false }
        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                if (!dialog.isShowing || isFinishing) return
                val state = try { GithubApkInstaller.status(this@MainActivity, release) }
                    catch (_: Exception) { null }
                when (state?.status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> {
                        val pct = if (state.total > 0) {
                            (100 * state.received / state.total).toInt().coerceIn(0, 100)
                        } else 0
                        progress.isIndeterminate = state.total <= 0
                        progress.progress = pct
                        statusText.text = if (state.total > 0)
                            "Загрузка: $pct%" else "Загрузка обновления…"
                        button.text = "Загрузка…"
                        button.isEnabled = false
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        progress.isIndeterminate = false
                        progress.progress = 100
                        statusText.text = "APK загружен. Осталось проверить и установить."
                        button.text = "Проверить и установить"
                        button.isEnabled = true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        progress.isIndeterminate = false
                        progress.progress = 0
                        statusText.text = "Загрузка не удалась. Можно повторить."
                        button.text = "Повторить загрузку"
                        button.isEnabled = true
                    }
                    else -> {
                        progress.isIndeterminate = false
                        progress.progress = 0
                        statusText.text = "Размер обновления: " +
                            (release.bytes / 1_000_000f).toString().take(4) + " МБ"
                        button.text = "Скачать обновление"
                        button.isEnabled = true
                    }
                }
                handler.postDelayed(this, 950L)
            }
        }
        button.setOnClickListener {
            val state = GithubApkInstaller.status(this, release)
            if (state?.status == DownloadManager.STATUS_SUCCESSFUL) {
                tryInstallUpdate(release)
            } else {
                try {
                    GithubApkInstaller.begin(this, release)
                    statusText.text = "Загрузка запущена. Можно закрыть окно."
                    button.isEnabled = false
                } catch (e: Exception) {
                    statusText.text = "Не удалось начать загрузку: " +
                        (e.localizedMessage ?: "ошибка")
                }
            }
        }
        dialog.show()
        handler.post(ticker)
    }

    private fun tryInstallUpdate(release: UpdateRelease) {
        // PackageManager and SHA-256 checks may be expensive; never block UI.
        Thread({
            val file = try { GithubApkInstaller.verifiedApk(this, release) }
            catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Проверка APK не пройдена: " +
                        (e.localizedMessage ?: "ошибка"), Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!GithubApkInstaller.allowedToInstall(this)) {
                    pendingGithubInstall = true
                    themedDialogBuilder().setTitle("Разрешение на установку")
                        .setMessage("Android должен разрешить BalanceHub устанавливать " +
                            "скачанные APK. Откройте системные настройки, " +
                            "разрешите установку и вернитесь в приложение.")
                        .setNegativeButton("Позже", null)
                        .setPositiveButton("Открыть настройки") { _, _ ->
                            try {
                                startActivity(GithubApkInstaller.installPermissionIntent(this))
                            } catch (_: Exception) {
                                pendingGithubInstall = false
                                Toast.makeText(this, "Системные настройки недоступны",
                                    Toast.LENGTH_LONG).show()
                            }
                        }.show()
                    return@runOnUiThread
                }
                try {
                    startActivity(GithubApkInstaller.installerIntent(this, file))
                } catch (_: Exception) {
                    Toast.makeText(this,
                        "Не удалось открыть системный установщик APK",
                        Toast.LENGTH_LONG).show()
                }
            }
        }, "BalanceHub-APK-verify").start()
    }

    /** One warm, version-aware welcome after upgrading; never on every launch. */
    private fun showWhatsNewIfNeeded() {
        if (welcomeShowing || tutorialStep >= 0 || isFinishing) return
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        val previous = preferences.getLong("release_welcome_version", 0L)
        if (previous >= versionCode) return
        // Never reuse the notes of an older APK for a newer/unknown version.
        val notes = AppReleaseNotes.forVersion(packageInfo.versionName)
        if (notes.isEmpty()) {
            preferences.edit().putLong("release_welcome_version", versionCode).apply()
            maybeOfferAppLock()
            return
        }
        welcomeShowing = true
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        body.addView(textLine("BalanceHub обновлён!", 21f, ink, true))
        body.addView(verticalGap(8))
        body.addView(textLine(
            "Коротко о том, что изменилось в этой версии:",
            14f, muted
        ))
        body.addView(verticalGap(9))
        body.addView(textLine(
            "Что нового в версии " + (packageInfo.versionName ?: ""), 15f, ink, true
        ))
        for (note in notes) {
            body.addView(verticalGap(12))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(iconTile(note.iconId, 42),
                    LinearLayout.LayoutParams(dp(42), dp(42)))
                addView(View(this@MainActivity),
                    LinearLayout.LayoutParams(dp(11), 1))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(textLine(note.title, 14f, ink, true))
                    if (note.description.isNotBlank()) {
                        addView(verticalGap(3))
                        addView(textLine(note.description, 12f, muted))
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            body.addView(row)
        }
        body.addView(verticalGap(10))
        val dialog = themedDialogBuilder().setView(android.widget.ScrollView(this).apply {
            addView(body)
        }).setPositiveButton("Начать пользоваться", null).create()
        dialog.setOnDismissListener {
            // Mark as shown even if the user closes the window with Back.
            preferences.edit().putLong("release_welcome_version", versionCode).apply()
            welcomeShowing = false
            if (!maybeOfferAppLock()) showDeferredAppLockUpdate()
        }
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    private fun openNotificationTarget(incoming: Intent) {
        when (incoming.getIntExtra("open_tab", -1)) {
            2 -> {
                val id = incoming.getStringExtra("open_payment_id")
                if (id != null && payments.any { it.id == id }) {
                    archiveOpen = false
                    expandedPaymentId = id
                    upcomingListExpanded = true
                    if (currentTab == 2) renderTab() else selectTab(2)
                    window.decorView.post {
                        payments.firstOrNull { it.id == id }?.let(::showPaymentSchedule)
                    }
                }
            }
            3 -> {
                val id = incoming.getStringExtra("open_event_id")
                if (id != null && EventStore.active(this).any { it.id == id }) {
                    eventArchiveExpanded = false
                    expandedEventId = id
                    eventListExpanded = true
                    if (currentTab == 3) renderTab() else selectTab(3)
                    window.decorView.post {
                        EventStore.active(this).firstOrNull { it.id == id }
                            ?.let(::showEventSchedule)
                    }
                }
            }
            4 -> {
                if (currentTab != 4) selectTab(4)
                if (incoming.getBooleanExtra("show_update", false)) {
                    GithubUpdateEngine.cached(this)?.let { showGithubUpdateDialog(it) }
                        ?: checkGithubUpdate(manual = true, offer = true)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (appLockUnlocked) openNotificationTarget(intent)
    }

    /* Navigation targets always resolve against the latest active store. */

    private fun recurrenceLabel(value: String) = when (value) {
        "weekly" -> "Каждую неделю"
        "monthly" -> "Каждый месяц"
        else -> "Единоразово"
    }

    private fun scheduleReminder(payment: PaymentReminder) {
        PaymentOccurrenceScheduler.schedule(this, payment)
    }

    private fun cancelReminder(payment: PaymentReminder) {
        PaymentOccurrenceScheduler.cancel(this, payment.id)
    }

    private fun scheduleDiagnosticReminder(category: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded()
            return
        }
        DiagnosticReminderReceiver.schedule(this, category)
        Toast.makeText(this, "Напоминание запланировано через 2 минуты",
            Toast.LENGTH_LONG).show()
        renderTab()
    }


    private fun selectDiagnosticCategory(action: (String) -> Unit) {
        val choices = listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)
        showThemedChoices("Тип уведомления",
            choices.map { notificationTitle(it) }, -1) { index ->
            action(choices[index])
        }
    }

    private fun sendVibrationTest(category: String) {
        val started = BalanceVibration.preview(this, category)
        Toast.makeText(this, if (started)
            "Команда вибромотору отправлена. Проверьте отклик устройства."
            else "Вибромотор недоступен или Android отклонил запуск",
            Toast.LENGTH_LONG).show()
    }

    private fun sendNotificationTest(category: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded()
            return
        }
        if (!androidx.core.app.NotificationManagerCompat.from(this)
                .areNotificationsEnabled()) {
            Toast.makeText(this, "Уведомления BalanceHub запрещены Android",
                Toast.LENGTH_LONG).show()
            openAndroidNotificationSettings()
            return
        }
        val channelId = BalanceNotificationSounds.ensureChannel(this, category)
        val system = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = system.getNotificationChannel(channelId)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                Toast.makeText(this, "Этот канал отключён в настройках Android",
                    Toast.LENGTH_LONG).show()
                openAndroidNotificationSettings(category)
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = system.getNotificationChannel(channelId)
            if (channel != null) {
                val issues = BalanceNotificationSounds.signalIssues(this, category)
                if (issues.isNotEmpty()) {
                    themedDialogBuilder()
                        .setTitle("Настройки BalanceHub и Android различаются")
                        .setMessage(issues.joinToString("\n") +
                            "\n\nВосстановление создаст новый канал только с вашего " +
                            "подтверждения. Можно отправить тест без исправления " +
                            "либо открыть системные настройки канала.")
                        .setNegativeButton("Настройки Android") { _, _ ->
                            openAndroidNotificationSettings(category)
                        }
                        .setNeutralButton("Отправить тест") { _, _ ->
                            postDiagnosticNotification(category, channelId)
                        }
                        .setPositiveButton("Восстановить") { _, _ ->
                            showChannelRepairDialog(category, testAfterRepair = true)
                        }.show()
                    return
                }
            }
        }
        postDiagnosticNotification(category, channelId)
    }

    private fun postDiagnosticNotification(category: String, channelId: String) {
        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Тест BalanceHub: " + notificationTitle(category))
            .setContentText("Проверка звука и вибрации через Android")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        BalanceNotificationSounds.configureLegacySound(this, category, builder)
        try {
            // A separate notification on every press, not an update of one test.
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(ReminderNoticeIds.test(), builder.build())
            val compatibilityVibration =
                ColorOsDirectVibration.afterNotification(this, category, channelId)
            val directReport = if (BalanceNotificationSounds.colorOsCompatibility(this))
                "Прямая вибрация: " + compatibilityVibration.name + ". "
            else ""
            val automaticReport = if (PaymentAutomaticSound.enabled(this, category)) {
                "Звук BalanceHub: " +
                    PaymentAutomaticSound.afterNotification(this, channelId,
                        BalanceNotificationSounds.selected(this, category),
                        BalanceNotificationSounds.volume(this, category),
                        category = category).name + ". "
            } else ""
            Toast.makeText(this, "Уведомление передано Android. " + directReport +
                automaticReport + "Проверьте фактический звук на устройстве.",
                Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Android отклонил отправку уведомления",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }


    /** Keep the financial task invisible in the app switcher while protection is active. */
    private fun applyAppLockWindowPrivacy() {
        if (AppLockSecurity.enabled(this) &&
            !AppLockSecurity.screenshotsAllowed(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun afterAppLockUnlocked() {
        if (!appLockUnlocked) return
        showWhatsNewIfNeeded()
        checkGithubUpdate(manual = false, offer = true)
    }

    private fun showDeferredAppLockUpdate() {
        deferredUpdate?.let {
            deferredUpdate = null
            window.decorView.post { showGithubUpdateDialog(it) }
        }
    }

    private fun maybeOfferAppLock(): Boolean {
        if (!AppLockSecurity.supported() || !appLockUnlocked || welcomeShowing ||
            tutorialStep >= 0 || AppLockSecurity.enabled(this) ||
            preferences.getBoolean("app_lock_offer_seen", false) || isFinishing) return false
        preferences.edit().putBoolean("app_lock_offer_seen", true).apply()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(16))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = "Иконка BalanceHub"
            }, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(verticalGap(12))
            addView(textLine("Защитить BalanceHub?", 21f, ink, true).apply {
                gravity = Gravity.CENTER
            })
            addView(verticalGap(10))
            addView(textLine(
                "Установите 4-значный код доступа. Дополнительно можно включить " +
                    "вход по отпечатку пальца, сохранённому в Android.",
                14f, muted).apply { gravity = Gravity.CENTER })
            addView(verticalGap(10))
            addView(textLine("Защиту можно включить позже в настройках.",
                12f, muted).apply { gravity = Gravity.CENTER })
        }
        val dialog = themedDialogBuilder().setView(body)
            .setNegativeButton("Не сейчас") { _, _ -> showDeferredAppLockUpdate() }
            .setPositiveButton("Установить код") { _, _ -> beginPinCreation() }
            .create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
        return true
    }

    private fun lockErrorMessage(result: AppLockSecurity.PinResult): String = when (result) {
        AppLockSecurity.PinResult.WRONG -> {
            val delay = AppLockSecurity.remainingLockout(this)
            if (delay > 0L) "Слишком много попыток. Подождите " +
                ((delay + 999L) / 1000L) + " сек." else "Неверный код. Попробуйте ещё раз."
        }
        AppLockSecurity.PinResult.BLOCKED ->
            "Подождите " + ((AppLockSecurity.remainingLockout(this) + 999L) / 1000L) + " сек."
        AppLockSecurity.PinResult.ERROR -> "Не удалось проверить код доступа."
        AppLockSecurity.PinResult.SUCCESS -> ""
    }

    private fun showLockedScreen(message: String = "",
        automaticFingerprint: Boolean = true) {
        if (!AppLockSecurity.enabled(this)) return
        appLockUnlocked = false
        applyAppLockWindowPrivacy()
        showPinPad("Введите код доступа",
            if (message.isNotBlank()) message else "Разблокируйте BalanceHub",
            fingerprint = AppLockSecurity.fingerprintEnabled(this),
            cancel = null) { pin ->
            when (val result = AppLockSecurity.verifyPin(this, pin)) {
                AppLockSecurity.PinResult.SUCCESS -> finishAppUnlock()
                else -> showLockedScreen(lockErrorMessage(result), automaticFingerprint = false)
            }
        }
        // The PIN pad is already present: any fingerprint failure or cancellation
        // returns directly to it without re-triggering biometric authentication.
        if (automaticFingerprint && AppLockSecurity.fingerprintEnabled(this)) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed && appLockMandatoryScreen &&
                    appLockScreenVisible && !appLockUnlocked) {
                    promptFingerprint(enrolling = false, fallbackOnMismatch = true) {
                        finishAppUnlock()
                    }
                }
            }
        }
    }

    private fun finishAppUnlock() {
        fingerprintSignal?.cancel()
        fingerprintSignal = null
        appLockUnlocked = true
        appLockScreenVisible = false
        appLockMandatoryScreen = false
        appLockBackgroundAt = 0L
        appLockScreenTurnedOff = false
        // Reconcile events and reminders missed while the UI was locked.
        PaymentArchiveStore.archiveDue(this)
        val archivedIds = PaymentArchiveStore.archived(this).map { it.id }.toSet()
        if (archivedIds.isNotEmpty()) payments.removeAll { it.id in archivedIds }
        val beforeEvents = EventStore.active(this).associate { it.id to it.dueAt }
        EventStore.reconcileElapsed(this)
        val exact = ExactReminderAlarms.allowed(this)
        val permissionChanged = lastExactAlarmState != exact
        lastExactAlarmState = exact
        if (permissionChanged) payments.forEach(::scheduleReminder)
        for (event in EventStore.active(this)) {
            if (permissionChanged || beforeEvents[event.id] != event.dueAt)
                EventScheduler.schedule(this, event)
        }
        if (pendingGithubInstall && GithubApkInstaller.allowedToInstall(this)) {
            pendingGithubInstall = false
            GithubUpdateEngine.cached(this)?.let { tryInstallUpdate(it) }
        }
        showDashboard()
        openNotificationTarget(intent)
        if (currentTab == 4) dashboardScroll.post {
            dashboardScroll.scrollTo(0, settingsScrollPositions[settingsScreen()] ?: 0)
        }
        window.decorView.post { afterAppLockUnlocked() }
    }

    /** Real app icon and exact BalanceHub gradients; no credential input from Android UI. */
    private fun showPinPad(
        title: String, subtitle: String, fingerprint: Boolean = false,
        cancel: (() -> Unit)? = null, onPin: (String) -> Unit
    ) {
        renderedWinter = winterActive
        appLockScreenVisible = true
        appLockMandatoryScreen = cancel == null &&
            AppLockSecurity.enabled(this) && !appLockUnlocked
        fingerprintSignal?.cancel()
        fingerprintSignal = null
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), headerTopPadding, dp(24), dp(20))
            background = screenGradient()
        }
        val scroller = android.widget.ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        body.addView(textLine(if (cancel == null) "BalanceHub" else "‹  Назад",
            17f, accent, true).apply {
            gravity = Gravity.CENTER
            if (cancel != null) setOnClickListener { cancel() }
        }, LinearLayout.LayoutParams(-1, dp(44)))
        body.addView(verticalGap(10))
        body.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "Иконка BalanceHub"
        }, LinearLayout.LayoutParams(dp(76), dp(76)))
        body.addView(verticalGap(17))
        body.addView(textLine(title, 22f, ink, true).apply {
            gravity = Gravity.CENTER
        })
        body.addView(verticalGap(8))
        val hint = textLine(subtitle, 14f, muted).apply { gravity = Gravity.CENTER }
        body.addView(hint)
        body.addView(verticalGap(23))

        val indicators = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        val dots = (0 until 4).map {
            textLine("○", 28f, accent, true).apply {
                gravity = Gravity.CENTER
                indicators.addView(this, LinearLayout.LayoutParams(dp(32), dp(38)))
            }
        }
        body.addView(indicators)
        body.addView(verticalGap(25))

        val keyboard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(if (fingerprint) "Отпечаток" else "", "0", "⌫"))
        val input = StringBuilder()
        var processing = false
        fun updateDots() {
            dots.forEachIndexed { index, dot ->
                dot.text = if (index < input.length) "●" else "○"
            }
        }
        rows.forEach { values ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            values.forEach { value ->
                val key = textLine(
                    if (value == "Отпечаток") "◎" else value,
                    if (value == "Отпечаток") 30f else 25f, ink, true
                ).apply {
                    gravity = Gravity.CENTER
                    background = cardGradient()
                    isClickable = value.isNotEmpty()
                    isFocusable = value.isNotEmpty()
                    contentDescription = if (value == "Отпечаток")
                        "Разблокировать отпечатком пальца" else value
                    setOnClickListener {
                        if (processing) return@setOnClickListener
                        when (value) {
                            "Отпечаток" -> promptFingerprint(enrolling = false,
                                fallbackOnMismatch = true) { finishAppUnlock() }
                            "⌫" -> {
                                if (input.isNotEmpty()) input.deleteCharAt(input.length - 1)
                                updateDots()
                            }
                            else -> {
                                if (value.isNotEmpty() && input.length < 4) {
                                    input.append(value)
                                    updateDots()
                                    if (input.length == 4) {
                                        processing = true
                                        val entered = input.toString()
                                        post { onPin(entered) }
                                    }
                                }
                            }
                        }
                    }
                }
                row.addView(key, LinearLayout.LayoutParams(0, dp(63), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            keyboard.addView(row)
        }
        body.addView(keyboard, LinearLayout.LayoutParams(-1, -2))
        body.addView(verticalGap(12))
        if (cancel == null) {
            body.addView(textLine("Забыли код?", 13f, accent, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                isClickable = true
                setOnClickListener {
                    themedDialogBuilder().setTitle("Забыли код доступа?")
                        .setMessage("Если вход по отпечатку включён, разблокируйте " +
                            "приложение отпечатком и восстановите код в настройках " +
                            "безопасности. Если оба способа недоступны, восстановить " +
                            "доступ можно только через очистку данных приложения; " +
                            "при этом удаляются локальные платежи и настройки.")
                        .setPositiveButton("Понятно", null).show()
                }
            })
        }
        body.addView(textLine(if (cancel == null) "Закрыть приложение" else "Отмена",
            13f, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(10), dp(6), dp(10))
            isClickable = true
            setOnClickListener {
                if (cancel != null) cancel() else moveTaskToBack(true)
            }
        })
        scroller.addView(body)
        main.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(main)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = backdrop
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }
    }

    private fun beginPinCreation() {
        showPinPad("Создайте код доступа", "Придумайте код из четырёх цифр",
            cancel = { restoreAfterSecurityFlow() }) { first ->
            showPinPad("Повторите код", "Введите те же четыре цифры",
                cancel = { beginPinCreation() }) { second ->
                if (first != second) {
                    showPinPad("Коды не совпадают", "Попробуйте создать код ещё раз",
                        cancel = { restoreAfterSecurityFlow() }) { retry ->
                        showPinPad("Повторите код", "Подтвердите новый код",
                            cancel = { beginPinCreation() }) { again ->
                            completePinCreation(retry, again)
                        }
                    }
                } else completePinCreation(first, second)
            }
        }
    }

    private fun completePinCreation(first: String, second: String) {
        if (AppLockSecurity.enabled(this) && !appLockUnlocked) {
            showLockedScreen()
            return
        }
        if (first != second) {
            Toast.makeText(this, "Коды не совпадают. Повторите установку.",
                Toast.LENGTH_LONG).show()
            beginPinCreation()
            return
        }
        if (!AppLockSecurity.savePin(this, first)) {
            Toast.makeText(this, "Не удалось безопасно сохранить код.",
                Toast.LENGTH_LONG).show()
            restoreAfterSecurityFlow()
            return
        }
        appLockUnlocked = true
        appLockScreenVisible = false
        applyAppLockWindowPrivacy()
        showDashboard()
        themedDialogBuilder()
            .setTitle("Защита включена")
            .setMessage("Код доступа установлен. Включить вход по отпечатку пальца Android?")
            .setNegativeButton("Позже", null)
            .setPositiveButton("Включить") { _, _ ->
                if (AppLockSecurity.fingerprintAvailable(this)) {
                    promptFingerprint(enrolling = true) {
                        refreshSecuritySettings()
                        Toast.makeText(this, "Отпечаток пальца включён",
                            Toast.LENGTH_SHORT).show()
                    }
                } else themedDialogBuilder()
                    .setTitle("Отпечаток недоступен")
                    .setMessage("Добавьте отпечаток в настройках Android и включите его " +
                        "позже через Безопасность.")
                    .setPositiveButton("Понятно", null).show()
            }.show()
    }

    private fun restoreAfterSecurityFlow() {
        if (AppLockSecurity.enabled(this) && !appLockUnlocked) {
            // Cancel/back after a background lock must never reopen financial data.
            showLockedScreen()
            return
        }
        appLockScreenVisible = false
        appLockMandatoryScreen = false
        appLockUnlocked = true
        applyAppLockWindowPrivacy()
        showDashboard()
    }

    private fun confirmExistingPin(action: () -> Unit) {
        showPinPad("Подтвердите код", "Введите действующий код BalanceHub",
            cancel = { restoreAfterSecurityFlow() }) { pin ->
            when (val result = AppLockSecurity.verifyPin(this, pin)) {
                AppLockSecurity.PinResult.SUCCESS -> action()
                else -> showPinPad("Подтвердите код", lockErrorMessage(result),
                    cancel = { restoreAfterSecurityFlow() }) { retry ->
                    when (val r = AppLockSecurity.verifyPin(this, retry)) {
                        AppLockSecurity.PinResult.SUCCESS -> action()
                        else -> {
                            Toast.makeText(this, lockErrorMessage(r), Toast.LENGTH_LONG).show()
                            confirmExistingPin(action)
                        }
                    }
                }
            }
        }
    }

    private fun refreshSecuritySettings() {
        if (!appLockUnlocked && AppLockSecurity.enabled(this)) {
            // A failed or invalidated fingerprint must never restore the dashboard.
            showLockedScreen("Используйте код BalanceHub для входа")
        } else if (appLockScreenVisible) restoreAfterSecurityFlow()
        else if (currentTab == 4) renderTab()
    }

    @Suppress("DEPRECATION")
    private fun promptFingerprint(enrolling: Boolean,
        fallbackOnMismatch: Boolean = false, success: () -> Unit) {
        if (!AppLockSecurity.fingerprintAvailable(this)) {
            if (!fallbackOnMismatch) Toast.makeText(this,
                "Отпечаток пальца недоступен в Android", Toast.LENGTH_LONG).show()
            return
        }
        var complete = false
        var signal: CancellationSignal? = null
        val dialog = themedDialogBuilder()
            .setTitle("Отпечаток пальца")
            .setMessage("Приложите палец к системному сканеру Android.")
            .setNegativeButton(if (enrolling) "Отмена" else "Ввести код", null)
            .create()
        fingerprintDialog = dialog
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.setOnDismissListener {
            if (!complete) signal?.cancel()
            if (fingerprintDialog === dialog) fingerprintDialog = null
            if (fingerprintSignal === signal) fingerprintSignal = null
        }
        dialog.show()
        signal = AppLockSecurity.authenticateFingerprint(this, enrolling,
            onSuccess = {
                if (!complete && dialog.isShowing) {
                    complete = true
                    dialog.dismiss()
                    success()
                }
            },
            onError = { error ->
                if (!complete && dialog.isShowing) {
                    complete = true
                    signal?.cancel()
                    dialog.dismiss()
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    if (!fallbackOnMismatch) refreshSecuritySettings()
                }
            },
            onMismatch = {
                if (!complete && dialog.isShowing) {
                    if (fallbackOnMismatch) {
                        complete = true
                        signal?.cancel()
                        dialog.dismiss()
                        Toast.makeText(this, "Отпечаток не распознан. Введите код.",
                            Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this, "Отпечаток не распознан",
                        Toast.LENGTH_SHORT).show()
                }
            })
        if (dialog.isShowing) fingerprintSignal = signal else signal?.cancel()
    }

    private fun renderSecuritySettings() {
        settingsHeader("Безопасность", "Код доступа и отпечаток пальца")
        if (!AppLockSecurity.supported()) {
            settingsGroup(settingsPanel(settingsRow("shield-check",
                "Доступно с Android 11", "На этом устройстве функция недоступна")))
            return
        }
        val active = AppLockSecurity.enabled(this)
        val rows = mutableListOf<View>(
            settingsSwitch("shield-check", "Защита приложения",
                "Запрашивать код доступа при открытии", active) { enable ->
                if (enable && !active) beginPinCreation()
                else if (!enable && active) confirmExistingPin {
                    AppLockSecurity.disable(this)
                    restoreAfterSecurityFlow()
                    Toast.makeText(this, "Защита отключена", Toast.LENGTH_SHORT).show()
                }
            },
            settingsSwitch("shield-check", "Разрешить снимки экрана",
                "По умолчанию при включённой защите снимки запрещены",
                AppLockSecurity.screenshotsAllowed(this)) { allowed ->
                AppLockSecurity.setScreenshotsAllowed(this, allowed)
                applyAppLockWindowPrivacy()
                renderTab()
            }
        )
        if (active) {
            rows.add(settingsRow("key", "Изменить код доступа",
                "Четырёхзначный код BalanceHub") {
                confirmExistingPin { beginPinCreation() }
            })
            rows.add(settingsSwitch("shield-check", "Вход по отпечатку пальца",
                if (AppLockSecurity.fingerprintAvailable(this))
                    "Системный отпечаток Android" else "Отпечаток в Android не настроен",
                AppLockSecurity.fingerprintEnabled(this)) { enable ->
                if (enable) {
                    confirmExistingPin {
                        restoreAfterSecurityFlow()
                        promptFingerprint(enrolling = true) {
                            refreshSecuritySettings()
                        }
                    }
                } else {
                    confirmExistingPin {
                        AppLockSecurity.disableFingerprint(this)
                        restoreAfterSecurityFlow()
                    }
                }
            })
            val options = listOf(
                AppLockPolicy.IMMEDIATE to "Сразу после сворачивания",
                AppLockPolicy.ONE_MINUTE to "Через 1 минуту",
                AppLockPolicy.FIVE_MINUTES to "Через 5 минут",
                AppLockPolicy.DEVICE_LOCK to "После блокировки устройства"
            )
            if (AppLockSecurity.fingerprintEnabled(this)) {
                rows.add(settingsRow("key", "Восстановить код по отпечатку",
                    "Если забыли код, подтвердите системный отпечаток") {
                    promptFingerprint(enrolling = false) { beginPinCreation() }
                })
            }
            rows.add(settingsRow("alarm-clock", "Повторная блокировка",
                options.firstOrNull { it.first == AppLockSecurity.relock(this) }?.second ?: "") {
                val index = options.indexOfFirst { it.first == AppLockSecurity.relock(this) }
                    .coerceAtLeast(0)
                showThemedChoices("Повторная блокировка", options.map { it.second }, index) {
                    chosen -> AppLockSecurity.setRelock(this, options[chosen].first)
                    renderTab()
                }
            })
        }
        settingsGroup(settingsPanel(*rows.toTypedArray()))
        if (active) {
            contentContainer.addView(textLine(
                "Отпечатки хранятся только в Android. При их изменении вход " +
                    "по отпечатку нужно включить повторно, подтвердив код BalanceHub.",
                12f, muted))
        }
    }

    private fun loadPayments() {
        val array = PaymentArchiveStore.activeSnapshot(this)
        if (array == null) {
            Toast.makeText(this, "Данные платежей повреждены. Исходная запись сохранена; " +
                "автоматическая перезапись запрещена.", Toast.LENGTH_LONG).show()
            return
        }
        paymentBaseline.clear()
        var advanced = false
        var invalid = 0
        val now = System.currentTimeMillis()
        for (i in 0 until array.length()) {
            val raw = array.optJSONObject(i)
            val original = raw?.let(PaymentReminderCodec::decode)
            if (original == null) {
                invalid++
                continue
            }
            paymentBaseline[original.id] = PaymentReminderCodec.encode(original).toString()
            var payment = original
            if (original.recurrence == "weekly" || original.recurrence == "monthly") {
                val due = Calendar.getInstance().apply { timeInMillis = original.dueAt }
                var iterations = 0
                while (due.timeInMillis <= now && iterations++ < 10_000) {
                    if (original.recurrence == "weekly") due.add(Calendar.DAY_OF_YEAR, 7)
                    else due.add(Calendar.MONTH, 1)
                }
                if (due.timeInMillis != original.dueAt) {
                    payment = payment.copy(dueAt = due.timeInMillis)
                    advanced = true
                }
            }
            if (payment.id.startsWith("t2_subscription_") &&
                payment.title != "Абонентская плата T2") {
                payment = payment.copy(title = "Абонентская плата T2")
                advanced = true
            }
            payments.add(payment)
        }
        // Merge only the revised recurring records, preserving unreadable rows.
        if (advanced && !persistPayments()) {
            Toast.makeText(this, "Не удалось сохранить обновлённые даты платежей",
                Toast.LENGTH_LONG).show()
        }
        if (invalid > 0) Toast.makeText(this,
            "Некоторые записи платежей не удалось прочитать; они сохранены без изменений",
            Toast.LENGTH_LONG).show()
        payments.forEach(::scheduleReminder)
    }

    private fun persistPayments(): Boolean {
        // Archive transactions and UI updates share one lock and commit atomically.
        val archivedIds = PaymentArchiveStore.archived(this).map { it.id }.toSet()
        payments.removeAll { it.id in archivedIds }
        val desired = JSONArray()
        val nextBaseline = mutableMapOf<String, String>()
        for (payment in payments) {
            val record = PaymentReminderCodec.encode(payment)
            desired.put(record)
            nextBaseline[payment.id] = record.toString()
        }
        val saved = PaymentArchiveStore.saveUiChanges(this, paymentBaseline, desired)
        if (saved) {
            paymentBaseline.clear()
            paymentBaseline.putAll(nextBaseline)
        } else Toast.makeText(this,
            "Не удалось сохранить платежи. Данные в хранилище не изменены.",
            Toast.LENGTH_LONG).show()
        return saved
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private data class HelpSection(
    val title: String,
    val subtitle: String,
    val icon: String,
    val paragraphs: List<String>
)
