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
    private var checkingGithubUpdates = false
    private var updateDialogOpen = false
    private var pendingGithubInstall = false
    private var lastExactAlarmState: Boolean? = null
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
            if (welcomeShowing) deferredUpdate = release
            else window.decorView.post { showGithubUpdateDialog(release) }
        }
    }
    private var upcomingListExpanded = true
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
    // Match the approved header position on every tab and in every theme.
    // Never derive this padding from transient window insets during recreation.
    private val headerTopPadding get() = dp(36)
    private val bankUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SberPushNotificationListener.ACTION_BALANCE_UPDATED &&
                (currentTab == 1 || (currentTab == 4 && bankSettingsOpen)) &&
                ::contentContainer.isInitialized) renderTab()
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
    private var dueDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (getSharedPreferences("balancehub_data", MODE_PRIVATE)
            .getString("appearance", "system")) {
            "day" -> AppCompatDelegate.MODE_NIGHT_NO
            "night" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        super.onCreate(savedInstanceState)
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
            ?: preferences.getBoolean("payments_list_expanded", true)
        expandedPaymentId = savedInstanceState?.getString("expanded_payment_id")
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
        showDashboard()
        if (currentTab == 4) dashboardScroll.post {
            dashboardScroll.scrollTo(0, settingsScrollPositions[settingsScreen()] ?: 0)
        }
        GithubApkInstaller.cleanOld(this)
        GithubUpdateEngine.schedule(this)
        window.decorView.post {
            showWhatsNewIfNeeded()
            checkGithubUpdate(manual = false,
                offer = true)
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
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
                    operatorDetailsOpen -> {
                        operatorDetailsOpen = false
                        renderTab()
                    }
                    bankSettingsOpen && engineeringMenuOpen && currentTab == 4 -> {
                        bankSettingsOpen = false
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
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
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
        ContextCompat.registerReceiver(this, downloadedApkReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        closeEngineeringMenu()
        BalanceNotificationSounds.stopPreview()
        unregisterReceiver(bankUpdateReceiver)
        unregisterReceiver(eventChangedReceiver)
        unregisterReceiver(paymentChangedReceiver)
        unregisterReceiver(downloadedApkReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
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
        if (::contentContainer.isInitialized &&
            (currentTab == 0 || currentTab == 1 || currentTab == 3 || currentTab == 4)) renderTab()
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
    private val ink: Int get() = if (isNight) Color.rgb(239, 249, 247) else Color.rgb(30, 57, 59)
    private val muted: Int get() = if (isNight) Color.rgb(165, 189, 191) else Color.rgb(104, 129, 130)
    private val accent: Int get() = if (isNight) Color.rgb(87, 169, 167) else Color.rgb(35, 131, 131)
    private val surface: Int get() = if (isNight) Color.rgb(32, 54, 60) else Color.WHITE
    private val backdrop: Int get() = if (isNight) Color.rgb(17, 33, 40) else Color.rgb(237, 248, 246)
    private val softAccent: Int get() = if (isNight) Color.rgb(38, 77, 78) else Color.rgb(223, 244, 239)

    private val balanceHubTypeface: Typeface by lazy {
        Typeface.createFromAsset(assets, "fonts/montserrat_bold.ttf")
    }

    private fun screenGradient() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (isNight) intArrayOf(Color.rgb(19, 42, 49), Color.rgb(17, 31, 38))
        else intArrayOf(Color.rgb(238, 250, 247), Color.rgb(245, 249, 249))
    )

    private fun cardGradient(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (isNight) intArrayOf(Color.rgb(37, 63, 67), Color.rgb(31, 48, 57))
        else intArrayOf(Color.WHITE, Color.rgb(235, 247, 245))
    ).apply {
        cornerRadius = dp(20).toFloat()
        setStroke(dp(1), if (isNight) Color.rgb(66, 102, 104)
            else Color.rgb(205, 229, 225))
    }

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

    private fun verticalGap(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = cardGradient()
        elevation = dp(2).toFloat()
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
        AlertDialog.Builder(this,
            if (isNight) R.style.BalanceHubDialogDark else R.style.BalanceHubDialogLight)

    private fun dialogGradient(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (isNight) intArrayOf(Color.rgb(36, 66, 70), Color.rgb(26, 46, 54))
        else intArrayOf(Color.WHITE, Color.rgb(233, 247, 243))
    ).apply {
        cornerRadius = dp(22).toFloat()
        setStroke(dp(1), if (isNight) Color.rgb(72, 132, 127)
            else Color.rgb(172, 217, 208))
    }

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
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = screenGradient()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), headerTopPadding, dp(20), dp(23))
            background = BalanceHubHeaderDrawable(isNight)
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
            setPadding(navSidePadding, dp(8), navSidePadding, dp(8))
            background = null
        }
        val navigation = BalanceBottomNavigation(this, isNight, currentTab) {
            selectTab(it)
        }
        bottomNavigation = navigation
        tabViews = (0 until NavigationMotionPolicy.TAB_COUNT).mapNotNull {
            navigation.section(it)
        }
        tabs.addView(navigation, LinearLayout.LayoutParams(-1, dp(64)))
        // System navigation bars/gesture handles own the bottom inset.
        // Only this outer strip receives the inset; the pill never shifts.
        ViewCompat.setOnApplyWindowInsetsListener(tabs) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(navSidePadding, dp(8), navSidePadding, bottom + dp(8))
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
            tabViews.getOrNull(1)?.apply { background = null; elevation = 0f }
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

    /** In-context coach: it never covers the actual buttons the user must tap. */
    private fun highlightTutorialTarget(target: View) {
        val gold = Color.rgb(235, 200, 94)
        target.background = GradientDrawable().apply {
            setColor(if (isNight) Color.rgb(44, 75, 67) else Color.rgb(220, 238, 225))
            cornerRadius = dp(13).toFloat()
            setStroke(dp(2), gold)
        }
        target.elevation = dp(9).toFloat()
    }

    private fun completeTutorial() {
        preferences.edit().putBoolean("onboarding_completed", true).apply()
        tutorialStep = -1
        renderTab()
    }

    private fun restartTutorial() {
        tutorialStep = 0
        operatorDetailsOpen = false
        mobileSettingsOpen = false
        if (currentTab == 0) renderTab() else selectTab(0)
    }

    private fun backTutorial() {
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
            val amount = PaymentOverview.totalLabel(
                upcomingPayments.map { it.amount to it.currency })
            addView(textLine(amount, if (amount.length > 17) 12f else 15f,
                ink, true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(verticalGap(2))
            addView(textLine("Общая сумма", 11f, muted))
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
            for (day in PaymentCalendarMath.scheduledDays(
                    payment.dueAt, payment.recurrence, year, month)) {
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
                        isNight, paymentCount > 0, eventCount > 0, chosen, isToday)
                    addView(textLine(day.toString(), 13f,
                        if (isNight && (marked || chosen)) Color.WHITE else ink,
                        chosen || marked || isToday).apply {
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    })
                    addView(textLine(
                        if (paymentCount + eventCount > 1)
                            (paymentCount + eventCount).toString()
                        else if (marked) "•" else " ",
                        9f, if (isNight && (marked || chosen)) Color.WHITE else accent,
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
                    PaymentCurrencyFormatter.amountWithCurrency(
                        nearest.amount, nearest.currency), 11f, muted))
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
                        PaymentCurrencyFormatter.amountWithCurrency(
                            payment.amount, payment.currency), 12f, ink))
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
        contentContainer.addView(verticalGap(8))
        contentContainer.addView(textLine("Мобильная связь", 14f, muted))
        contentContainer.addView(verticalGap(14))

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
            val amount = textLine(
                cached?.first?.let { it + " ₽" } ?: "Нет данных",
                if (cached == null) 15f else 22f,
                Color.rgb(23, 55, 33), true
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = rounded(
                    if (isNight) Color.rgb(191, 239, 104)
                    else Color.rgb(216, 241, 166), 14
                )
            }
            if (resources.configuration.screenWidthDp >= 395) {
                row.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
                row.addView(amount)
            }
            mobile.addView(row)
            if (resources.configuration.screenWidthDp < 395) {
                mobile.addView(verticalGap(9))
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
            if (!BankCardPreferences.enabled(this, bank.id)) false
            else if (bank.id == "sber") SberBalanceStore.latest(this) != null
            else RussianBankBalanceStore.latest(this, bank.id) != null
        }
        for (bank in orderedBanks) {
            if (bank.id == "sber") renderSberBankCard()
            else renderOtherBankBalance(bank)
        }
        if (visibleBanks.isEmpty()) {
            contentContainer.addView(textLine(
                "Все банковские карточки скрыты. Верните их через Настройки → Банки.",
                13f, muted))
            contentContainer.addView(verticalGap(12))
        }
    }

    private fun renderSberBankCard() {
        val enabled = SberBalanceStore.isEnabled(this)
        val granted = SberBalanceStore.isAccessGranted(this)
        // A masked card suffix and masked account suffix may identify the same
        // money. Do not sum them or infer equivalence from four trailing digits.
        val snapshot = if (enabled) SberBalanceStore.latest(this) else null
        val bankCard = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(sberLogo(54), LinearLayout.LayoutParams(dp(54), dp(54)))
        row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        val subtitle = when {
            !enabled -> "Push не подключён"
            !granted -> "Нет доступа к уведомлениям"
            snapshot == null -> "Ожидание банковского Push"
            snapshot.lastFour != null -> "Последний Push · ••••" + snapshot.lastFour
            else -> "Последний Push · номер не указан"
        }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine("Сбербанк", 16f, ink, true))
            addView(verticalGap(3))
            addView(textLine(subtitle, 12f, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val amount = textLine(
            snapshot?.let { rubles(it.kopecks) } ?: "Нет данных",
            if (snapshot == null) 15f else 22f,
            Color.rgb(23, 55, 33), true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(
                if (isNight) Color.rgb(191, 239, 104)
                else Color.rgb(216, 241, 166), 14
            )
        }
        // Follow the same responsive layout as the mobile operator cards.
        if (resources.configuration.screenWidthDp >= 395) {
            row.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
            row.addView(amount)
        }
        bankCard.addView(row)
        if (resources.configuration.screenWidthDp < 395) {
            bankCard.addView(verticalGap(9))
            bankCard.addView(LinearLayout(this).apply {
                gravity = Gravity.END
                addView(amount)
            })
        }
        if (snapshot != null) {
            val time = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT
            ).format(Date(snapshot.observedAt))
            bankCard.addView(verticalGap(8))
            bankCard.addView(textLine(
                "Подтверждено Push СберБанк Онлайн: " + time, 12f, muted
            ))
        } else {
            val state = when {
                !enabled -> "Подключите Push в настройках Сбербанка"
                !granted -> "Разрешите BalanceHub доступ к уведомлениям"
                SberPushDiagnostics.latest(this)?.outcome ==
                    SberPushDiagnostics.Outcome.SYSTEM_REDACTED ->
                    "Android скрыл банковский текст от BalanceHub"
                else -> "Ожидание Push с указанием остатка в рублях"
            }
            bankCard.addView(verticalGap(8))
            bankCard.addView(textLine(state, 12f, muted))
        }
        // Bank details are read-only. Connection settings remain in the engineer menu.
        bankCard.isClickable = true
        bankCard.isFocusable = true
        bankCard.contentDescription = "Подробнее о последнем балансе Сбербанка"
        bankCard.setOnClickListener { showSberBalanceDetails() }
        contentContainer.addView(bankCard)
        contentContainer.addView(verticalGap(12))
    }

    /** Display the latest confirmed Push balance, not a live bank account query. */
    private fun showSberBalanceDetails() {
        val enabled = SberBalanceStore.isEnabled(this)
        val granted = SberBalanceStore.isAccessGranted(this)
        val snapshot = if (enabled) SberBalanceStore.latest(this) else null
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(13), dp(19), dp(15))
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sberLogo(48), LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(12), 1))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(textLine("Сбербанк", 19f, ink, true))
                addView(verticalGap(3))
                addView(textLine("Информация о последнем балансе", 12f, muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        panel.addView(heading)
        panel.addView(verticalGap(18))
        val balancePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(softAccent, 17)
            addView(textLine("Последний подтверждённый баланс", 12f, muted))
            addView(verticalGap(5))
            addView(textLine(snapshot?.let { rubles(it.kopecks) }
                ?: "Упс, пока нет данных", if (snapshot == null) 19f else 29f, accent, true))
            if (snapshot == null) {
                addView(verticalGap(7))
                addView(textLine(when {
                    !enabled -> "Получение Push Сбербанка отключено."
                    !granted -> "Разрешите BalanceHub доступ к уведомлениям Android."
                    else -> "Ожидаем Push Сбербанка с явно указанным остатком."
                }, 13f, muted))
            }
        }
        panel.addView(balancePanel)
        panel.addView(verticalGap(15))
        panel.addView(textLine("Карта / счёт", 12f, muted))
        panel.addView(verticalGap(3))
        panel.addView(textLine(snapshot?.lastFour?.let { "•••• $it" }
            ?: "Номер не указан", 16f, ink, true))
        panel.addView(verticalGap(13))
        panel.addView(textLine("Последнее обновление", 12f, muted))
        panel.addView(verticalGap(3))
        panel.addView(textLine(snapshot?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(it.observedAt))
        } ?: "Нет подтверждённых Push", 15f, ink, true))
        panel.addView(verticalGap(13))
        panel.addView(textLine("Источник данных", 12f, muted))
        panel.addView(verticalGap(3))
        panel.addView(textLine("Push СберБанк Онлайн", 15f, ink, true))
        panel.addView(verticalGap(13))
        panel.addView(textLine("Статус данных", 12f, muted))
        panel.addView(verticalGap(3))
        val status = when {
            !enabled -> "Получение Push отключено"
            !granted -> "Доступ к уведомлениям отключён"
            snapshot == null -> "Ожидание подтверждённого Push"
            System.currentTimeMillis() - snapshot.observedAt !in 0L..86_400_000L ->
                "Данные могут быть устаревшими"
            else -> "Последние полученные данные, не онлайн-баланс"
        }
        panel.addView(textLine(status, 13f, muted))
        val dialog = themedDialogBuilder()
            .setTitle("Сведения о балансе")
            .setView(android.widget.ScrollView(this).apply { addView(panel) })
            .setNeutralButton("Скрыть карточку") { _, _ ->
                RussianBankCatalog.byId("sber")?.let(::confirmHideBankCard)
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

    private fun renderOtherBankBalance(bank: RussianBank) {
        val enabled = BankCardPreferences.enabled(this, bank.id)
        val granted = SberBalanceStore.isAccessGranted(this)
        val snapshot = if (enabled) RussianBankBalanceStore.latest(this, bank.id)
            else null
        val bankCard = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(bankLogo(bank, 54),
            LinearLayout.LayoutParams(dp(54), dp(54)))
        row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        val subtitle = when {
            !enabled -> "Push не подключён"
            !granted -> "Нет доступа к уведомлениям"
            snapshot == null -> "Ожидание банковского Push"
            snapshot.lastFour != null -> "Последний Push · ••••" + snapshot.lastFour
            else -> "Последний Push · номер не указан"
        }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine(bank.name, 16f, ink, true))
            addView(verticalGap(3))
            addView(textLine(subtitle, 12f, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val amount = textLine(
            snapshot?.let { rubles(it.kopecks) } ?: "Нет данных",
            if (snapshot == null) 15f else 22f,
            Color.rgb(23, 55, 33), true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(
                if (isNight) Color.rgb(191, 239, 104)
                else Color.rgb(216, 241, 166), 14)
        }
        if (resources.configuration.screenWidthDp >= 395) {
            row.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
            row.addView(amount)
        }
        bankCard.addView(row)
        if (resources.configuration.screenWidthDp < 395) {
            bankCard.addView(verticalGap(9))
            bankCard.addView(LinearLayout(this).apply {
                gravity = Gravity.END
                addView(amount)
            })
        }
        bankCard.addView(verticalGap(8))
        if (snapshot != null) {
            val time = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                DateFormat.SHORT).format(Date(snapshot.observedAt))
            bankCard.addView(textLine("Подтверждено Push " + bank.name + ": " +
                time, 12f, muted))
        } else bankCard.addView(textLine(when {
            !enabled -> "Подключите Push в настройках банков"
            !granted -> "Разрешите BalanceHub доступ к уведомлениям"
            else -> RussianBankDiagnostics.last(this, bank)
                ?: "Ожидание Push с указанием остатка в рублях"
        }, 12f, muted))
        bankCard.isClickable = true
        bankCard.isFocusable = true
        bankCard.contentDescription = "Подробнее о последнем балансе " + bank.name
        bankCard.setOnClickListener { showOtherBankDetails(bank) }
        contentContainer.addView(bankCard)
        contentContainer.addView(verticalGap(12))
    }

    private fun showOtherBankDetails(bank: RussianBank) {
        val enabled = BankCardPreferences.enabled(this, bank.id)
        val granted = SberBalanceStore.isAccessGranted(this)
        val snapshot = if (enabled) RussianBankBalanceStore.latest(this, bank.id)
            else null
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(13), dp(19), dp(15))
        }
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bankLogo(bank, 48), LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(12), 1))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(textLine(bank.name, 19f, ink, true))
                addView(verticalGap(3))
                addView(textLine("Информация о последнем балансе", 12f, muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        panel.addView(verticalGap(18))
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(softAccent, 17)
            addView(textLine("Последний подтверждённый баланс", 12f, muted))
            addView(verticalGap(5))
            addView(textLine(snapshot?.let { rubles(it.kopecks) }
                ?: "Упс, пока нет данных",
                if (snapshot == null) 19f else 29f, accent, true))
            if (snapshot == null) {
                addView(verticalGap(7))
                addView(textLine(when {
                    !enabled -> "Получение Push этого банка пока отключено."
                    !granted -> "Разрешите BalanceHub доступ к уведомлениям Android."
                    else -> "Ожидаем уведомление банка с явно указанным остатком."
                }, 13f, muted))
            }
        })
        for ((heading, value) in listOf(
            "Карта / счёт" to
                (snapshot?.lastFour?.let { "•••• $it" } ?: "Номер не указан"),
            "Последнее обновление" to
                (snapshot?.let { DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.observedAt)) }
                    ?: "Нет подтверждённых Push"),
            "Источник данных" to ("Push " + bank.name),
            "Статус данных" to (when {
                !enabled -> "Получение Push отключено"
                !granted -> "Доступ к уведомлениям отключён"
                snapshot == null -> RussianBankDiagnostics.last(this, bank)
                    ?: "Ожидание подтверждённого Push"
                System.currentTimeMillis() - snapshot.observedAt !in 0L..86_400_000L ->
                    "Данные могут быть устаревшими"
                else -> "Последние полученные данные, не онлайн-баланс"
            })
        )) {
            panel.addView(verticalGap(13))
            panel.addView(textLine(heading, 12f, muted))
            panel.addView(verticalGap(3))
            panel.addView(textLine(value, 15f, ink, true))
        }
        val dialog = themedDialogBuilder().setTitle("Сведения о балансе")
            .setView(android.widget.ScrollView(this).apply { addView(panel) })
            .setNeutralButton("Скрыть карточку") { _, _ ->
                confirmHideBankCard(bank)
            }.setPositiveButton("Закрыть", null).create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
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
                persistPayments()
            }
        }
        val old = payments.firstOrNull { it.id == id }
        if (!preferences.getBoolean(enabledKeyFor(sim), true)) {
            if (old != null) {
                cancelReminder(old)
                payments.remove(old)
                persistPayments()
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
        persistPayments()
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

    /** Payment creation uses the same controls as before, now in a scrollable dialog. */
    private fun showNewPaymentDialog() {
        editingId = null
        selectedPaymentIcon = null
        dueDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
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
        val recOptions = listOf("Единоразовый", "Еженедельный", "Ежемесячный")
        recurrenceInput = styledSpinner(recOptions)
        form.addView(textLine("Название", 13f, muted))
        form.addView(titleInput)
        form.addView(verticalGap(9))
        form.addView(textLine("Иконка", 13f, muted))
        form.addView(iconRow)
        form.addView(verticalGap(9))
        form.addView(textLine("Сумма", 13f, muted))
        form.addView(amountInput)
        form.addView(verticalGap(9))
        form.addView(textLine("Валюта", 13f, muted))
        form.addView(currencyInput)
        form.addView(verticalGap(12))
        form.addView(dateButton, LinearLayout.LayoutParams(-1, dp(50)))
        form.addView(verticalGap(12))
        form.addView(textLine("Повторение платежа", 13f, muted))
        form.addView(verticalGap(6))
        form.addView(recurrenceInput)
        form.addView(verticalGap(12))
        form.addView(textLine("Напомнить за сколько дней", 13f, muted))
        form.addView(leadDaysInput)
        val dialog = themedDialogBuilder().setTitle("Новое напоминание")
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
            "banks" -> renderBanksSettings()
            "notifications" -> renderNotificationsMenu()
            "payment", "event", "update_sound" -> renderNotificationCategory()
            "updates" -> renderUpdateSettings()
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
                if (now - engineeringTapStartedAt > 3_000L || engineeringTapCount == 0) {
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
        settingsGroup(settingsPanel(
            settingsRow("settings", "Оформление", "Авто, светлая или тёмная") {
                openSettingsPage("appearance")
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
        settingsHeader("Банки", "Карточки и банковские Push")
        for (bank in RussianBankCatalog.available(this)) {
            settingsGroup(settingsPanel(
                settingsSwitch("credit-card", bank.name,
                    "Показывать карточку в «Балансах»",
                    BankCardPreferences.visible(this, bank.id)) { show ->
                    BankCardPreferences.setVisible(this, bank.id, show)
                    renderTab()
                },
                settingsRow("settings", "Получение Push",
                    if (BankCardPreferences.enabled(this, bank.id))
                        "Включено · нажмите для отключения"
                    else "Не подключено · нажмите для подключения") {
                    toggleBankPush(bank)
                }
            ))
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
                        val error = BalanceNotificationSounds.applyVolumeChoice(
                            this@MainActivity, category, gain)
                        if (error != null)
                            Toast.makeText(this@MainActivity,
                                error, Toast.LENGTH_LONG).show()
                        refreshSettingsWithoutJump()
                    }
                })
            })
        }
        val rows = mutableListOf<View>()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rows.add(settingsRow("settings", "Канал уведомлений Android",
                BalanceNotificationSounds.effectiveSummary(this, category)) {
                openAndroidNotificationSettings(category)
            })
            if (BalanceNotificationSounds.needsRepair(this, category)) {
                rows.add(settingsRow("settings", "Применить звук и вибрацию",
                    "Выбранные настройки отличаются от канала Android") {
                    showChannelRepairDialog(category)
                })
            }
        }
        settingsGroup(settingsPanel(*rows.toTypedArray()))
    }

    private fun showChannelRepairDialog(category: String) {
        themedDialogBuilder()
            .setTitle("Применить настройки: " + notificationTitle(category) + "?")
            .setMessage("Android сохраняет отдельно звук и вибрацию системного канала. " +
                "BalanceHub создаст новый канал с выбранной здесь мелодией и " +
                "вибросигналом. Изменённые вручную параметры этого канала могут " +
                "сброситься. Другие категории, платежи и события не изменятся. " +
                "Если Android не примет настройки, текущий канал останется.")
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Настройки Android") { _, _ ->
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
                    Toast.makeText(this,
                        "Настройки переданы Android. Проверьте уведомление на телефоне.",
                        Toast.LENGTH_LONG).show()
                }
                refreshSettingsWithoutJump()
            }.show()
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
            }
        ))
        if (found != null) settingsGroup(settingsPanel(
            settingsRow("download", "Скачать новую версию", found.tag) {
                showGithubUpdateDialog(found)
            }
        ))
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
        val banking = card()
        banking.addView(sberHeading("Сбербанк   ›"))
        banking.addView(verticalGap(6))
        banking.addView(textLine(
            "Подключение Push, доступ Android, состояние и диагностика", 13f, muted))
        banking.setOnClickListener {
            bankSettingsOpen = true
            renderTab()
        }
        contentContainer.addView(banking)
        contentContainer.addView(verticalGap(12))
        val channels = card().apply {
            isClickable = true
            isFocusable = true
            addView(textLine("Системные каналы   ›", 17f, ink, true))
            addView(verticalGap(5))
            addView(textLine("Платежи, события, обновления: звук, вибрация и права Android",
                13f, muted))
            setOnClickListener {
                engineeringChannelsOpen = true
                renderTab()
            }
        }
        contentContainer.addView(channels)
        contentContainer.addView(verticalGap(12))
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
        notifications.addView(secondaryButton("Тест платежа через 2 минуты") {
            scheduleDiagnosticReminder(BalanceNotificationSounds.PAYMENT)
        })
        notifications.addView(verticalGap(8))
        notifications.addView(secondaryButton("Тест события через 2 минуты") {
            scheduleDiagnosticReminder(BalanceNotificationSounds.EVENT)
        })
        val log = getSharedPreferences("balancehub_reminder_diagnostics", MODE_PRIVATE)
        notifications.addView(verticalGap(8))
        notifications.addView(secondaryButton("Настройки батареи Android") {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    ("package:" + packageName).toUri()))
            }
        })
        notifications.addView(verticalGap(9))
        for ((key, title) in listOf("payment" to "Платёж", "event" to "Событие")) {
            val planned = log.getLong("${key}_planned", 0L)
            val delivered = log.getLong("${key}_delivered", 0L)
            val posted = log.getLong("${key}_posted", 0L)
            if (planned > 0L) {
                val delta = if (delivered >= planned) {
                    " · запуск +" + ((delivered - planned) / 1000L) + " сек"
                } else " · ожидается"
                notifications.addView(textLine("$title: " +
                    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(planned)) +
                    delta, 12f, muted))
                val method = when (log.getString("${key}_mode", "")) {
                    "exact_idle" -> "точный будильник"
                    "inexact_idle" -> "резервный неточный"
                    "expired" -> "время уже прошло"
                    else -> "неизвестен"
                }
                notifications.addView(textLine(
                    "Метод: $method · разрешение точных: " +
                        (if (log.getBoolean("${key}_exact_allowed", false))
                            "да" else "нет") +
                        " · без ограничений батареи: " +
                        (if (log.getBoolean("${key}_battery_unrestricted", false))
                            "да" else "нет"),
                    12f, muted))
                if (posted >= delivered && delivered > 0L)
                    notifications.addView(textLine("Отправка в Android спустя " +
                        ((posted - delivered) / 1000L) + " сек после запуска",
                        12f, muted))
            }
        }
        contentContainer.addView(notifications)
        contentContainer.addView(verticalGap(12))
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
        contentContainer.addView(verticalGap(12))
        contentContainer.addView(textLine(
            "При выходе из инженерного меню доступ закрывается.",
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
                themedDialogBuilder()
                    .setTitle("Канал: " + notificationTitle(category))
                    .setMessage(NotificationSignalAudit.report(this, category)
                        .joinToString("\n"))
                    .setNegativeButton("Закрыть", null)
                    .setPositiveButton("Настройки Android") { _, _ ->
                        openAndroidNotificationSettings(category)
                    }.show()
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
                "«На лапках», «Теперь у тебя» или другой распознанной фразой покажем " +
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
        val recurrence = listOf("once", "weekly", "monthly")[recurrenceInput.selectedItemPosition]
        val old = payments.firstOrNull { it.id == editingId }
        if (old != null) {
            cancelReminder(old)
            payments.removeAll { it.id == old.id }
        }
        val reminder = PaymentReminder(old?.id ?: UUID.randomUUID().toString(), title, amount,
            currency, dueDate.timeInMillis, leadDays, recurrence, selectedPaymentIcon)
        payments.add(reminder)
        editingId = null
        persistPayments()
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

    private fun renderPayments() {
        if (!::listContainer.isInitialized || currentTab != 2 || archiveOpen) return
        listContainer.removeAllViews()
        if (expandedPaymentId != null && payments.none { it.id == expandedPaymentId }) {
            expandedPaymentId = null
        }
        val sorted = payments.sortedBy { it.dueAt }
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
        }
        header.addView(iconTile("calendar-days", 38),
            LinearLayout.LayoutParams(dp(38), dp(38)))
        header.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine("Предстоящие платежи", 17f, ink, true))
            addView(verticalGap(3))
            addView(textLine(
                if (upcomingListExpanded) "Нажмите, чтобы свернуть · " + sorted.size
                else "Нажмите, чтобы раскрыть · " + sorted.size,
                12f, muted
            ))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconTile(if (upcomingListExpanded)
            "chevron-up" else "chevron-down", 32),
            LinearLayout.LayoutParams(dp(32), dp(32)))
        header.setOnClickListener {
            upcomingListExpanded = PaymentListInteraction.toggleSection(upcomingListExpanded)
            if (!upcomingListExpanded) expandedPaymentId = null
            preferences.edit().putBoolean("payments_list_expanded", upcomingListExpanded).apply()
            renderPayments()
        }
        section.addView(header)

        if (upcomingListExpanded) {
            section.addView(View(this).apply {
                setBackgroundColor(if (isNight) Color.rgb(63, 100, 102)
                    else Color.rgb(201, 228, 224))
            }, LinearLayout.LayoutParams(-1, dp(1)))
            if (sorted.isEmpty()) {
                section.addView(textLine(
                    "Пока нет напоминаний. Добавьте первый платёж выше.",
                    13f, muted
                ).apply { setPadding(dp(15), dp(17), dp(15), dp(17)) })
            }
            for ((index, payment) in sorted.withIndex()) {
                val expanded = expandedPaymentId == payment.id
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = if (expanded) rounded(softAccent, 12)
                        else rounded(Color.TRANSPARENT, 12)
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Платёж " + payment.title +
                        if (expanded) ", свернуть подробности" else ", раскрыть подробности"
                }
                val titleAndDate = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(textLine(payment.title, 14f, ink, true).apply {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(verticalGap(4))
                    addView(textLine(DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(payment.dueAt)), 12f, muted))
                }
                row.addView(iconTile(payment.iconId ?: IconRegistry.select(
                    payment.title, "credit-card"), 38), LinearLayout.LayoutParams(dp(38), dp(38)))
                row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
                row.addView(titleAndDate, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
                val right = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END
                    addView(textLine(
                        PaymentCurrencyFormatter.amountWithCurrency(
                            payment.amount, payment.currency
                        ), 14f, ink, true
                    ).apply { gravity = Gravity.END })
                    addView(verticalGap(2))
                    addView(iconTile(if (expanded)
                        "chevron-up" else "chevron-down", 25),
                        LinearLayout.LayoutParams(dp(25), dp(25)))
                }
                row.addView(right)
                row.setOnClickListener {
                    expandedPaymentId = PaymentListInteraction.toggleRow(
                        expandedPaymentId, payment.id
                    )
                    renderPayments()
                }
                item.addView(row)

                if (expanded) {
                    val detail = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), 0, dp(14), dp(12))
                    }
                    detail.addView(textLine(
                        "Напомнить за " + payment.leadDays + " дн. · " +
                            recurrenceLabel(payment.recurrence),
                        13f, muted
                    ))
                    detail.addView(verticalGap(10))
                    val actions = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }
                    if (payment.id.startsWith("t2_subscription_") ||
                        payment.id.startsWith("mobile_subscription_")) {
                        actions.addView(secondaryButton("Настроить") {
                            val simId = payment.id.removePrefix("t2_subscription_")
                                .removePrefix("mobile_subscription_").toIntOrNull()
                            if (simId != null) selectedOperatorId = simId
                            mobileSettingsOpen = true
                            selectTab(4)
                        }, LinearLayout.LayoutParams(0, dp(45), 1f))
                        actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
                        actions.addView(secondaryButton("Отключить") {
                            if (PaymentArchiveStore.archiveById(
                                    this, payment.id, PaymentArchiveStore.DELETED
                                )) {
                                val prefix = if (payment.id.startsWith("t2_subscription_"))
                                    "t2_payment_enabled_" else "mobile_payment_enabled_"
                                val sim = payment.id.removePrefix("t2_subscription_")
                                    .removePrefix("mobile_subscription_")
                                preferences.edit().putBoolean(prefix + sim, false).apply()
                                cancelReminder(payment)
                                payments.removeAll { it.id == payment.id }
                                expandedPaymentId = null
                                renderPayments()
                            } else {
                                Toast.makeText(this, "Не удалось сохранить в архив",
                                    Toast.LENGTH_LONG).show()
                            }
                        }, LinearLayout.LayoutParams(0, dp(45), 1f))
                    } else {
                        actions.addView(secondaryButton("Изменить") {
                            showEditPaymentDialog(payment)
                        }, LinearLayout.LayoutParams(0, dp(45), 1f))
                        actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
                        actions.addView(secondaryButton("Удалить") {
                            showDeletePaymentDialog(payment)
                        }, LinearLayout.LayoutParams(0, dp(45), 1f))
                    }
                    detail.addView(actions)
                    detail.startAnimation(
                        android.view.animation.AlphaAnimation(0.5f, 1f).apply {
                            duration = 160L
                        }
                    )
                    item.addView(detail)
                }
                section.addView(item)
                if (index < sorted.lastIndex) {
                    section.addView(View(this).apply {
                        setBackgroundColor(if (isNight) Color.rgb(54, 84, 89)
                            else Color.rgb(213, 231, 228))
                    }, LinearLayout.LayoutParams(-1, dp(1)))
                }
            }
        }
        listContainer.addView(section)
        val archivedCount = PaymentArchiveStore.archived(this).size
        val folder = settingsPanel(settingsRow("archive", "Архив платежей",
            "$archivedCount записей · завершённые и удалённые") {
            archiveOpen = true
            expandedArchivePaymentId = null
            renderTab()
        })
        listContainer.addView(verticalGap(14))
        listContainer.addView(folder)
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
            val amount = PaymentCurrencyFormatter.amountWithCurrency(
                entry.amount, entry.currency)
            val dueLabel = DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(Date(entry.dueAt))
            val status = if (entry.reason == PaymentArchiveStore.COMPLETED)
                "Срок истёк" else "Удалено вручную"
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
                        val payment = PaymentReminder(
                            restored.id, restored.title, restored.amount,
                            restored.currency, restored.dueAt,
                            restored.leadDays, restored.recurrence
                        )
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
                    chosen.set(visible.get(Calendar.YEAR), visible.get(Calendar.MONTH), day, 9, 0, 0)
                    chosen.set(Calendar.MILLISECOND, 0)
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
        dateControl.setOnClickListener {
            showThemedCalendar(date) {
                dateControl.text = "Дата: " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.time)
            }
        }
        val options = listOf("Единоразовый", "Еженедельный", "Ежемесячный")
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
            setSelection(listOf("once", "weekly", "monthly")
                .indexOf(payment.recurrence).coerceAtLeast(0))
        }
        editor.addView(textLine("Название", 13f, muted))
        editor.addView(name)
        editor.addView(verticalGap(8))
        editor.addView(textLine("Иконка", 13f, muted))
        editor.addView(iconRow)
        editor.addView(verticalGap(8))
        editor.addView(textLine("Сумма", 13f, muted))
        editor.addView(amount)
        editor.addView(verticalGap(8))
        editor.addView(textLine("Валюта", 13f, muted))
        editor.addView(currency)
        editor.addView(verticalGap(12))
        editor.addView(dateControl)
        editor.addView(verticalGap(12))
        editor.addView(textLine("Повторение", 13f, muted))
        editor.addView(verticalGap(6))
        editor.addView(recurrence)
        editor.addView(verticalGap(12))
        editor.addView(textLine("Напомнить за (дней)", 13f, muted))
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
                cancelReminder(payment)
                payments.removeAll { it.id == payment.id }
                val updated = payment.copy(title = newTitle, amount = newAmount,
                    currency = newCurrency, dueAt = date.timeInMillis,
                    leadDays = newDays,
                    recurrence = listOf("once", "weekly", "monthly")[recurrence.selectedItemPosition],
                    iconId = chosenIcon)
                payments.add(updated)
                persistPayments()
                scheduleReminder(updated)
                renderPayments()
                dialog.dismiss()
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

    private fun styledSpinner(options: List<String>, selected: Int = 0): Spinner =
        Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(
                this@MainActivity, android.R.layout.simple_spinner_item, options
            ) {
                override fun getView(position: Int, convertView: View?,
                    parent: android.view.ViewGroup): View =
                    textLine(options[position], 16f, ink).apply {
                        setPadding(dp(14), dp(12), dp(14), dp(12))
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

    private fun renderEvents() {
        contentContainer.addView(textLine("События", 23f, ink, true))
        contentContainer.addView(verticalGap(6))
        contentContainer.addView(textLine(
            "Важные даты и планы под вашим контролем", 13f, muted
        ))
        contentContainer.addView(verticalGap(13))
        contentContainer.addView(primaryButton("＋  Добавить событие") {
            showEventEditor()
        })
        contentContainer.addView(verticalGap(17))

        // Reconcile also when switching tabs while the app has stayed open.
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
                addView(textLine(
                    if (eventListExpanded) "Нажмите, чтобы свернуть · " + upcoming.size
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
            if (upcoming.isEmpty()) {
                list.addView(textLine("Событий пока нет. Запланируйте первое событие.",
                    13f, muted).apply { setPadding(dp(15), dp(17), dp(15), dp(17)) })
            }
        val now = System.currentTimeMillis()
        for (event in upcoming) {
            list.addView(verticalGap(12))
            val expanded = expandedEventId == event.id
            val icon = event.iconId ?: IconRegistry.select(event.title)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(9), dp(8), dp(9))
                background = rounded(if (expanded) softAccent else Color.TRANSPARENT, 12)
                addView(iconTile(icon, 44), LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(12), 1))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(textLine(event.title, 15f, ink, true).apply {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(verticalGap(3))
                    addView(textLine(eventDateLabel(event.dueAt), 12f, muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val delta = PaymentCalendarMath.daysUntil(now, event.dueAt)
                val status = when {
                    event.dueAt < now -> "Просрочено"
                    delta == 0 -> "Сегодня"
                    delta == 1 -> "Завтра"
                    else -> "Через " + delta + " дн."
                }
                addView(textLine(status, 11f,
                    if (event.dueAt < now) Color.rgb(236, 140, 128) else accent, true))
                setOnClickListener {
                    expandedEventId = if (expanded) null else event.id
                    renderTab()
                }
            }
            list.addView(row)
            if (expanded) {
                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                }
                if (event.details.isNotBlank()) {
                    details.addView(textLine(event.details, 13f, muted))
                    details.addView(verticalGap(5))
                }
                val lead = when (event.leadMinutes) {
                    0 -> "В момент события"
                    10 -> "За 10 минут"
                    60 -> "За 1 час"
                    1_440 -> "За 1 день"
                    4_320 -> "За 3 дня"
                    else -> "За " + event.leadMinutes + " минут"
                }
                details.addView(textLine(
                    eventRepeatLabel(event.recurrence) + " · " + lead, 12f, muted))
                details.addView(verticalGap(10))
                val controls = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                controls.addView(secondaryButton("Изменить") {
                    showEventEditor(event)
                }, LinearLayout.LayoutParams(0, dp(45), 1f))
                controls.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
                controls.addView(secondaryButton("Готово") {
                    completeEvent(event)
                }, LinearLayout.LayoutParams(0, dp(45), 1f))
                controls.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
                controls.addView(secondaryButton("В архив") {
                    moveEventToArchive(event)
                }, LinearLayout.LayoutParams(0, dp(45), 1f))
                details.addView(controls)
                list.addView(details)
            }
        }
        }
        contentContainer.addView(list)
        contentContainer.addView(verticalGap(14))
        val archivedCount = EventStore.archived(this).size
        val folder = settingsPanel(settingsRow("archive", "Архив событий",
            "$archivedCount записей · завершённые и удалённые") {
            eventArchiveExpanded = true
            expandedArchiveEventId = null
            renderTab()
        })
        contentContainer.addView(folder)
        contentContainer.addView(verticalGap(22))
    }

    /** Mirror the payment archive: separate screen, compact rows, one expanded item. */
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

    private fun showEventEditor(original: PlannedEvent? = null) {
        val date = Calendar.getInstance().apply {
            if (original != null) timeInMillis = original.dueAt
            else {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
        }
        var manualIcon = original?.iconId
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
                timeButton.text = "Время: " +
                    java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(date.time)
            }
        }
        val recurrenceValues = listOf("once", "daily", "weekly", "monthly", "yearly")
        val recurrence = styledSpinner(listOf(
            "Не повторяется", "Ежедневно", "Еженедельно", "Ежемесячно", "Ежегодно"
        ), recurrenceValues.indexOf(original?.recurrence ?: "once").coerceAtLeast(0))
        val leadValues = listOf(0, 10, 60, 1_440, 4_320)
        val lead = styledSpinner(listOf(
            "В момент события", "За 10 минут", "За 1 час", "За 1 день", "За 3 дня"
        ), leadValues.indexOf(original?.leadMinutes ?: 60).coerceAtLeast(0))
        editor.addView(textLine("Название", 13f, muted))
        editor.addView(name)
        editor.addView(verticalGap(10))
        editor.addView(textLine("Иконка", 13f, muted))
        editor.addView(iconRow)
        editor.addView(verticalGap(10))
        editor.addView(textLine("Описание", 13f, muted))
        editor.addView(details)
        editor.addView(verticalGap(11))
        val dateTime = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dateTime.addView(dateButton, LinearLayout.LayoutParams(0, dp(48), 3f))
        dateTime.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        dateTime.addView(timeButton, LinearLayout.LayoutParams(0, dp(48), 2f))
        editor.addView(dateTime)
        editor.addView(verticalGap(11))
        editor.addView(textLine("Повторение", 13f, muted))
        editor.addView(recurrence)
        editor.addView(verticalGap(10))
        editor.addView(textLine("Напомнить", 13f, muted))
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
                val item = PlannedEvent(original?.id ?: UUID.randomUUID().toString(),
                    title, details.text.toString().trim().take(2_000),
                    date.timeInMillis, leadValues[lead.selectedItemPosition],
                    recurrenceValues[recurrence.selectedItemPosition], manualIcon)
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
        welcomeShowing = true
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        body.addView(textLine("BalanceHub обновлён!", 21f, ink, true))
        body.addView(verticalGap(8))
        body.addView(textLine(
            "Рады снова видеть вас! Мы сделали BalanceHub удобнее: " +
                "теперь важные даты, планы и платежи легко держать под контролем.",
            14f, muted
        ))
        body.addView(verticalGap(9))
        body.addView(textLine(
            "Что нового в версии " + (packageInfo.versionName ?: ""), 15f, ink, true
        ))
        for (note in AppReleaseNotes.current) {
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
                    addView(verticalGap(3))
                    addView(textLine(note.description, 12f, muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            body.addView(row)
        }
        body.addView(verticalGap(15))
        body.addView(textLine(
            "Спасибо, что выбираете BalanceHub. Пусть всё важное будет под рукой!",
            13f, accent, true
        ))
        val dialog = themedDialogBuilder().setView(android.widget.ScrollView(this).apply {
            addView(body)
        }).setPositiveButton("Начать пользоваться") { _, _ ->
            preferences.edit().putLong("release_welcome_version", versionCode).apply()
        }.create()
        dialog.setOnDismissListener {
            welcomeShowing = false
            deferredUpdate?.let {
                deferredUpdate = null
                window.decorView.post { showGithubUpdateDialog(it) }
            }
        }
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(dialogGradient()) }
        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.getIntExtra("open_tab", -1)) {
            3 -> if (currentTab == 3) renderTab() else selectTab(3)
            4 -> {
                if (currentTab != 4) selectTab(4)
                if (intent.getBooleanExtra("show_update", false)) {
                    GithubUpdateEngine.cached(this)?.let { showGithubUpdateDialog(it) }
                        ?: checkGithubUpdate(manual = true, offer = true)
                }
            }
        }
    }

    private fun recurrenceLabel(value: String) = when (value) {
        "weekly" -> "Каждую неделю"
        "monthly" -> "Каждый месяц"
        else -> "Единоразово"
    }

    private fun scheduleReminder(payment: PaymentReminder) {
        PaymentAlarmScheduler.schedule(this, payment.id, payment.title, payment.amount,
            payment.currency, payment.dueAt, payment.leadDays, payment.recurrence)
    }

    private fun cancelReminder(payment: PaymentReminder) {
        PaymentAlarmScheduler.cancel(this, payment.id)
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
                val issues = NotificationChannelSignalState.issues(
                    wantsSound = BalanceNotificationSounds.selected(this, category) != "silent" &&
                        BalanceNotificationSounds.volume(this, category) > 0,
                    channelHasSound = channel.sound != null,
                    wantsVibration = BalanceNotificationSounds.vibrationFor(this, category),
                    channelHasVibration = channel.shouldVibrate())
                if (issues.isNotEmpty()) {
                    themedDialogBuilder()
                        .setTitle("Уведомление будет без части сигналов")
                        .setMessage(issues.joinToString("\n") +
                            "\n\nНастройки в BalanceHub не заменяют системные " +
                            "параметры канала. Откройте настройки Android либо " +
                            "восстановите канал в настройках этой категории.")
                        .setNegativeButton("Отмена", null)
                        .setNeutralButton("Настройки Android") { _, _ ->
                            openAndroidNotificationSettings(category)
                        }
                        .setPositiveButton("Отправить тест") { _, _ ->
                            postDiagnosticNotification(category, channelId)
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
            Toast.makeText(this, "Уведомление передано Android. " +
                "Фактическое воспроизведение зависит от настроек устройства.",
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

    private fun loadPayments() {
        val array = JSONArray(preferences.getString("payment_reminders", "[]") ?: "[]")
        var advanced = false
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val recurrence = item.optString("recurrence", "once")
            val lead = item.getInt("leadDays")
            val date = Calendar.getInstance().apply { timeInMillis = item.getLong("dueAt") }
            var paymentAdvanced = false
            if (recurrence == "weekly" || recurrence == "monthly") {
                // T2 stays in "upcoming" until the actual estimated debit date;
                // receiving a one-day-before alert must not hide that payment.
                while (date.timeInMillis <= System.currentTimeMillis()) {
                    if (recurrence == "weekly") date.add(Calendar.DAY_OF_YEAR, 7)
                    else date.add(Calendar.MONTH, 1)
                    advanced = true
                    paymentAdvanced = true
                }
            }
            val id = item.getString("id")
            val oldTitle = item.getString("title")
            val title = if (id.startsWith("t2_subscription_")) {
                "Абонентская плата T2"
            } else oldTitle
            if (title != oldTitle) {
                advanced = true
                paymentAdvanced = true
            }
            val payment = PaymentReminder(id, title,
                item.getString("amount"), item.getString("currency"), date.timeInMillis,
                lead, recurrence, item.optString("icon")
                    .takeIf { IconRegistry.find(it) != null })
            payments.add(payment)
            if (paymentAdvanced) scheduleReminder(payment)
            else if (payment.recurrence == "once") {
                PaymentArchiveScheduler.schedule(this, payment.id, payment.dueAt)
            }
        }
        if (advanced) persistPayments()
        // Reconcile every active reminder after upgrades and force-stop recovery.
        payments.forEach(::scheduleReminder)
    }

    private fun persistPayments() {
        // Do not resurrect items moved to history by an alarm while the UI was open.
        val historyIds = PaymentArchiveStore.archived(this).map { it.id }.toSet()
        payments.removeAll { it.id in historyIds }
        val array = JSONArray()
        for (payment in payments) {
            array.put(JSONObject().apply {
                put("id", payment.id); put("title", payment.title); put("amount", payment.amount)
                put("currency", payment.currency); put("dueAt", payment.dueAt); put("leadDays", payment.leadDays)
                put("recurrence", payment.recurrence)
                put("icon", payment.iconId ?: JSONObject.NULL)
            })
        }
        preferences.edit().putString("payment_reminders", array.toString()).apply()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private data class PaymentReminder(
    val id: String, val title: String, val amount: String, val currency: String,
    val dueAt: Long, val leadDays: Int, val recurrence: String = "once",
    val iconId: String? = null
)

private data class HelpSection(
    val title: String,
    val subtitle: String,
    val icon: String,
    val paragraphs: List<String>
)
