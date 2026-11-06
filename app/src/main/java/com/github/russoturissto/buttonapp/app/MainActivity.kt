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
    private var currentTab = 0
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
        loadPayments()
        showDashboard()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("current_tab", currentTab)
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
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setBackgroundColor(accent)
        }
        header.addView(textLine("BalanceHub", 21f, Color.WHITE, true))
        // Use a stable status-bar height. Insets may be dispatched as zero when
        // the view hierarchy is recreated during bottom-tab navigation.
        header.setPadding(dp(20), statusBarHeight() + dp(12), dp(20), dp(12))
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
        names.forEachIndexed { index, name ->
            tabs.addView(TextView(this).apply {
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
                setOnClickListener {
                    currentTab = index
                    showDashboard()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        // A constant navigation-bar inset prevents the bottom tabs from
        // jumping when Android re-dispatches insets after tab changes.
        tabs.setPadding(dp(6), dp(8), dp(6), navigationBarHeight() + dp(8))
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
            item.addView(textLine("${payment.amount} ${payment.currency} · " +
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(payment.dueAt)), 14f, muted))
            contentContainer.addView(item)
            contentContainer.addView(verticalGap(9))
        }
        contentContainer.addView(verticalGap(12))
        contentContainer.addView(primaryButton("Все платежи") {
            currentTab = 2
            showDashboard()
        })
    }

    private fun renderBalances() {
        contentContainer.addView(textLine("Балансы", 23f, ink, true))
        contentContainer.addView(verticalGap(8))
        contentContainer.addView(textLine("Мобильная связь · информационный вид", 14f, muted))
        contentContainer.addView(verticalGap(14))
        val mobile = card()
        mobile.addView(textLine("Мобильная связь", 19f, ink, true))
        mobile.addView(verticalGap(10))
        val sims = T2Balance.sims(this)
        val selectedSim = sims.firstOrNull { it.isT2 } ?: sims.firstOrNull()
        val manager = getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as android.telephony.TelephonyManager
        val operator = selectedSim?.operator?.takeIf { it.isNotBlank() } ?: manager.simOperatorName?.takeIf { it.isNotBlank() }
            ?: "Оператор не определён"
        val operatorCode = manager.simOperator.orEmpty()
        val known = RussianMobileOperators.find(operatorCode, operator)
        val operatorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val emblem = textLine(known?.shortLabel ?: "SIM", 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(8, 41, 42), 14)
        }
        operatorRow.addView(emblem, LinearLayout.LayoutParams(dp(66), dp(62)))
        operatorRow.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(textLine(known?.displayName ?: operator, 17f, ink, true))
        details.addView(textLine("Номер: не определён", 13f, muted))
        details.addView(textLine("Оператор SIM · $operatorCode", 12f, muted))
        operatorRow.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        mobile.addView(operatorRow)
        mobile.addView(verticalGap(16))
        mobile.addView(textLine("Баланс", 13f, muted))
        val cached = selectedSim?.let { T2Balance.cached(this, it.subscriptionId) }
        val amountView = textLine(cached?.let { "${it.first} ₽" } ?: "Нет данных", 23f, ink, true)
        mobile.addView(amountView)
        if (cached != null) mobile.addView(textLine("Обновлено: " + DateFormat.getDateTimeInstance().format(Date(cached.second)), 12f, muted))
        mobile.addView(verticalGap(10))
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val internet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(softAccent, 12)
            addView(textLine("Интернет", 13f, ink, true))
            addView(textLine("Нет данных", 12f, muted))
        }
        val minutes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(softAccent, 12)
            addView(textLine("Минуты", 13f, ink, true))
            addView(textLine("Нет данных", 12f, muted))
        }
        metrics.addView(internet, LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        metrics.addView(minutes, LinearLayout.LayoutParams(0, -2, 1f))
        mobile.addView(metrics)
        mobile.addView(verticalGap(12))
        mobile.addView(textLine("Проверка при открытии: включена · В фоне: каждые 24 ч", 12f, muted))
        mobile.addView(verticalGap(8))
        val status = textLine("USSD T2: *105# · ожидание проверки", 12f, accent)
        mobile.addView(status)
        if (selectedSim != null && selectedSim.isT2) {
            fun runCheck(force: Boolean) {
                status.text = "Проверяем баланс T2…"
                T2Balance.check(this, selectedSim, force) { message ->
                    runOnUiThread {
                        status.text = message
                        T2Balance.cached(this, selectedSim.subscriptionId)?.let { amountView.text = "${it.first} ₽" }
                    }
                }
            }
            mobile.addView(verticalGap(8))
            mobile.addView(primaryButton("Проверить баланс T2") {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this,
                        arrayOf(android.Manifest.permission.CALL_PHONE, android.Manifest.permission.READ_PHONE_STATE), 510)
                } else runCheck(true)
            })
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                runCheck(false)
            } else status.text = "Разрешите телефонные вызовы кнопкой ниже"
        }
        mobile.addView(verticalGap(12))
        mobile.addView(secondaryButton("Настроить проверку") {
            Toast.makeText(this, "Настройки → Мобильная связь", Toast.LENGTH_SHORT).show()
            currentTab = 3
            showDashboard()
        })
        contentContainer.addView(mobile)
        contentContainer.addView(verticalGap(14))
        val bank = card()
        bank.addView(textLine("Банковские счета", 17f, ink, true))
        bank.addView(verticalGap(6))
        bank.addView(textLine("Подключение банковских счетов появится позже.", 13f, muted))
        contentContainer.addView(bank)
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
        contentContainer.addView(textLine("Настройки", 23f, ink, true))
        contentContainer.addView(verticalGap(16))
        val appearanceCard = card()
        appearanceCard.addView(textLine("Оформление", 18f, ink, true))
        appearanceCard.addView(verticalGap(6))
        appearanceCard.addView(textLine("Выберите удобный режим отображения", 14f, muted))
        appearanceCard.addView(verticalGap(12))
        val selected = preferences.getString("appearance", "system") ?: "system"
        listOf(
            "system" to "Как в системе",
            "day" to "День",
            "night" to "Ночь"
        ).forEach { (value, label) ->
            val active = value == selected
            appearanceCard.addView(TextView(this).apply {
                text = (if (active) "✓  " else "     ") + label
                textSize = 16f
                setTextColor(if (active) accent else ink)
                setPadding(dp(14), dp(13), dp(14), dp(13))
                if (active) {
                    setTypeface(null, Typeface.BOLD)
                    background = rounded(softAccent, 12)
                }
                setOnClickListener {
                    if (value != preferences.getString("appearance", "system")) {
                        preferences.edit().putString("appearance", value).apply()
                        val mode = when (value) {
                            "day" -> AppCompatDelegate.MODE_NIGHT_NO
                            "night" -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                        AppCompatDelegate.setDefaultNightMode(mode)
                        recreate()
                    }
                }
            })
            appearanceCard.addView(verticalGap(3))
        }
        contentContainer.addView(appearanceCard)
        contentContainer.addView(verticalGap(14))
        val mobileSettings = card()
        mobileSettings.addView(textLine("Мобильная связь", 18f, ink, true))
        mobileSettings.addView(verticalGap(8))
        mobileSettings.addView(textLine("Автоматическая проверка при открытии: включена", 14f, ink))
        mobileSettings.addView(verticalGap(5))
        mobileSettings.addView(textLine("Фоновая проверка: каждые 24 часа", 14f, ink))
        mobileSettings.addView(verticalGap(5))
        mobileSettings.addView(textLine("Только Wi-Fi: нет", 14f, ink))
        mobileSettings.addView(verticalGap(9))
        mobileSettings.addView(textLine("Параметры согласованы. USSD и фоновое расписание будут подключены отдельно после проверки совместимости.", 12f, muted))
        contentContainer.addView(mobileSettings)
        contentContainer.addView(verticalGap(14))
        val notificationCard = card()
        notificationCard.addView(textLine("Уведомления", 18f, ink, true))
        notificationCard.addView(verticalGap(7))
        notificationCard.addView(textLine("Проверьте, что уведомления отображаются на телефоне.", 14f, muted))
        notificationCard.addView(verticalGap(15))
        notificationCard.addView(primaryButton("Проверить уведомление") { sendTestNotification() })
        contentContainer.addView(notificationCard)
        contentContainer.addView(verticalGap(14))
        val info = card()
        info.addView(textLine("О приложении", 17f, ink, true))
        info.addView(verticalGap(6))
        info.addView(textLine("BalanceHub · управление платежами и балансами", 13f, muted))
        contentContainer.addView(info)
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
        dateButton.text = "Дата оплаты: " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(dueDate.time)
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
            item.addView(textLine("${payment.amount} ${payment.currency} · оплатить ${date}", 14f, muted))
            item.addView(textLine("Напомнить за ${payment.leadDays} дн. · ${recurrenceLabel(payment.recurrence)}", 13f, muted))
            item.addView(verticalGap(8))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(secondaryButton("Изменить") {
                showEditPaymentDialog(payment)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            actions.addView(secondaryButton("Удалить") {
                showDeletePaymentDialog(payment)
            }, LinearLayout.LayoutParams(0, -2, 1f))
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
                while (date.timeInMillis - lead * 86400000L <= System.currentTimeMillis()) {
                    if (recurrence == "weekly") date.add(Calendar.DAY_OF_YEAR, 7)
                    else date.add(Calendar.MONTH, 1)
                    advanced = true
                    paymentAdvanced = true
                }
            }
            val payment = PaymentReminder(item.getString("id"), item.getString("title"),
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

    private fun navigationBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private data class PaymentReminder(
    val id: String, val title: String, val amount: String, val currency: String,
    val dueAt: Long, val leadDays: Int, val recurrence: String = "once"
)
