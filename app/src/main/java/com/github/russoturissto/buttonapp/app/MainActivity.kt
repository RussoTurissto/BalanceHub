package com.github.russoturissto.buttonapp.app

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
    private lateinit var listContainer: LinearLayout
    private var dueDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPayments()
        showDashboard()
    }

    private fun showDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "BalanceHub"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Баланс под контролем"
            textSize = 14f
            setPadding(0, 0, 0, dp(20))
        })
        root.addView(TextView(this).apply {
            text = "Новое напоминание о платеже"
            textSize = 20f
            setPadding(0, 0, 0, dp(12))
        })

        titleInput = field("Название платежа (например, Интернет)")
        amountInput = field("Сумма")
        amountInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        currencyInput = field("Валюта (например, RUB или EUR)").apply { setText("RUB") }
        leadDaysInput = field("Напомнить за сколько дней").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("3")
        }
        dateButton = Button(this).apply { setOnClickListener { chooseDate() } }
        updateDateButton()
        root.addView(titleInput)
        root.addView(amountInput)
        root.addView(currencyInput)
        root.addView(dateButton)
        root.addView(leadDaysInput)
        root.addView(Button(this).apply {
            text = "Сохранить напоминание"
            setOnClickListener { saveReminder() }
        })
        root.addView(TextView(this).apply {
            text = "Предстоящие платежи"
            textSize = 20f
            setPadding(0, dp(24), 0, dp(8))
        })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)
        setContentView(android.widget.ScrollView(this).apply { addView(root) })
        renderPayments()
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        setSingleLine(true)
        textSize = 16f
    }

    private fun chooseDate() {
        DatePickerDialog(this, { _, year, month, day ->
            dueDate = Calendar.getInstance().apply {
                set(year, month, day, 9, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            updateDateButton()
        }, dueDate.get(Calendar.YEAR), dueDate.get(Calendar.MONTH), dueDate.get(Calendar.DAY_OF_MONTH)).show()
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
        val reminder = PaymentReminder(UUID.randomUUID().toString(), title, amount, currency, dueDate.timeInMillis, leadDays)
        payments.add(reminder)
        persistPayments()
        scheduleReminder(reminder)
        renderPayments()
        titleInput.text.clear()
        amountInput.text.clear()
        Toast.makeText(this, "Напоминание сохранено", Toast.LENGTH_SHORT).show()
        requestNotificationPermissionIfNeeded()
    }

    private fun renderPayments() {
        if (!::listContainer.isInitialized) return
        listContainer.removeAllViews()
        if (payments.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Пока нет напоминаний. Добавьте первый платёж выше."
            })
            return
        }
        payments.sortedBy { it.dueAt }.forEach { payment ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            }
            item.addView(TextView(this).apply {
                text = payment.title
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            item.addView(TextView(this).apply {
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(payment.dueAt))
                text = "${payment.amount} ${payment.currency} · оплатить ${date} · напомнить за ${payment.leadDays} дн."
                textSize = 14f
            })
            item.addView(Button(this).apply {
                text = "Удалить"
                setOnClickListener {
                    payments.removeAll { it.id == payment.id }
                    cancelReminder(payment)
                    persistPayments()
                    renderPayments()
                }
            })
            listContainer.addView(item)
            listContainer.addView(TextView(this).apply { text = " " })
        }
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
        }
        return PendingIntent.getBroadcast(this, payment.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            payments.add(PaymentReminder(item.getString("id"), item.getString("title"),
                item.getString("amount"), item.getString("currency"), item.getLong("dueAt"), item.getInt("leadDays")))
        }
    }

    private fun persistPayments() {
        val array = JSONArray()
        payments.forEach { payment ->
            array.put(JSONObject().apply {
                put("id", payment.id); put("title", payment.title); put("amount", payment.amount)
                put("currency", payment.currency); put("dueAt", payment.dueAt); put("leadDays", payment.leadDays)
            })
        }
        preferences.edit().putString("payment_reminders", array.toString()).apply()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private data class PaymentReminder(
    val id: String, val title: String, val amount: String, val currency: String,
    val dueAt: Long, val leadDays: Int
)
