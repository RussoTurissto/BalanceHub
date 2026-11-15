package com.github.russoturissto.buttonapp.app

/** A dedicated, bundled vector mark for each listed provider. */
internal enum class OperatorBrandMark {
    T2, MTS, MEGAFON, BEELINE, YOTA, SBER, TMOBILE, MIRANDA, MKS, MOTIV, LETAI, ROSTELECOM, VOLNA, WIN, SEV, PHOENIX, GPB, VTB, GAZPROM, ALFA, MCN, TINKOFF, DANYCOM, OSNOVA, UNKNOWN
}

internal object OperatorBrandMarks {
    fun fromOperator(operator: RussianMobileOperator?): OperatorBrandMark =
        when (operator?.displayName) {
            "T2 (Tele2)" -> OperatorBrandMark.T2
            "МТС" -> OperatorBrandMark.MTS
            "МегаФон" -> OperatorBrandMark.MEGAFON
            "Билайн" -> OperatorBrandMark.BEELINE
            "Yota" -> OperatorBrandMark.YOTA
            "СберМобайл" -> OperatorBrandMark.SBER
            "Т-Мобайл" -> OperatorBrandMark.TMOBILE
            "Миранда" -> OperatorBrandMark.MIRANDA
            "МКС (Лугаком)" -> OperatorBrandMark.MKS
            "Мотив" -> OperatorBrandMark.MOTIV
            "Летай" -> OperatorBrandMark.LETAI
            "Ростелеком" -> OperatorBrandMark.ROSTELECOM
            "Волна" -> OperatorBrandMark.VOLNA
            "Win Mobile" -> OperatorBrandMark.WIN
            "Севмобайл" -> OperatorBrandMark.SEV
            "Феникс" -> OperatorBrandMark.PHOENIX
            "ГПБ Мобайл" -> OperatorBrandMark.GPB
            "ВТБ Мобайл" -> OperatorBrandMark.VTB
            "Газпромбанк Мобайл" -> OperatorBrandMark.GAZPROM
            "Альфа-Мобайл" -> OperatorBrandMark.ALFA
            "MCN Telecom" -> OperatorBrandMark.MCN
            "Тинькофф Мобайл" -> OperatorBrandMark.TINKOFF
            "DANYCOM" -> OperatorBrandMark.DANYCOM
            "ОСНОВА Телеком" -> OperatorBrandMark.OSNOVA
            else -> OperatorBrandMark.UNKNOWN
        }
}
