package com.github.russoturissto.buttonapp.app

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import java.util.Locale

/** Shared offline icon catalogue for navigation, payments, events, archives and settings. */
internal data class AppIcon(val id: String, val label: String, val family: String, val keywords: List<String>)

internal object IconRegistry {
    private fun icon(id: String, label: String, family: String, words: String) =
        AppIcon(id, label, family, words.split('|').map(String::trim).filter(String::isNotEmpty))

    val all: List<AppIcon> = listOf(
        icon("calendar-days", "Календарь", "life", "событ|календар|дат|план"),
        icon("cake", "День рождения", "personal", "рожден|юбиле|именин|торт"),
        icon("gift", "Подарок", "personal", "подар|сюрприз"),
        icon("party", "Праздник", "personal", "праздн|вечерин|корпоратив|фестиваль"),
        icon("heart", "Близкие", "personal", "любов|свидан|годовщин|романтик"),
        icon("heart-pulse", "Здоровье", "health", "здоров|обследован|медосмотр"),
        icon("stethoscope", "Врач", "health", "врач|доктор|прием|приём|поликлиник"),
        icon("pill", "Лекарства", "health", "лекарств|таблетк|аптек|витамин"),
        icon("syringe", "Прививка", "health", "привив|вакцин|укол"),
        icon("tooth", "Стоматолог", "health", "стоматол|зуб|дантист"),
        icon("dumbbell", "Тренировка", "sport", "трениров|спортзал|фитнес|упражнен"),
        icon("bike", "Велосипед", "sport", "велосипед|велопрогул|вело"),
        icon("running", "Бег", "sport", "пробеж|марафон|бег"),
        icon("trophy", "Соревнование", "sport", "соревнован|турнир|чемпионат"),
        icon("graduation-cap", "Учёба", "work", "экзамен|зачет|зачёт|выпускн|диплом"),
        icon("book-open", "Книги", "work", "книг|чтен|библиотек"),
        icon("school", "Школа", "work", "школ|урок|заняти|университет"),
        icon("briefcase", "Работа", "work", "работ|офис|совещан|проект|командиров"),
        icon("users", "Встреча", "work", "встреч|собрани|коллег|гост"),
        icon("presentation", "Презентация", "work", "презентац|доклад|выступлен"),
        icon("plane", "Самолёт", "travel", "самолет|самолёт|авиабил|рейс|перелет|перелёт|аэропорт"),
        icon("train", "Поезд", "travel", "поезд|вокзал|электричк"),
        icon("bus", "Автобус", "travel", "автобус|остановк"),
        icon("car-front", "Автомобиль", "travel", "авто|машин|автомобил|гараж"),
        icon("fuel", "Топливо", "travel", "бензин|заправ|дизел|топлив"),
        icon("wrench", "Ремонт", "travel", "ремонт|сервис|замен|техосмотр|то машин|масл"),
        icon("map-pin", "Адрес", "travel", "адрес|место|геолокац|маршрут"),
        icon("suitcase", "Поездка", "travel", "отпуск|путешеств|поездк|отел|отель"),
        icon("home", "Дом", "home", "дом|квартир|жиль|аренд"),
        icon("key", "Ключи", "home", "ключ|замок|недвижим"),
        icon("hammer", "Инструменты", "home", "стройк|мебел|сборк"),
        icon("paintbrush", "Покраска", "home", "покраск|краск|дизайн"),
        icon("paw-print", "Питомец", "personal", "животн|питом|ветеринар|кот|кошк|собак"),
        icon("dog", "Собака", "personal", "собак|щен"),
        icon("cat", "Кошка", "personal", "кошк|котен|котён"),
        icon("baby", "Ребёнок", "personal", "ребен|ребён|малыш|детск"),
        icon("family", "Семья", "personal", "семь|родител|родствен"),
        icon("coffee", "Кофе", "food", "кофе|кафе|завтрак"),
        icon("utensils", "Ресторан", "food", "ресторан|ужин|обед|пицц"),
        icon("shopping-cart", "Покупки", "money", "покупк|супермаркет|продукт"),
        icon("shopping-bag", "Магазин", "money", "магазин|шопинг|заказ"),
        icon("wallet", "Кошелёк", "money", "кошелек|кошелёк|деньг|наличн"),
        icon("credit-card", "Карта", "money", "карт|банк|банков|кредит"),
        icon("banknote", "Оплата", "money", "оплат|платеж|платёж|перевод|расход"),
        icon("receipt", "Квитанция", "money", "чек|квитанц|налог|пошлин|штраф"),
        icon("wifi", "Интернет", "tech", "интернет|wifi|вайфай|роутер|провайдер"),
        icon("smartphone", "Мобильная связь", "tech", "телефон|мобильн|сотов|сим|sim|тариф|оператор|t2|мтс"),
        icon("phone", "Звонок", "tech", "звон|позвон|созвон"),
        icon("monitor", "Компьютер", "tech", "монитор|компьютер|экран|пк"),
        icon("laptop", "Ноутбук", "tech", "ноутбук|ноут"),
        icon("gamepad", "Игры", "tech", "игр|консол|приставк"),
        icon("music", "Музыка", "media", "музык|концерт|песня"),
        icon("film", "Кино", "media", "кино|фильм|театр|сериал"),
        icon("camera", "Фото", "media", "фото|фотограф|съёмк|съемк"),
        icon("sun", "День", "life", "солнеч|утро|погода"),
        icon("moon", "Вечер", "life", "вечер|ночь"),
        icon("flower", "Цветы", "personal", "цвет|букет|сад"),
        icon("tree", "Природа", "travel", "лес|поход|пикник|природ"),
        icon("umbrella", "Погода", "life", "дожд|зонт"),
        icon("shield-check", "Безопасность", "tech", "безопасн|парол|защит|страхов"),
        icon("bell", "Уведомление", "life", "напоминан|будильник|оповещен"),
        icon("alarm-clock", "Время", "life", "время|срок|дедлайн|будильник"),
        icon("archive", "Архив", "life", "архив|истори"),
        icon("check-circle", "Выполнено", "life", "готов|выполн|заверш"),
        icon("settings", "Настройки", "tech", "настро|параметр|опци"),
        icon("calendar-check", "Запланировано", "life", "запланир|записан|заброниров"),
        icon("ticket", "Билеты", "travel", "билет|музей|выставк"),
        icon("mail", "Почта", "tech", "почт|письм|посылк"),
        icon("sparkles", "Особое", "personal", "особенн|важн|интересн"),
        icon("trash", "Удалить", "life", "удал"),
        icon("pencil", "Редактировать", "life", "редактир|измен"),
        icon("plus", "Добавить", "life", "добав"),
        icon("chevron-down", "Развернуть", "life", "развер"),
        icon("chevron-up", "Свернуть", "life", "сверн")
    )

