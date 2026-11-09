package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit

/** Exact known Android app package names. No QUERY_ALL_PACKAGES permission. */
internal data class RussianBank(
    val id: String, val name: String, val packages: List<String>, val monogram: String
)

internal object RussianBankCatalog {
    // Presence is not proof of an app's publisher/signing identity or a readable Push balance.
    val banks: List<RussianBank> = listOf(
        RussianBank("sber", "Сбербанк", listOf("ru.sberbankmobile"), "С"),
        RussianBank("tbank", "Т-Банк", listOf("com.idamob.tinkoff.android"), "Т"),
        RussianBank("vtb", "ВТБ", listOf("ru.vtb24.mobilebanking.android"), "В"),
        RussianBank("alfa", "Альфа-Банк", listOf("ru.alfabank.mobile.android"), "А"),
        RussianBank("raiffeisen", "Райффайзенбанк", listOf("ru.raiffeisennews"), "Р"),
        RussianBank("gazprom", "Газпромбанк", listOf("ru.gazprombank.android.mobilebank.app", "ru.gazprombank.android"), "Г"),
        RussianBank("psb", "ПСБ", listOf("ru.psbank.android"), "П"),
        RussianBank("sovcom", "Совкомбанк", listOf("ru.sovcomcard.halva.v1"), "С"),
        RussianBank("rshb", "Россельхозбанк", listOf("ru.rshb.dbo", "ru.rshb.digitalbank"), "Р"),
        RussianBank("mkb", "МКБ", listOf("ru.mkb.mobile"), "М"),
        RussianBank("unicredit", "ЮниКредит Банк", listOf("ru.ucb.android", "ru.unicredit.android"), "Ю"),
        RussianBank("sinara", "Банк Синара", listOf("ru.skbbank.ib"), "С"),
        RussianBank("gazenergo", "Газэнергобанк", listOf("ru.gebank.ib"), "Г"),
        RussianBank("rnbk", "РНКБ", listOf("com.bifit.rncbbeta"), "Р"),
        RussianBank("expo", "Экспобанк", listOf("ru.ftc.faktura.expobank"), "Э"),
        RussianBank("vbrr", "ВБРР", listOf("com.bssys.vbrrretail"), "В"),
        RussianBank("russia", "Банк Россия", listOf("ru.artsofte.russiafl"), "Р"),
        RussianBank("hlynov", "Хлынов", listOf("ru.bank_hlynov.xbank"), "Х"),
        RussianBank("blank", "Бланк Банк", listOf("ru.ftc.faktura.vesta"), "Б"),
        RussianBank("solidarnost", "Солидарность", listOf("com.isimplelab.ibank.solidarnost"), "С"),
        RussianBank("domrf", "Банк ДОМ.РФ", listOf("com.bssys.roscapretail"), "Д"),
        RussianBank("mts", "МТС Банк", listOf("ru.mts.money"), "М"),
        RussianBank("pskb", "ПСКБ", listOf("ru.ftc.faktura.pskb"), "П"),
        RussianBank("levober", "Левобережный", listOf("ru.ftc.faktura.nskbl"), "Л"),
        RussianBank("akbars", "Ак Барс Банк", listOf("ru.akbars.mobile"), "А"),
        RussianBank("rostfin", "РостФинанс", listOf("ru.ftc.faktura.rostfinance"), "Р"),
        RussianBank("bystr", "БыстроБанк", listOf("ru.ilb.bbmobile"), "Б"),
        RussianBank("diamond", "Алмазэргиэнбанк", listOf("ru.albank.online.aebit"), "А"),
        RussianBank("ic", "ИК Банк", listOf("com.bifit.mobile.citizen.icbru"), "И"),
        RussianBank("orenburg", "Банк Оренбург", listOf("ru.ftc.faktura.orbank"), "О"),
        RussianBank("agroprom", "Агропромкредит", listOf("ru.ftc.faktura.agropromkredit"), "А"),
        RussianBank("energobank", "Энергобанк", listOf("com.energobank.digital"), "Э"),
        RussianBank("koshelev", "КОШЕЛЕВ-БАНК", listOf("com.bifit.mobile.citizen.kbnk"), "К"),
        RussianBank("sdm", "СДМ-Банк", listOf("ru.ftc.faktura.sdm"), "С"),
        RussianBank("absolut", "Абсолют Банк", listOf("ru.ftc.faktura.absolutbank"), "А"),
        RussianBank("akcept", "Акцепт", listOf("ru.ftc.faktura.akcept"), "А"),
        RussianBank("energobank2", "Энерготрансбанк", listOf("ru.faktura.etbank.person"), "Э"),
        RussianBank("resobank", "РЕСО Кредит", listOf("ru.ftc.faktura.resokreditbank"), "Р"),
        RussianBank("bspb", "Банк Санкт-Петербург", listOf("ru.bspb"), "Б"),
        RussianBank("kuban", "Кубань Кредит", listOf("com.isimplelab.ibank.kubankredit"), "К"),
        RussianBank("tochka", "Точка", listOf("com.tochka.bank"), "Т"),
        RussianBank("novikom", "Новикомбанк", listOf("com.bssys.novikomretail"), "Н"),
        RussianBank("rosdor", "РосДорБанк", listOf("ru.rosdorbank.physical"), "Р"),
        RussianBank("primsoc", "Примсоцбанк", listOf("ru.ftc.faktura.primsoc"), "П"),
        RussianBank("saratov", "Банк Саратов", listOf("ru.ftc.faktura.banksaratov"), "С"),
        RussianBank("rosbank", "Росбанк", listOf("ru.rosbank.android"), "Р"),
        RussianBank("open", "Открытие", listOf("com.openbank"), "О"),
        RussianBank("pochta", "Почта Банк", listOf("ru.pochtabank"), "П"),
        RussianBank("yoomoney", "ЮMoney", listOf("ru.yoo.money"), "Ю"),
        RussianBank("otpb", "ОТП Банк", listOf("ru.otpbank.mobile"), "О"),
        RussianBank("uralsib", "Уралсиб", listOf("ru.uralsib.bank"), "У")
    )
    init {
        require(banks.map { it.id }.distinct().size == banks.size)
        require(banks.flatMap { it.packages }.distinct().size ==
            banks.sumOf { it.packages.size })
    }
    fun byId(id: String): RussianBank? = banks.firstOrNull { it.id == id }
    fun byPackage(packageName: String): RussianBank? =
        banks.firstOrNull { packageName in it.packages }

    fun installedPackage(context: Context, bank: RussianBank): String? =
        bank.packages.firstOrNull { name ->
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(name, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) { false }
        }
    fun available(context: Context): List<RussianBank> = banks.filter {
        it.id == "sber" || installedPackage(context, it) != null ||
            BankCardPreferences.enabled(context, it.id)
    }
}

/** Card visibility is separate from bank Push access and balance storage. */
internal object BankCardPreferences {
    private const val PREFS = "balancehub_bank_cards_v1"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun visible(context: Context, id: String): Boolean =
        prefs(context).getBoolean("visible_$id", true)
    fun setVisible(context: Context, id: String, visible: Boolean) {
        prefs(context).edit { putBoolean("visible_$id", visible) }
    }
    fun enabled(context: Context, id: String): Boolean =
        if (id == "sber") SberBalanceStore.isEnabled(context)
        else prefs(context).getBoolean("push_$id", false)

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        if (id == "sber") {
            SberBalanceStore.setEnabled(context, enabled)
            return
        }
        if (RussianBankCatalog.byId(id) == null) return
        prefs(context).edit { putBoolean("push_$id", enabled) }
        if (!enabled) RussianBankBalanceStore.clear(context, id)
    }
}
