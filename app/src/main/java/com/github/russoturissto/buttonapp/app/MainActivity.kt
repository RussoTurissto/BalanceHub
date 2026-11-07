package com.github.russoturissto.buttonapp.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.app.AlarmManager
import android.app.DatePickerDialog
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
    private var tabViews: List<TextView> = emptyList()
    private var currentTab = 0
    private var mobileSettingsOpen = false
    private var operatorDetailsOpen = false
    private var archiveOpen = false
    private var upcomingListExpanded = true
    private var homeUpcomingExpanded = false
    private var homeExpandedPaymentId: String? = null
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
        currentTab = savedInstanceState?.getInt("current_tab") ?: 0
        mobileSettingsOpen = savedInstanceState?.getBoolean("mobile_settings_open") ?: false
        operatorDetailsOpen = savedInstanceState?.getBoolean("operator_details_open") ?: false
        archiveOpen = savedInstanceState?.getBoolean("archive_open") ?: false
        upcomingListExpanded = savedInstanceState?.getBoolean("payments_list_expanded")
            ?: preferences.getBoolean("payments_list_expanded", true)
        expandedPaymentId = savedInstanceState?.getString("expanded_payment_id")
        homeUpcomingExpanded = savedInstanceState?.getBoolean("home_payments_expanded")
            ?: preferences.getBoolean("home_payments_expanded", false)
        homeExpandedPaymentId = savedInstanceState?.getString("home_expanded_payment_id")
        homeSelectedDay = savedInstanceState?.getInt("home_selected_day", -1)
            ?.takeIf { it in 1..31 }
        savedInstanceState?.getLong("home_calendar_month", 0L)
            ?.takeIf { it > 0L }?.let { homeCalendarMonth.timeInMillis = it }
        selectedOperatorId = savedInstanceState?.getInt("selected_operator_id", Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        tutorialStep = savedInstanceState?.getInt("tutorial_step") ?:
            if (preferences.getBoolean("onboarding_completed", false)) -1 else 0
        PaymentArchiveStore.archiveDue(this)
        loadPayments()
        for (sim in T2Balance.sims(this)) syncMobilePayment(sim)
        showDashboard()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    archiveOpen && currentTab == 2 -> {
                        archiveOpen = false
                        renderTab()
                    }
                    operatorDetailsOpen -> {
                        operatorDetailsOpen = false
                        renderTab()
                    }
                    mobileSettingsOpen && currentTab == 3 -> {
                        mobileSettingsOpen = false
                        renderTab()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }


    override fun onResume() {
        super.onResume()
        PaymentArchiveStore.archiveDue(this)
        // The background receiver may already have archived a payment while the
        // activity was paused. Always reconcile the in-memory list with history.
        val archivedIds = PaymentArchiveStore.archived(this).map { it.id }.toSet()
        if (archivedIds.isNotEmpty() && ::contentContainer.isInitialized) {
            val changed = payments.removeAll { it.id in archivedIds }
            if (changed) renderTab()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 510 || requestCode == 511) &&
            (currentTab == 1 || (currentTab == 3 && mobileSettingsOpen))) renderTab()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("current_tab", currentTab)
        outState.putBoolean("mobile_settings_open", mobileSettingsOpen)
        outState.putBoolean("operator_details_open", operatorDetailsOpen)
        outState.putBoolean("archive_open", archiveOpen)
        outState.putBoolean("payments_list_expanded", upcomingListExpanded)
        outState.putString("expanded_payment_id", expandedPaymentId)
        outState.putBoolean("home_payments_expanded", homeUpcomingExpanded)
        outState.putString("home_expanded_payment_id", homeExpandedPaymentId)
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

    private fun swipeToTab(nextTab: Int, direction: Int) {
        if (nextTab !in 0..3 || nextTab == currentTab) return
        contentContainer.animate().cancel()
        selectTab(nextTab)
        // Small slide and decelerating fade produce a gentler tab transition.
        contentContainer.translationX = (if (direction > 0) dp(26) else -dp(26)).toFloat()
        contentContainer.alpha = 0.86f
        contentContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(210L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()
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
                            if (direction != 0 && destination in 0..3) {
                                // Cancel the child's gesture, not its click, once a real
                                // horizontal swipe has been confirmed on release.
                                val cancellation = MotionEvent.obtain(event)
                                cancellation.action = MotionEvent.ACTION_CANCEL
                                super.dispatchTouchEvent(cancellation)
                                cancellation.recycle()
                                post { swipeToTab(destination, direction) }
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
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                if (isNight) intArrayOf(Color.rgb(19, 39, 44), Color.rgb(17, 35, 40))
                else intArrayOf(Color.rgb(234, 247, 244), Color.rgb(224, 243, 239))
            )
        }
        val names = listOf("Главная", "Балансы", "Платежи", "Настройки")
        val buttons = mutableListOf<TextView>()
        names.forEachIndexed { index, name ->
            val tab = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(12), dp(3), dp(12))
                setTextColor(if (index == currentTab) Color.WHITE else ink)
                val selected = index == currentTab
                if (selected) setTypeface(null, Typeface.BOLD)
                background = tabBackground(selected)
                setOnClickListener { selectTab(index) }
            }
            buttons.add(tab)
            tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        tabViews = buttons
        // The system owns the navigation bar. Apply its real inset once to
        // the persistent tab strip instead of estimating it before setContentView.
        ViewCompat.setOnApplyWindowInsetsListener(tabs) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(dp(6), dp(8), dp(6), bottom + dp(8))
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


    // Preserve the screen and its inset listener across tab switches.
    // Recreating the entire root view caused the bottom navigation to jump.
    private fun selectTab(index: Int) {
        if (index == currentTab) return
        currentTab = index
        if (tutorialStep == 0 && index == 1) tutorialStep = 1
        if (index != 3) mobileSettingsOpen = false
        if (index != 1) operatorDetailsOpen = false
        if (index != 2) {
            archiveOpen = false
            expandedPaymentId = null
        }
        tabViews.forEachIndexed { position, tab ->
            val selected = position == index
            tab.setTextColor(if (selected) Color.WHITE else ink)
            tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            tab.background = tabBackground(selected)
        }
        renderTab()
        // Every tab starts at its own top, rather than inheriting another
        // screen's scroll offset and hiding the dashboard summary.
        if (::dashboardScroll.isInitialized) dashboardScroll.scrollTo(0, 0)
    }

    private fun renderTab() {
        contentContainer.removeAllViews()
        // All four top-level headings must share the exact same Y position.
        // Reuse the responsive top inset already approved for "Главная";
        // do not shift the home title or change the wave/header itself.
        val topPadding = HomeDashboardSizing.forHeight(
            resources.configuration.screenHeightDp,
            resources.configuration.fontScale
        ).topPaddingDp
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
        contentContainer.addView(verticalGap(11))
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val upcomingPayments = payments.filter { it.dueAt >= startOfToday }
        val summary = card()
        summary.addView(textLine("Предстоящие платежи", 14f, muted))
        summary.addView(verticalGap(10))
        val totals = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val count = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine(upcomingPayments.size.toString(), 34f, ink, true))
            addView(textLine("Запланировано\nнапоминаний", 12f, muted))
        }
        totals.addView(count, LinearLayout.LayoutParams(0, -2, 1f))
        val divider = View(this).apply { setBackgroundColor(muted) }
        totals.addView(divider, LinearLayout.LayoutParams(dp(1), dp(65)).apply {
            marginStart = dp(7)
            marginEnd = dp(15)
        })
        val valueColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val amount = PaymentOverview.totalLabel(
            upcomingPayments.map { it.amount to it.currency }
        )
        valueColumn.addView(textLine(amount, 20f, ink, true).apply {
            gravity = Gravity.END
        })
        valueColumn.addView(verticalGap(4))
        valueColumn.addView(textLine("Общая сумма", 12f, muted).apply {
            gravity = Gravity.END
        })
        totals.addView(valueColumn, LinearLayout.LayoutParams(0, -2, 1f))
        summary.addView(totals)
        contentContainer.addView(summary)
        contentContainer.addView(verticalGap(12))
        renderHomeCalendar(upcomingPayments)
        contentContainer.addView(verticalGap(12))
        renderHomePayments(upcomingPayments.sortedBy { it.dueAt })
        contentContainer.addView(verticalGap(10))
        contentContainer.addView(primaryButton("Управлять платежами") {
            selectTab(2)
        })
    }

    private fun renderHomeCalendar(upcoming: List<PaymentReminder>) {
        val year = homeCalendarMonth.get(Calendar.YEAR)
        val month = homeCalendarMonth.get(Calendar.MONTH)
        val offset = PaymentCalendarMath.mondayOffset(year, month)
        val monthDays = PaymentCalendarMath.daysInMonth(year, month)
        val sizing = HomeDashboardSizing.forHeight(
            resources.configuration.screenHeightDp,
            resources.configuration.fontScale
        )
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
        val calendar = card().apply { setPadding(dp(13), dp(11), dp(13), dp(12)) }
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
        calendar.addView(verticalGap(6))

        val weekdays = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (name in listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")) {
            weekdays.addView(textLine(name, 11f, muted, true).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, dp(22), 1f))
        }
        calendar.addView(weekdays)
        val rows = (offset + monthDays + 6) / 7
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
                val marked = paymentCount > 0
                val isToday = isCurrentMonth &&
                    today.get(Calendar.DAY_OF_MONTH) == day
                val chosen = homeSelectedDay == day
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(1), dp(2), dp(1), dp(2))
                    background = GradientDrawable().apply {
                        setColor(when {
                            chosen -> Color.rgb(32, 133, 128)
                            marked -> if (isNight) Color.rgb(25, 112, 108)
                                else Color.rgb(207, 239, 230)
                            else -> Color.TRANSPARENT
                        })
                        cornerRadius = dp(11).toFloat()
                        if (isToday) setStroke(dp(2), Color.rgb(45, 221, 212))
                        else if (chosen) setStroke(dp(2), Color.rgb(166, 236, 211))
                    }
                    isClickable = true
                    isFocusable = true
                    contentDescription = day.toString() + " " + title +
                        (if (marked) ", платежей: " + paymentCount else "") +
                        (if (isToday) ", сегодня" else "")
                }
                cell.addView(textLine(day.toString(), 13f,
                    if (chosen || (marked && isNight)) Color.WHITE else ink,
                    chosen || marked || isToday).apply {
                    gravity = Gravity.CENTER
                })
                cell.addView(textLine(
                    if (paymentCount > 1) paymentCount.toString()
                    else if (marked) "•" else " ",
                    10f,
                    if (chosen || (marked && isNight)) Color.WHITE else accent,
                    marked
                ).apply { gravity = Gravity.CENTER })
                cell.setOnClickListener {
                    homeSelectedDay = if (chosen) null else day
                    renderTab()
                }
                daysRow.addView(cell, LinearLayout.LayoutParams(0, dp(sizing.dayCellHeightDp), 1f)
                    .apply { marginStart = dp(1); marginEnd = dp(1) })
            }
            calendar.addView(daysRow)
        }
        calendar.addView(verticalGap(8))
        calendar.addView(View(this).apply {
            setBackgroundColor(if (isNight) Color.rgb(68, 104, 104)
                else Color.rgb(196, 222, 218))
        }, LinearLayout.LayoutParams(-1, dp(1)))
        calendar.addView(verticalGap(8))

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
                homeUpcomingExpanded = true
                homeExpandedPaymentId = nearest.id
                preferences.edit().putBoolean("home_payments_expanded", true).apply()
                renderTab()
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
                addView(textLine("Платежи на " +
                    SimpleDateFormat("d MMMM", Locale("ru"))
                        .format(selectedDate.time), 13f, ink, true))
            }
            val selectedPayments = byDay[chosenDay].orEmpty()
            if (selectedPayments.isEmpty()) {
                details.addView(verticalGap(4))
                details.addView(textLine("На эту дату платежей нет", 12f, muted))
            } else for (payment in selectedPayments) {
                details.addView(verticalGap(4))
                details.addView(textLine(payment.title + " · " +
                    PaymentCurrencyFormatter.amountWithCurrency(
                        payment.amount, payment.currency), 12f, ink))
            }
            calendar.addView(details)
        }
        contentContainer.addView(calendar)
    }

    private fun renderHomePayments(upcoming: List<PaymentReminder>) {
        if (homeExpandedPaymentId != null &&
            upcoming.none { it.id == homeExpandedPaymentId }) {
            homeExpandedPaymentId = null
        }
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardGradient()
            elevation = dp(2).toFloat()
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(15), dp(14), dp(15))
            isClickable = true
            isFocusable = true
            contentDescription = if (homeUpcomingExpanded)
                "Все предстоящие платежи, свернуть"
            else "Все предстоящие платежи, раскрыть"
        }
        heading.addView(textLine("Все предстоящие платежи (" +
            upcoming.size + ")", 16f, ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(textLine(if (homeUpcomingExpanded) "⌃" else "⌄",
            23f, accent, true))
        heading.setOnClickListener {
            homeUpcomingExpanded = !homeUpcomingExpanded
            if (!homeUpcomingExpanded) homeExpandedPaymentId = null
            preferences.edit().putBoolean(
                "home_payments_expanded", homeUpcomingExpanded).apply()
            renderTab()
        }
        section.addView(heading)
        if (homeUpcomingExpanded) {
            section.addView(View(this).apply {
                setBackgroundColor(if (isNight) Color.rgb(63, 98, 100)
                    else Color.rgb(207, 229, 224))
            }, LinearLayout.LayoutParams(-1, dp(1)))
            if (upcoming.isEmpty()) {
                section.addView(textLine("Пока нет предстоящих платежей",
                    13f, muted).apply {
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                })
            }
            for ((index, payment) in upcoming.withIndex()) {
                val expanded = homeExpandedPaymentId == payment.id
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    if (expanded) background = rounded(softAccent, 12)
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(13), dp(10), dp(13), dp(10))
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Платёж " + payment.title +
                        if (expanded) ", свернуть" else ", раскрыть"
                }
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(textLine(payment.title, 13f, ink, true).apply {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(verticalGap(3))
                    addView(textLine(DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(payment.dueAt)), 11f, muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
                row.addView(textLine(PaymentCurrencyFormatter.amountWithCurrency(
                    payment.amount, payment.currency), 13f, ink, true).apply {
                    gravity = Gravity.END
                })
                row.setOnClickListener {
                    homeExpandedPaymentId = PaymentListInteraction.toggleRow(
                        homeExpandedPaymentId, payment.id)
                    renderTab()
                }
                item.addView(row)
                if (expanded) {
                    item.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(13), 0, dp(13), dp(12))
                        addView(textLine("Напомнить за " + payment.leadDays +
                            " дн. · " + recurrenceLabel(payment.recurrence),
                            12f, muted))
                        addView(verticalGap(8))
                        addView(secondaryButton("Открыть во вкладке «Платежи»") {
                            upcomingListExpanded = true
                            expandedPaymentId = payment.id
                            preferences.edit().putBoolean(
                                "payments_list_expanded", true).apply()
                            selectTab(2)
                        }, LinearLayout.LayoutParams(-1, dp(42)))
                    })
                }
                section.addView(item)
                if (index != upcoming.lastIndex) {
                    section.addView(View(this).apply {
                        setBackgroundColor(if (isNight) Color.rgb(55, 87, 90)
                            else Color.rgb(215, 234, 230))
                    }, LinearLayout.LayoutParams(-1, dp(1)))
                }
            }
        }
        contentContainer.addView(section)
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
            return
        }
        for (sim in sims) {
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
            if (tutorialStep == 1 && sim == sims.first() &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) highlightTutorialTarget(mobile)
            contentContainer.addView(mobile)
            contentContainer.addView(verticalGap(12))
        }
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
        summary.addView(textLine(balance?.first?.let { it + " ₽" } ?: "Нет данных",
            28f, accent, true))
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
            selectTab(3)
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
                    else if (currentTab == 3 && mobileSettingsOpen) renderTab()
                } else {
                    Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun renderPaymentScreen() {
        contentContainer.addView(textLine("Платежи", 23f, ink, true))
        contentContainer.addView(verticalGap(12))
        val form = card()
        form.addView(textLine("Новое напоминание", 18f, ink, true))
        form.addView(verticalGap(10))
        titleInput = field("Название платежа")
        amountInput = field("Сумма")
        amountInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        currencyInput = field("Валюта (RUB, EUR...)").apply { setText("RUB") }
        leadDaysInput = field("Напомнить за сколько дней").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("3")
        }
        dateButton = Button(this).apply {
            isAllCaps = false
            setTextColor(accent)
            background = rounded(softAccent, 12)
            setOnClickListener { chooseDate() }
        }
        updateDateButton()
        form.addView(titleInput)
        form.addView(amountInput)
        form.addView(currencyInput)
        form.addView(dateButton)
        form.addView(verticalGap(12))
        form.addView(textLine("Повторение платежа", 14f, muted))
        form.addView(verticalGap(8))
        val recurrenceOptions = listOf("Единоразовый", "Еженедельный", "Ежемесячный")
        val recurrenceAdapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, recurrenceOptions
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return textLine(recurrenceOptions[position], 16f, ink, true).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return textLine(recurrenceOptions[position], 16f, ink).apply {
                    setBackgroundColor(surface)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(15), dp(18), dp(15))
                }
            }
        }
        recurrenceInput = Spinner(this).apply {
            adapter = recurrenceAdapter
            background = rounded(softAccent, 12)
            dropDownVerticalOffset = dp(4)
            minimumHeight = dp(52)
        }
        form.addView(recurrenceInput)
        form.addView(verticalGap(14))
        form.addView(textLine("За сколько дней напомнить", 13f, muted))
        form.addView(leadDaysInput)
        form.addView(verticalGap(10))
        form.addView(primaryButton("Сохранить напоминание") { saveReminder() })
        contentContainer.addView(form)
        contentContainer.addView(verticalGap(18))
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contentContainer.addView(listContainer)
        renderPayments()
    }


    private fun renderSettings() {
        if (mobileSettingsOpen) {
            renderMobileSettings()
            return
        }
        contentContainer.addView(textLine("Настройки", 23f, ink, true))
        contentContainer.addView(verticalGap(6))
        contentContainer.addView(textLine("Управление BalanceHub", 14f, muted))
        contentContainer.addView(verticalGap(18))

        // First and more compact: three side-by-side theme selectors.
        val appearanceCard = card()
        appearanceCard.addView(textLine("Оформление", 18f, ink, true))
        appearanceCard.addView(verticalGap(5))
        appearanceCard.addView(textLine("Тема приложения", 13f, muted))
        appearanceCard.addView(verticalGap(12))
        val currentAppearance = preferences.getString("appearance", "system") ?: "system"
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            Triple("system", "Авто", "◉"),
            Triple("day", "Светлая", "☀"),
            Triple("night", "Тёмная", "☾")
        ).forEachIndexed { index, (mode, label, glyph) ->
            val chosen = currentAppearance == mode
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(8), dp(3), dp(8))
                background = GradientDrawable().apply {
                    setColor(if (chosen) softAccent else surface)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(if (chosen) 2 else 1), if (chosen) accent else muted)
                }
                contentDescription = "Тема: $label"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (mode != preferences.getString("appearance", "system")) {
                        preferences.edit().putString("appearance", mode).apply()
                        val nightMode = when (mode) {
                            "day" -> AppCompatDelegate.MODE_NIGHT_NO
                            "night" -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                        AppCompatDelegate.setDefaultNightMode(nightMode)
                        recreate()
                    }
                }
            }
            cell.addView(textLine(glyph, 19f, if (chosen) ink else accent, true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            })
            cell.addView(verticalGap(4))
            cell.addView(textLine(label, 12f, ink, chosen).apply {
                gravity = Gravity.CENTER
            })
            if (chosen) cell.addView(textLine("✓", 11f, accent, true))
            choices.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        appearanceCard.addView(choices)
        appearanceCard.addView(verticalGap(12))
        contentContainer.addView(appearanceCard)
        contentContainer.addView(verticalGap(14))

        val mobileSettings = card()
        mobileSettings.addView(textLine("Мобильная связь   ›", 18f, ink, true))
        mobileSettings.addView(verticalGap(6))
        mobileSettings.addView(textLine(
            "SIM-карты, проверка баланса и настройки абонентской платы",
            14f, muted
        ))
        mobileSettings.addView(verticalGap(7))
        mobileSettings.addView(textLine("Открыть параметры мобильной связи", 13f, accent))
        mobileSettings.setOnClickListener {
            mobileSettingsOpen = true
            renderTab()
        }
        contentContainer.addView(mobileSettings)
        contentContainer.addView(verticalGap(14))

        val notificationCard = card()
        notificationCard.addView(textLine("Уведомления", 18f, ink, true))
        notificationCard.addView(verticalGap(6))
        notificationCard.addView(textLine(
            "Напоминания о платежах и проверка разрешений",
            14f, muted
        ))
        notificationCard.addView(verticalGap(12))
        notificationCard.addView(primaryButton("Проверить уведомление") {
            sendTestNotification()
        })
        contentContainer.addView(notificationCard)
        contentContainer.addView(verticalGap(14))

        val info = card()
        info.addView(textLine("О приложении", 18f, ink, true))
        info.addView(verticalGap(6))
        info.addView(textLine(
            "BalanceHub · управление балансами и платежами",
            13f, muted
        ))
        contentContainer.addView(info)
        contentContainer.addView(verticalGap(14))
        val help = card()
        help.addView(textLine("Помощь", 18f, ink, true))
        help.addView(verticalGap(6))
        help.addView(textLine(
            "Пошаговая настройка абонентской платы, даты и напоминаний",
            13f, muted
        ))
        help.addView(verticalGap(10))
        help.addView(secondaryButton("Пройти обучение повторно") {
            restartTutorial()
        })
        contentContainer.addView(help)
    }

    private fun renderMobileSettings() {
        contentContainer.addView(secondaryButton("‹  Все настройки") {
            mobileSettingsOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(16))
        contentContainer.addView(textLine("Мобильная связь", 23f, ink, true))
        contentContainer.addView(verticalGap(5))
        contentContainer.addView(textLine("Отдельные настройки для каждой SIM", 14f, muted))
        contentContainer.addView(verticalGap(14))
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
            val simsCard = card()
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
        val operatorCard = card()
        operatorCard.addView(textLine("Оператор и баланс", 18f, ink, true))
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

        val billing = card()
        billing.addView(textLine("Абонентская плата и дата", 18f, ink, true))
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

        val refresh = card()
        refresh.addView(textLine("Автоматическая проверка", 18f, ink, true))
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

    private fun saveReminder() {
        val title = titleInput.text.toString().trim()
        val amount = amountInput.text.toString().trim().replace(',', '.')
        val currency = currencyInput.text.toString().trim().uppercase()
        val leadDays = leadDaysInput.text.toString().toIntOrNull()
        if (title.isBlank() || amount.toBigDecimalOrNull() == null ||
            !currency.matches(Regex("[A-Z]{3}")) || leadDays == null || leadDays !in 0..30
        ) {
            Toast.makeText(this, "Проверьте название, сумму, валюту и срок напоминания", Toast.LENGTH_LONG).show()
            return
        }
        val recurrence = listOf("once", "weekly", "monthly")[recurrenceInput.selectedItemPosition]
        val old = payments.firstOrNull { it.id == editingId }
        if (old != null) {
            cancelReminder(old)
            payments.removeAll { it.id == old.id }
        }
        val reminder = PaymentReminder(old?.id ?: UUID.randomUUID().toString(), title, amount, currency, dueDate.timeInMillis, leadDays, recurrence)
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
        header.addView(textLine("▦", 23f, accent, true).apply {
            gravity = Gravity.CENTER
            background = rounded(softAccent, 11)
        }, LinearLayout.LayoutParams(dp(38), dp(38)))
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
        header.addView(textLine(if (upcomingListExpanded) "⌃" else "⌄",
            25f, accent, true).apply { gravity = Gravity.CENTER })
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
                    addView(textLine(if (expanded) "⌃" else "⌄",
                        18f, accent, true).apply { gravity = Gravity.END })
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
                            selectTab(3)
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
        val folder = card()
        folder.contentDescription = "Папка Архив платежей, записей: $archivedCount"
        folder.isClickable = true
        folder.isFocusable = true
        val folderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        folderRow.addView(textLine("📁", 29f, accent, true))
        folderRow.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val folderLabels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine("Архив платежей", 17f, ink, true))
            addView(verticalGap(3))
            addView(textLine(
                "$archivedCount записей · завершённые и удалённые",
                12f, muted
            ))
        }
        folderRow.addView(folderLabels, LinearLayout.LayoutParams(0, -2, 1f))
        folderRow.addView(textLine("›", 25f, accent, true))
        folder.addView(folderRow)
        folder.setOnClickListener {
            archiveOpen = true
            renderTab()
        }
        listContainer.addView(verticalGap(18))
        listContainer.addView(folder)
        listContainer.addView(verticalGap(22))
    }

    /** History is a separate folder inside the Payments tab. */
    private fun renderArchiveScreen() {
        contentContainer.addView(secondaryButton("‹  К платежам") {
            archiveOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(13))
        contentContainer.addView(textLine("📁 Архив платежей", 23f, ink, true))
        contentContainer.addView(verticalGap(5))
        contentContainer.addView(textLine(
            "Завершённые и удалённые напоминания сохраняются здесь.",
            13f, muted
        ))
        contentContainer.addView(verticalGap(14))
        val entries = PaymentArchiveStore.archived(this)
        if (entries.isEmpty()) {
            val empty = card()
            empty.addView(textLine("Архив пока пуст", 16f, ink, true))
            empty.addView(verticalGap(6))
            empty.addView(textLine(
                "Записи появятся после завершения одноразового платежа или удаления.",
                13f, muted
            ))
            contentContainer.addView(empty)
            return
        }
        for (entry in entries) {
            val item = card()
            item.setPadding(dp(15), dp(13), dp(15), dp(13))
            val status = if (entry.reason == PaymentArchiveStore.COMPLETED)
                "Завершено" else "Удалено вручную"
            item.addView(textLine(entry.title, 16f, ink, true))
            item.addView(verticalGap(4))
            item.addView(textLine(
                PaymentCurrencyFormatter.amountWithCurrency(entry.amount, entry.currency) +
                    " · " + DateFormat.getDateInstance(DateFormat.MEDIUM)
                        .format(Date(entry.dueAt)), 13f, muted
            ))
            item.addView(verticalGap(4))
            item.addView(textLine(
                "$status · ${recurrenceLabel(entry.recurrence)}",
                12f, accent, true
            ))
            item.addView(verticalGap(3))
            item.addView(textLine(
                "Напоминание за ${entry.leadDays} дн. · в архиве с " +
                    DateFormat.getDateInstance(DateFormat.SHORT)
                        .format(Date(entry.archivedAt)), 12f, muted
            ))
            item.addView(verticalGap(10))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.addView(secondaryButton("Восстановить") {
                restoreArchivedPayment(entry)
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            row.addView(secondaryButton("Удалить навсегда") {
                deleteArchivedPayment(entry)
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            item.addView(row)
            contentContainer.addView(item)
            contentContainer.addView(verticalGap(9))
        }
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
                    recurrence = listOf("once", "weekly", "monthly")[recurrence.selectedItemPosition])
                payments.add(updated)
                persistPayments()
                scheduleReminder(updated)
                renderPayments()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun recurrenceLabel(value: String) = when (value) {
        "weekly" -> "Каждую неделю"
        "monthly" -> "Каждый месяц"
        else -> "Единоразово"
    }

    private fun scheduleReminder(payment: PaymentReminder) {
        if (payment.recurrence == "once") {
            PaymentArchiveScheduler.schedule(this, payment.id, payment.dueAt)
        }
        val triggerAt = payment.dueAt - payment.leadDays * 24L * 60L * 60L * 1000L
        if (triggerAt <= System.currentTimeMillis()) return
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, reminderPendingIntent(payment))
    }

    private fun cancelReminder(payment: PaymentReminder) {
        PaymentArchiveScheduler.cancel(this, payment.id)
        (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(reminderPendingIntent(payment))
    }

    private fun reminderPendingIntent(payment: PaymentReminder): PendingIntent {
        val intent = Intent(this, PaymentReminderReceiver::class.java).apply {
            putExtra("title", payment.title)
            putExtra("amount", payment.amount)
            putExtra("currency", payment.currency)
            putExtra("id", payment.id)
            putExtra("dueAt", payment.dueAt)
            putExtra("leadDays", payment.leadDays)
            putExtra("recurrence", payment.recurrence)
        }
        return PendingIntent.getBroadcast(this, payment.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun sendTestNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionIfNeeded()
            Toast.makeText(this, "Разрешите уведомления и нажмите кнопку ещё раз", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, PaymentReminderReceiver::class.java).apply {
            putExtra("title", "Тест BalanceHub")
            putExtra("amount", "1")
            putExtra("currency", "RUB")
            putExtra("id", "balancehub-test-notification")
        }
        sendBroadcast(intent)
        Toast.makeText(this, "Тестовое уведомление отправлено", Toast.LENGTH_SHORT).show()
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
                val isT2 = item.getString("id").startsWith("t2_subscription_") ||
                    item.getString("id").startsWith("mobile_subscription_")
                while ((if (isT2) date.timeInMillis
                        else date.timeInMillis - lead * 86400000L
                    ) <= System.currentTimeMillis()
                ) {
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
                item.getString("amount"), item.getString("currency"), date.timeInMillis, lead, recurrence)
            payments.add(payment)
            if (paymentAdvanced) scheduleReminder(payment)
            else if (payment.recurrence == "once") {
                PaymentArchiveScheduler.schedule(this, payment.id, payment.dueAt)
            }
        }
        if (advanced) persistPayments()
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
            })
        }
        preferences.edit().putString("payment_reminders", array.toString()).apply()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private data class PaymentReminder(
    val id: String, val title: String, val amount: String, val currency: String,
    val dueAt: Long, val leadDays: Int, val recurrence: String = "once"
)