    private val byId = all.associateBy { it.id }
    fun find(id: String?): AppIcon? = byId[id]
    fun select(title: String, fallback: String = "calendar-days"): String {
        val words = title.lowercase(Locale.ROOT).replace('ё', 'е')
            .split(Regex("[^а-яa-z0-9]+")).filter(String::isNotBlank)
        var chosen: AppIcon? = null
        var top = 0
        for (item in all) {
            // Use the strongest matching concept rather than summing overlapping
            // synonyms (e.g. "авто" and "автомобил" should count once).
            val score = item.keywords.maxOfOrNull { term ->
                val tokens = term.lowercase(Locale.ROOT).replace('ё', 'е')
                    .split(' ').filter(String::isNotBlank)
                if (tokens.all { token -> words.any { word ->
                        word.startsWith(token) || (word.length >= 4 && token.startsWith(word))
                    } }) {
                    if (term in setOf("оплат", "платеж", "платёж", "напоминан",
                            "событ", "план", "важн")) 1
                    else 1 + tokens.size * 2 + term.length / 6 +
                        if (tokens.any { words.firstOrNull()?.startsWith(it) == true })
                            2 else 0
                } else 0
            } ?: 0
            if (score > top) { top = score; chosen = item }
        }
        return chosen?.id ?: fallback
    }

    fun tint(id: String, dark: Boolean): Int {
        val category = find(id)?.family ?: "life"
        val colors = when (category) {
            "personal" -> intArrayOf(201, 148, 228)
            "health" -> intArrayOf(245, 145, 157)
            "sport" -> intArrayOf(113, 212, 151)
            "work" -> intArrayOf(153, 169, 236)
            "travel" -> intArrayOf(118, 184, 237)
            "home" -> intArrayOf(237, 187, 118)
            "food" -> intArrayOf(243, 180, 125)
            "money" -> intArrayOf(183, 214, 116)
            "tech" -> intArrayOf(100, 205, 219)
            "media" -> intArrayOf(205, 158, 230)
            else -> intArrayOf(114, 203, 195)
        }
        return if (dark) Color.rgb(colors[0], colors[1], colors[2]) else
            Color.rgb((colors[0] * 0.68f).toInt(),
                (colors[1] * 0.68f).toInt(), (colors[2] * 0.68f).toInt())
    }
}

