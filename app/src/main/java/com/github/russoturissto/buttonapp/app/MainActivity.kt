package com.github.russoturissto.buttonapp.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
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
    private var tabViews: List<TextView> = emptyList()
    private var currentTab = 0
    private var mobileSettingsOpen = false
    private var operatorDetailsOpen = false
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
        loadPayments()
        T2Balance.sims(this).firstOrNull { it.isT2 }?.let { syncT2Payment(it) }
        showDashboard()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
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


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 510 && (currentTab == 1 || (currentTab == 3 && mobileSettingsOpen))) renderTab()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("current_tab", currentTab)
        outState.putBoolean("mobile_settings_open", mobileSettingsOpen)
        outState.putBoolean("operator_details_open", operatorDetailsOpen)
        super.onSaveInstanceState(outState)
    }

    private val isNight: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    private val ink: Int get() = if (isNight) Color.rgb(232, 239, 240) else Color.rgb(36, 49, 61)
    private val muted: Int get() = if (isNight) Color.rgb(160, 177, 182) else Color.rgb(111, 125, 136)
    private val accent: Int get() = if (isNight) Color.rgb(77, 135, 139) else Color.rgb(57, 107, 111)
    private val surface: Int get() = if (isNight) Color.rgb(35, 47, 54) else Color.WHITE
    private val backdrop: Int get() = if (isNight) Color.rgb(22, 31, 37) else Color.rgb(246, 248, 248)
    private val softAccent: Int get() = if (isNight) Color.rgb(43, 67, 71) else Color.rgb(233, 243, 242)

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
        background = rounded(surface)
        elevation = dp(1).toFloat()
    }

    private fun secondaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(ink)
        background = rounded(softAccent, 12)
        setOnClickListener { action() }
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(accent, 14)
        setOnClickListener { action() }
    }

    private fun showDashboard() {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backdrop)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), headerTopPadding, dp(20), dp(12))
            setBackgroundColor(accent)
        }
        header.addView(textLine("BalanceHub", 21f, Color.WHITE, true))
        screen.addView(header)

        val scroll = android.widget.ScrollView(this).apply {
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
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setBackgroundColor(backdrop)
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
                background = GradientDrawable().apply {
                    setColor(if (selected) accent else backdrop)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (selected) accent else muted)
                }
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
        if (index != 3) mobileSettingsOpen = false
        if (index != 1) operatorDetailsOpen = false
        tabViews.forEachIndexed { position, tab ->
            val selected = position == index
            tab.setTextColor(if (selected) Color.WHITE else ink)
            tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            tab.background = GradientDrawable().apply {
                setColor(if (selected) accent else backdrop)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (selected) accent else muted)
            }
        }
        renderTab()
    }

    private fun renderTab() {
        contentContainer.removeAllViews()
        when (currentTab) {
            0 -> renderHome()
            1 -> renderBalances()
            2 -> renderPaymentScreen()
            else -> renderSettings()
        }
    }

    private fun renderHome() {
        contentContainer.addView(textLine("Обзор", 23f, ink, true))
        contentContainer.addView(verticalGap(14))
        val summary = card()
        summary.addView(textLine("Предстоящие платежи", 14f, muted))
        summary.addView(verticalGap(8))
        summary.addView(textLine(payments.size.toString(), 36f, ink, true))
        summary.addView(textLine("Запланировано напоминаний", 13f, muted))
        contentContainer.addView(summary)
        contentContainer.addView(verticalGap(16))
        contentContainer.addView(textLine("Ближайшие платежи", 19f, ink, true))
        contentContainer.addView(verticalGap(10))
        val upcoming = payments.sortedBy { it.dueAt }.take(3)
        if (upcoming.isEmpty()) {
            val empty = card()
            empty.addView(textLine("Пока нет платежей", 16f, ink, true))
            empty.addView(verticalGap(5))
            empty.addView(textLine("Добавьте первый платёж во вкладке «Платежи».", 14f, muted))
            contentContainer.addView(empty)
        } else upcoming.forEach { payment ->
            val item = card()
            item.addView(textLine(payment.title, 16f, ink, true))
            item.addView(verticalGap(5))
            item.addView(textLine(PaymentCurrencyFormatter.amountWithCurrency(payment.amount, payment.currency) + " · " +
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(payment.dueAt)), 14f, muted))
            contentContainer.addView(item)
            contentContainer.addView(verticalGap(9))
        }
        contentContainer.addView(verticalGap(12))
        contentContainer.addView(primaryButton("Все платежи") {
            selectTab(2)
        })
    }


    private fun renderBalances() {
        if (operatorDetailsOpen) { renderOperatorDetails(); return }
        contentContainer.addView(textLine("Балансы", 23f, ink, true))
        contentContainer.addView(verticalGap(8))
        contentContainer.addView(textLine("Мобильная связь", 14f, muted))
        contentContainer.addView(verticalGap(14))

        val sims = T2Balance.sims(this)
        val selectedSim = sims.firstOrNull { it.isT2 } ?: sims.firstOrNull()
        val manager = getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as android.telephony.TelephonyManager
        val operator = selectedSim?.operator?.takeIf { it.isNotBlank() }
            ?: manager.simOperatorName?.takeIf { it.isNotBlank() }
            ?: "Оператор не определён"
        val operatorCode = manager.simOperator.orEmpty()
        val known = RussianMobileOperators.find(operatorCode, operator)
        val cached = selectedSim?.let { T2Balance.cached(this, it.subscriptionId) }
        syncT2Payment(selectedSim)

        // Compact Variant 1: operator identity on the left, a prominent
        // balance chip on the right. Stack the chip on narrow devices.
        val mobile = card()
        val operatorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val emblem = textLine(known?.shortLabel ?: "SIM", 19f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(8, 41, 42), 14)
        }
        operatorRow.addView(emblem, LinearLayout.LayoutParams(dp(56), dp(56)))
        operatorRow.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        val operatorDetails = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textLine(known?.displayName ?: operator, 17f, ink, true))
            addView(verticalGap(3))
            addView(textLine("Мобильный баланс", 13f, muted))
        }
        operatorRow.addView(operatorDetails, LinearLayout.LayoutParams(0, -2, 1f))

        val amountView = textLine(
            cached?.let { it.first + " ₽" } ?: "Нет данных",
            if (cached == null) 17f else 25f, Color.rgb(23, 55, 33), true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(
                if (isNight) Color.rgb(191, 239, 104) else Color.rgb(216, 241, 166), 15
            )
        }
        val wideScreen = resources.configuration.screenWidthDp >= 400
        if (wideScreen) {
            operatorRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            operatorRow.addView(amountView)
        }
        mobile.addView(operatorRow)
        if (!wideScreen) {
            mobile.addView(verticalGap(10))
            val amountRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            amountRow.addView(amountView)
            mobile.addView(amountRow)
        }

        // Keep the balances dashboard uncluttered. Operator diagnostics and
        // USSD controls are available from Settings -> Mobile connection.
        mobile.setOnClickListener {
            operatorDetailsOpen = true
            renderTab()
        }
        contentContainer.addView(mobile)
        contentContainer.addView(verticalGap(14))

        // This is a real preference, not a decorative settings label.
        if (selectedSim != null && selectedSim.isT2 &&
            preferences.getBoolean("mobile_auto_refresh_on_open", true) &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            T2Balance.check(this, selectedSim, false) { _ ->
                runOnUiThread {
                    syncT2Payment(selectedSim)
                    if (currentTab == 1 && operatorDetailsOpen) renderTab()
                    if (currentTab == 1 && !operatorDetailsOpen) {
                        T2Balance.cached(this, selectedSim.subscriptionId)?.let {
                            amountView.text = it.first + " ₽"
                            amountView.textSize = 25f
                        }
                    }
                }
            }
        }
        val bank = card()
        bank.addView(textLine("Банковские счета", 17f, ink, true))
        bank.addView(verticalGap(6))
        bank.addView(textLine("Подключение банковских счетов появится позже.", 13f, muted))
        contentContainer.addView(bank)
    }


    private fun formatT2Date(instant: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("ru")).format(Date(instant))

    private fun formatT2DateTime(instant: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", java.util.Locale("ru")).format(Date(instant))

    private fun t2DueAt(sim: MobileSim): Long? {
        val id = sim.subscriptionId
        val manuallyChosen = preferences.getLong("t2_manual_debit_$id", 0L).takeIf { it > 0 }
        val estimated = T2UssdParser.estimatedNextDebit(
            T2Balance.packages(this, id)?.packagesExpireAt
        )
        val firstDue = manuallyChosen ?: estimated ?: return null
        val date = Calendar.getInstance().apply { timeInMillis = firstDue }
        while (date.timeInMillis < System.currentTimeMillis()) date.add(Calendar.MONTH, 1)
        return date.timeInMillis
    }

    /** One stable ID per SIM prevents duplicate auto-generated reminders. */
    private fun syncT2Payment(sim: MobileSim?) {
        if (sim == null || !sim.isT2 || sim.subscriptionId < 0) return
        val id = sim.subscriptionId
        val paymentId = "t2_subscription_$id"
        val previous = payments.firstOrNull { it.id == paymentId }
        if (!preferences.getBoolean("t2_payment_enabled_$id", true)) {
            if (previous != null) {
                cancelReminder(previous)
                payments.removeAll { it.id == paymentId }
                persistPayments()
            }
            return
        }
        val fee = preferences.getString("t2_fee_$id", null)
            ?.trim()?.replace(',', '.')
        if (fee.isNullOrBlank() ||
            !fee.matches(Regex("""\d{1,7}(?:\.\d{1,2})?""")) ||
            fee.toBigDecimalOrNull()?.signum() != 1
        ) return
        val due = t2DueAt(sim) ?: previous?.dueAt ?: return
        val payment = PaymentReminder(
            paymentId, "Абонентская плата T2",
            fee, "RUB", due, 1, "monthly"
        )
        if (previous == payment) return
        if (previous != null) {
            cancelReminder(previous)
            payments.removeAll { it.id == paymentId }
        }
        payments.add(payment)
        persistPayments()
        scheduleReminder(payment)
    }

    private fun renderOperatorDetails() {
        contentContainer.addView(secondaryButton("‹  К балансам") {
            operatorDetailsOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(14))
        contentContainer.addView(textLine("T2 · Подробная информация", 23f, ink, true))
        contentContainer.addView(verticalGap(14))
        val selectedSim = T2Balance.sims(this).firstOrNull { it.isT2 }
        if (selectedSim == null) {
            contentContainer.addView(card().apply {
                addView(textLine("SIM-карта T2 не найдена", 16f, ink, true))
            })
            return
        }
        val id = selectedSim.subscriptionId
        val balance = T2Balance.cached(this, id)
        val due = t2DueAt(selectedSim)
        val fee = preferences.getString("t2_fee_$id", null)
        val manualDate = preferences.getLong("t2_manual_debit_$id", 0L) > 0L

        val summary = card()
        summary.addView(textLine("T2 (Tele2) · " + selectedSim.label, 18f, ink, true))
        summary.addView(verticalGap(6))
        summary.addView(textLine("Текущий известный баланс", 13f, muted))
        summary.addView(textLine(
            balance?.first?.let { it + " ₽" } ?: "Нет данных",
            28f, accent, true
        ))
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
        billing.addView(verticalGap(7))
        billing.addView(textLine(
            (if (manualDate) "Дата списания (указана вручную): "
             else "Предполагаемое списание: ") +
                (due?.let { formatT2Date(it) } ?: "нет данных"),
            14f, accent, true
        ))
        billing.addView(verticalGap(5))
        billing.addView(textLine(
            "Расчёт по дате окончания пакетов не подтверждён оператором.",
            12f, muted
        ))
        contentContainer.addView(billing)
        contentContainer.addView(verticalGap(12))

        val controls = card()
        controls.addView(textLine("Обновление и управление", 18f, ink, true))
        controls.addView(verticalGap(9))
        val status = textLine("Обновите данные USSD *105#", 13f, muted)
        controls.addView(status)
        controls.addView(verticalGap(10))
        controls.addView(primaryButton("Проверить T2 · *105#") {
            val callGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
            val stateGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (!callGranted || !stateGranted) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.CALL_PHONE,
                        android.Manifest.permission.READ_PHONE_STATE), 510
                )
                status.text = "Разрешите доступ к телефону и повторите запрос"
            } else {
                status.text = "Получаем данные T2…"
                T2Balance.check(this, selectedSim, true) { message ->
                    runOnUiThread {
                        syncT2Payment(selectedSim)
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        if (currentTab == 1 && operatorDetailsOpen) renderTab()
                    }
                }
            }
        })
        controls.addView(verticalGap(9))
        controls.addView(secondaryButton("Настроить абонентскую плату и дату") {
            operatorDetailsOpen = false
            mobileSettingsOpen = true
            selectTab(3)
        })
        contentContainer.addView(controls)
    }

    private fun renderPaymentScreen() {
        contentContainer.addView(textLine("Платежи", 23f, ink, true))
        contentContainer.addView(verticalGap(14))
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
            background = rounded(Color.rgb(233, 243, 242), 12)
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
        contentContainer.addView(verticalGap(22))
        contentContainer.addView(textLine("Предстоящие платежи", 19f, ink, true))
        contentContainer.addView(verticalGap(10))
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

        val mobileSettings = card()
        mobileSettings.addView(textLine("Мобильная связь   ›", 18f, ink, true))
        mobileSettings.addView(verticalGap(6))
        mobileSettings.addView(textLine(
            "SIM-карты, проверка баланса T2 и сведения об операторе",
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

        val appearanceCard = card()
        appearanceCard.addView(textLine("Оформление", 18f, ink, true))
        appearanceCard.addView(verticalGap(6))
        appearanceCard.addView(textLine("Тема приложения", 14f, muted))
        appearanceCard.addView(verticalGap(10))
        val selected = preferences.getString("appearance", "system") ?: "system"
        listOf(
            "system" to "Как в системе",
            "day" to "Светлая",
            "night" to "Тёмная"
        ).forEach { (value, label) ->
            val active = value == selected
            appearanceCard.addView(TextView(this).apply {
                text = (if (active) "✓  " else "     ") + label
                textSize = 16f
                setTextColor(if (active) accent else ink)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                if (active) {
                    setTypeface(null, Typeface.BOLD)
                    background = rounded(softAccent, 12)
                }
                setOnClickListener {
                    if (value != preferences.getString("appearance", "system")) {
                        preferences.edit().putString("appearance", value).apply()
                        AppCompatDelegate.setDefaultNightMode(when (value) {
                            "day" -> AppCompatDelegate.MODE_NIGHT_NO
                            "night" -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        })
                        recreate()
                    }
                }
            })
            appearanceCard.addView(verticalGap(2))
        }
        contentContainer.addView(appearanceCard)
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
    }

    private fun renderMobileSettings() {
        contentContainer.addView(secondaryButton("‹  Все настройки") {
            mobileSettingsOpen = false
            renderTab()
        })
        contentContainer.addView(verticalGap(18))
        contentContainer.addView(textLine("Мобильная связь", 23f, ink, true))
        contentContainer.addView(verticalGap(6))
        contentContainer.addView(textLine(
            "Информация о SIM и проверка баланса",
            14f, muted
        ))
        contentContainer.addView(verticalGap(16))

        val sims = T2Balance.sims(this)
        val selectedSim = sims.firstOrNull { it.isT2 } ?: sims.firstOrNull()
        val manager = getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as android.telephony.TelephonyManager
        val operator = selectedSim?.operator?.takeIf { it.isNotBlank() }
            ?: manager.simOperatorName?.takeIf { it.isNotBlank() }
            ?: "Оператор не определён"
        val cached = selectedSim?.let { T2Balance.cached(this, it.subscriptionId) }

        val simInfo = card()
        simInfo.addView(textLine("Информация об операторе", 18f, ink, true))
        simInfo.addView(verticalGap(10))
        simInfo.addView(textLine("Оператор: " + operator, 14f, ink))
        simInfo.addView(verticalGap(5))
        simInfo.addView(textLine("SIM: " + (selectedSim?.label ?: "не определена"), 14f, ink))
        simInfo.addView(verticalGap(5))
        simInfo.addView(textLine("Номер: не определён", 14f, muted))
        if (sims.size > 1) {
            simInfo.addView(verticalGap(5))
            simInfo.addView(textLine("Активных SIM-карт: " + sims.size, 14f, muted))
        }
        simInfo.addView(verticalGap(9))
        val cachedValue = textLine(
            "Последний баланс: " + (cached?.first?.let { it + " ₽" } ?: "нет данных"),
            15f, ink, true
        )
        simInfo.addView(cachedValue)
        val updateTime = textLine(
            cached?.let {
                "Обновлено: " + DateFormat.getDateTimeInstance().format(Date(it.second))
            } ?: "Пока нет успешных проверок",
            13f, muted
        )
        simInfo.addView(verticalGap(5))
        simInfo.addView(updateTime)
        simInfo.addView(verticalGap(10))
        simInfo.addView(textLine("Интернет и минуты: нет данных", 13f, muted))
        contentContainer.addView(simInfo)
        contentContainer.addView(verticalGap(14))

        val checking = card()
        checking.addView(textLine("Проверка баланса", 18f, ink, true))
        checking.addView(verticalGap(6))
        checking.addView(textLine(
            "USSD-запрос T2: *105#. Результат зависит от сети и устройства.",
            13f, muted
        ))
        checking.addView(verticalGap(10))
        val status = textLine(
            "Для обновления нажмите кнопку ниже",
            13f, accent
        )
        checking.addView(status)
        checking.addView(verticalGap(12))
        if (selectedSim != null && selectedSim.isT2) {
            checking.addView(primaryButton("Проверить баланс T2") {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.CALL_PHONE
                    ) != PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.READ_PHONE_STATE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            android.Manifest.permission.CALL_PHONE,
                            android.Manifest.permission.READ_PHONE_STATE
                        ), 510
                    )
                    status.text = "Разрешите доступ к телефону и повторите проверку"
                } else {
                    status.text = "Проверяем баланс T2…"
                    T2Balance.check(this, selectedSim, true) { message ->
                        runOnUiThread {
                            syncT2Payment(selectedSim)
                            status.text = message
                            T2Balance.cached(this, selectedSim.subscriptionId)?.let {
                                cachedValue.text = "Последний баланс: " + it.first + " ₽"
                                updateTime.text = "Обновлено: " +
                                    DateFormat.getDateTimeInstance().format(Date(it.second))
                            }
                        }
                    }
                }
            })
        } else {
            status.text = "Автоматический USSD сейчас доступен только для T2"
        }
        contentContainer.addView(checking)
        contentContainer.addView(verticalGap(14))


        if (selectedSim != null && selectedSim.isT2) {
            val id = selectedSim.subscriptionId
            val billingCard = card()
            billingCard.addView(textLine("Абонентская плата T2", 18f, ink, true))
            billingCard.addView(verticalGap(7))
            billingCard.addView(textLine(
                "Укажите стоимость тарифа вручную. Она не берётся из баланса.",
                13f, muted
            ))
            billingCard.addView(verticalGap(8))
            val feeInput = field("Абонентская плата, ₽").apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(preferences.getString("t2_fee_$id", ""))
            }
            billingCard.addView(feeInput)
            billingCard.addView(verticalGap(9))
            billingCard.addView(primaryButton("Сохранить сумму") {
                val fee = feeInput.text.toString().trim().replace(',', '.')
                if (!fee.matches(Regex("""\d{1,7}(?:\.\d{1,2})?""")) ||
                    fee.toBigDecimalOrNull()?.signum() != 1
                ) {
                    Toast.makeText(this,
                        "Введите сумму больше нуля, максимум 2 знака после запятой",
                        Toast.LENGTH_LONG).show()
                } else {
                    preferences.edit().putString("t2_fee_$id", fee).apply()
                    syncT2Payment(selectedSim)
                    Toast.makeText(this, "Абонентская плата сохранена", Toast.LENGTH_SHORT).show()
                    requestNotificationPermissionIfNeeded()
                    renderTab()
                }
            })
            billingCard.addView(verticalGap(14))
            val manualDate = preferences.getLong("t2_manual_debit_$id", 0L)
            billingCard.addView(textLine(
                "Следующее списание: " +
                    (t2DueAt(selectedSim)?.let { formatT2Date(it) } ?: "нет данных"),
                15f, ink, true
            ))
            billingCard.addView(verticalGap(5))
            billingCard.addView(textLine(
                if (manualDate > 0L) "Дата установлена вручную"
                else "Расчёт по окончанию пакетов (если указано 23:59)",
                12f, muted
            ))
            billingCard.addView(verticalGap(10))
            billingCard.addView(secondaryButton("Указать дату списания вручную") {
                val selectedDate = Calendar.getInstance().apply {
                    timeInMillis = t2DueAt(selectedSim) ?: System.currentTimeMillis()
                }
                showThemedCalendar(selectedDate) {
                    preferences.edit().putLong(
                        "t2_manual_debit_$id", selectedDate.timeInMillis
                    ).apply()
                    syncT2Payment(selectedSim)
                    renderTab()
                }
            })
            if (manualDate > 0L) {
                billingCard.addView(verticalGap(6))
                billingCard.addView(secondaryButton("Вернуть расчётную дату") {
                    preferences.edit().remove("t2_manual_debit_$id").apply()
                    syncT2Payment(selectedSim)
                    renderTab()
                })
            }
            billingCard.addView(verticalGap(12))
            val enabledRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            enabledRow.addView(textLine(
                "Добавлять в предстоящие платежи", 14f, ink
            ), LinearLayout.LayoutParams(0, -2, 1f))
            enabledRow.addView(androidx.appcompat.widget.SwitchCompat(this).apply {
                isChecked = preferences.getBoolean("t2_payment_enabled_$id", true)
                contentDescription = "Автоматически создавать платёж T2"
                setOnCheckedChangeListener { _, enabled ->
                    preferences.edit().putBoolean("t2_payment_enabled_$id", enabled).apply()
                    syncT2Payment(selectedSim)
                    if (enabled) requestNotificationPermissionIfNeeded()
                }
            })
            billingCard.addView(enabledRow)
            billingCard.addView(verticalGap(8))
            billingCard.addView(textLine(
                "Повторение — ежемесячно. Уведомление — за 1 день. " +
                    "При обновлении USSD дата уточняется без дубликатов.",
                12f, muted
            ))
            contentContainer.addView(billingCard)
            contentContainer.addView(verticalGap(14))
        }

        val refreshCard = card()
        refreshCard.addView(textLine("Автоматическое обновление", 18f, ink, true))
        refreshCard.addView(verticalGap(8))
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val switchLabel = textLine(
            "Проверять баланс при открытии вкладки «Балансы»", 14f, ink
        )
        switchRow.addView(switchLabel, LinearLayout.LayoutParams(0, -2, 1f))
        val autoRefreshSwitch = androidx.appcompat.widget.SwitchCompat(this).apply {
            isChecked = preferences.getBoolean("mobile_auto_refresh_on_open", true)
            contentDescription = "Проверять баланс автоматически при открытии вкладки"
            setOnCheckedChangeListener { _, enabled ->
                preferences.edit().putBoolean(
                    "mobile_auto_refresh_on_open", enabled
                ).apply()
            }
        }
        switchRow.addView(autoRefreshSwitch)
        refreshCard.addView(switchRow)
        refreshCard.addView(verticalGap(8))
        refreshCard.addView(textLine(
            "Повторные запросы ограничены. Фоновая проверка пока не настроена.",
            12f, muted
        ))
        contentContainer.addView(refreshCard)
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
        renderPayments()
        titleInput.text.clear()
        amountInput.text.clear()
        Toast.makeText(this, "Напоминание сохранено", Toast.LENGTH_SHORT).show()
        requestNotificationPermissionIfNeeded()
    }

    private fun renderPayments() {
        if (!::listContainer.isInitialized || currentTab != 2) return
        listContainer.removeAllViews()
        if (payments.isEmpty()) {
            val empty = card()
            empty.addView(textLine("Пока нет напоминаний", 15f, ink, true))
            empty.addView(textLine("Добавьте первый платёж выше.", 13f, muted))
            listContainer.addView(empty)
            return
        }
        payments.sortedBy { it.dueAt }.forEach { payment ->
            val item = card()
            item.addView(textLine(payment.title, 17f, ink, true))
            item.addView(verticalGap(6))
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(payment.dueAt))
            item.addView(textLine("${PaymentCurrencyFormatter.amountWithCurrency(payment.amount, payment.currency)} · оплатить $date", 14f, muted))
            item.addView(textLine("Напомнить за ${payment.leadDays} дн. · ${recurrenceLabel(payment.recurrence)}", 13f, muted))
            item.addView(verticalGap(8))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (payment.id.startsWith("t2_subscription_")) {
                actions.addView(secondaryButton("Настроить T2") {
                    mobileSettingsOpen = true
                    selectTab(3)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
                actions.addView(secondaryButton("Отключить") {
                    val id = payment.id.removePrefix("t2_subscription_")
                    preferences.edit().putBoolean("t2_payment_enabled_" + id, false).apply()
                    cancelReminder(payment)
                    payments.removeAll { it.id == payment.id }
                    persistPayments()
                    renderPayments()
                }, LinearLayout.LayoutParams(0, -2, 1f))
            } else {
                actions.addView(secondaryButton("Изменить") {
                    showEditPaymentDialog(payment)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
                actions.addView(secondaryButton("Удалить") {
                    showDeletePaymentDialog(payment)
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            item.addView(actions)
            listContainer.addView(item)
            listContainer.addView(verticalGap(10))
        }
    }

    private fun showDeletePaymentDialog(payment: PaymentReminder) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = rounded(surface, 22)
        }
        panel.addView(textLine("Удалить платёж?", 21f, ink, true))
        panel.addView(verticalGap(12))
        panel.addView(textLine("«${payment.title}» будет удалён без возможности восстановления.", 15f, muted))
        panel.addView(verticalGap(24))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        buttons.addView(secondaryButton("Отмена") { dialog.dismiss() },
            LinearLayout.LayoutParams(0, dp(52), 1f))
        buttons.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        buttons.addView(primaryButton("Удалить") {
            payments.removeAll { it.id == payment.id }
            cancelReminder(payment)
            if (editingId == payment.id) editingId = null
            persistPayments()
            renderPayments()
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        panel.addView(buttons)
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(rounded(surface, 22)) }
        dialog.show()
    }

    private fun showThemedCalendar(selected: Calendar, onSelected: () -> Unit) {
        val visible = (selected.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val chosen = selected.clone() as Calendar
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(surface, 24)
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
        val dialog = AlertDialog.Builder(this).setView(wrapper).create()
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
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(rounded(surface, 24)) }
        dialog.show()
    }

    private fun showEditPaymentDialog(payment: PaymentReminder) {
        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
            background = rounded(surface, 18)
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
        val dialog = AlertDialog.Builder(this)
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
            dialog.window?.setBackgroundDrawable(rounded(surface, 20))
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
        val triggerAt = payment.dueAt - payment.leadDays * 24L * 60L * 60L * 1000L
        if (triggerAt <= System.currentTimeMillis()) return
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, reminderPendingIntent(payment))
    }

    private fun cancelReminder(payment: PaymentReminder) {
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
                val isT2 = item.getString("id").startsWith("t2_subscription_")
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
        }
        if (advanced) persistPayments()
    }

    private fun persistPayments() {
        val array = JSONArray()
        payments.forEach { payment ->
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