/** Uniform scalable pictograms. All artwork is locally rendered, no network dependency. */
internal class AppIconDrawable(private val key: String, private val shade: Int) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = shade; style = Paint.Style.STROKE; strokeWidth = 1.9f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private var opacity = 255
    private var filter: ColorFilter? = null
    @SuppressLint("UseKtx")
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / 24f, b.height() / 24f)
        pen.color = shade; pen.alpha = opacity; pen.colorFilter = filter
        fun l(x: Float, y: Float, a: Float, c: Float) =
            canvas.drawLine(x, y, a, c, pen)
        fun circ(x: Float, y: Float, r: Float) = canvas.drawCircle(x, y, r, pen)
        fun rect(x: Float, y: Float, a: Float, c: Float, r: Float = 2f) =
            canvas.drawRoundRect(RectF(x, y, a, c), r, r, pen)
        fun path(vararg nums: Float) {
            if (nums.size < 4) return
            val p = Path().apply { moveTo(nums[0], nums[1]) }
            var i = 2
            while (i + 1 < nums.size) { p.lineTo(nums[i], nums[i + 1]); i += 2 }
            canvas.drawPath(p, pen)
        }
        val type = when (key) {
            "baby", "family" -> "users"
            "cat", "dog" -> "paw-print"
            "syringe", "pill", "tooth", "stethoscope" -> "heart-pulse"
            "running", "bike", "trophy" -> "dumbbell"
            "school", "presentation" -> "graduation-cap"
            "bus", "train" -> "car-front"
            "fuel", "wrench", "hammer", "paintbrush" -> "wrench"
            "suitcase", "ticket" -> "plane"
            "key" -> "home"
            "coffee" -> "utensils"
            "shopping-bag", "shopping-cart" -> "shopping-cart"
            "wallet", "banknote", "receipt" -> "credit-card"
            "monitor", "laptop", "gamepad" -> "smartphone"
            "phone" -> "smartphone"
            "film", "camera" -> "music"
            "moon", "flower", "tree", "umbrella" -> "sun"
            "party", "sparkles" -> "gift"
            "calendar-check", "alarm-clock" -> "calendar-days"
            "check-circle", "shield-check" -> "check-circle"
            "pencil" -> "wrench"
            "mail" -> "archive"
            else -> key
        }
        when (type) {
            "calendar-days" -> { rect(3f, 5f, 21f, 21f); l(3f, 10f, 21f, 10f)
                ; l(8f, 3f, 8f, 7f); l(16f, 3f, 16f, 7f)
                ; circ(8f, 14f, 0.5f); circ(13f, 14f, 0.5f); circ(17f, 14f, 0.5f) }
            "archive" -> { rect(4f, 4f, 20f, 9f, 1f); rect(5f, 9f, 19f, 21f)
                ; l(9f, 13f, 15f, 13f) }
            "settings" -> { circ(12f, 12f, 4f); circ(12f, 12f, 8f)
                ; for (i in 0..7) { val a = i * Math.PI / 4.0
                    val x = kotlin.math.cos(a).toFloat(); val y = kotlin.math.sin(a).toFloat()
                    l(12 + 8*x, 12 + 8*y, 12 + 10*x, 12 + 10*y) } }
            "bell" -> { path(5f,17f,7f,15f,7f,10f,9f,6f,12f,5f,15f,6f,17f,10f,17f,15f,19f,17f)
                ; l(5f,17f,19f,17f); circ(12f,19f,1f) }
            "cake" -> { rect(4f, 12f, 20f, 21f, 1f); l(4f, 16f, 20f, 16f)
                ; l(8f, 7f, 8f, 12f); l(12f, 7f, 12f, 12f)
                ; l(16f, 7f, 16f, 12f); circ(12f, 5f, .5f) }
            "gift" -> { rect(4f, 10f, 20f, 21f, 1f); rect(3f, 7f, 21f, 10f, 1f)
                ; l(12f, 7f, 12f, 21f); path(12f,7f,7f,5f,8f,3f,12f,7f,16f,3f,17f,5f,12f,7f) }
            "heart", "heart-pulse" -> { path(12f,21f,4f,13f,4f,8f,7f,5f,10f,5f,12f,8f,14f,5f,17f,5f,20f,8f,20f,13f,12f,21f)
                ; if(type=="heart-pulse")path(5f,13f,9f,13f,11f,9f,13f,16f,15f,12f,19f,12f) }
            "dumbbell" -> { l(7f, 12f, 17f, 12f); l(5f, 8f, 5f, 16f); l(8f, 9f, 8f, 15f)
                ; l(16f, 9f, 16f, 15f); l(19f, 8f, 19f, 16f) }
            "graduation-cap" -> { path(2f,9f,12f,4f,22f,9f,12f,14f,2f,9f)
                ; l(6f,12f,6f,17f); path(6f,17f,12f,20f,18f,17f,18f,12f)
                ; l(22f,9f,22f,17f) }
            "book-open" -> { path(12f,20f,6f,18f,3f,18f,3f,5f,7f,5f,12f,7f)
                ; path(12f,20f,18f,18f,21f,18f,21f,5f,17f,5f,12f,7f,12f,20f) }
            "briefcase" -> { rect(3f, 8f, 21f, 20f); rect(8f, 4f, 16f, 8f)
                ; l(3f, 13f, 21f, 13f); l(11f, 12f, 13f, 12f) }
            "users" -> { circ(8f, 9f, 3f); circ(17f, 10f, 2.5f)
                ; canvas.drawArc(RectF(2f, 14f, 14f, 23f),180f,180f,false,pen)
                ; canvas.drawArc(RectF(12f, 15f, 23f, 22f),180f,180f,false,pen) }
            "plane" -> { path(3f,11f,10f,11f,16f,3f,19f,3f,15f,11f,21f,14f,21f,17f,14f,15f,10f,21f,7f,21f,9f,14f,3f,13f,3f,11f) }
            "car-front" -> { path(5f,10f,7f,5f,17f,5f,19f,10f,20f,19f,4f,19f,5f,10f)
                ; l(5f,10f,19f,10f); circ(7f,15f,1f); circ(17f,15f,1f) }
            "wrench" -> { path(20f,4f,17f,8f,14f,7f,13f,4f,9f,8f,9f,12f,4f,17f,7f,20f,12f,15f,16f,15f,20f,11f) }
            "home" -> { path(3f,10f,12f,3f,21f,10f,21f,21f,3f,21f,3f,10f)
                ; rect(9f,14f,15f,21f,0f) }
            "paw-print" -> { circ(7f,8f,1.5f); circ(12f,6f,1.5f); circ(17f,8f,1.5f)
                ; circ(19f,12f,1.2f); canvas.drawOval(RectF(7f,13f,17f,21f),pen) }
            "utensils" -> { l(6f,3f,6f,21f); l(3f,3f,3f,9f); l(9f,3f,9f,9f)
                ; l(3f,9f,9f,9f); l(17f,3f,17f,21f); path(17f,3f,21f,7f,21f,12f,17f,12f) }
            "shopping-cart" -> { path(3f,4f,5f,4f,8f,16f,19f,16f,21f,7f,6f,7f)
                ; circ(9f,20f,1f); circ(18f,20f,1f) }
            "credit-card" -> { rect(3f,5f,21f,19f); l(3f,10f,21f,10f)
                ; l(6f,15f,11f,15f) }
            "wifi" -> { canvas.drawArc(RectF(3f,5f,21f,21f),218f,104f,false,pen)
                ; canvas.drawArc(RectF(7f,9f,17f,18f),218f,104f,false,pen)
                ; circ(12f,19f,1f) }
            "smartphone" -> { rect(7f,2f,17f,22f); l(10f,5f,14f,5f)
                ; circ(12f,19f,.5f) }
            "music" -> { path(10f,18f,10f,5f,19f,3f,19f,15f)
                ; canvas.drawOval(RectF(5f,16f,10f,21f),pen)
                ; canvas.drawOval(RectF(14f,13f,19f,18f),pen) }
            "sun" -> { circ(12f,12f,4f)
                ; for(i in 0..7){val a=i*Math.PI/4;val x=kotlin.math.cos(a).toFloat();val y=kotlin.math.sin(a).toFloat()
                    l(12+7*x,12+7*y,12+9*x,12+9*y)} }
            "check-circle" -> { circ(12f,12f,9f); path(7f,12f,11f,16f,17f,9f) }
            "map-pin" -> { path(12f,22f,5f,12f,5f,8f,8f,4f,12f,3f,16f,4f,19f,8f,19f,12f,12f,22f)
                ; circ(12f,10f,2f) }
            "trash" -> { rect(6f,7f,18f,21f,0f); l(4f,6f,20f,6f);l(9f,3f,15f,3f)
                ; l(10f,11f,10f,18f); l(14f,11f,14f,18f) }
            "plus" -> { l(12f,4f,12f,20f);l(4f,12f,20f,12f) }
            "chevron-down" -> path(5f,9f,12f,16f,19f,9f)
            "chevron-up" -> path(5f,15f,12f,8f,19f,15f)
            else -> { rect(4f,4f,20f,20f);circ(12f,12f,4f) }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 24
    override fun getIntrinsicHeight(): Int = 24
}
