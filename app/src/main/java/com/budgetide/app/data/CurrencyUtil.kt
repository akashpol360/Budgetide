package com.budgetide.app.data

import java.util.Currency
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Picks the display currency from the device region and converts the app's
 * built-in INR demo values (sample data + calculator defaults) into it.
 *
 * Rates are APPROXIMATE (INR per 1 unit of the currency) and only used for
 * the starter/demo numbers - amounts the user types are never converted.
 * Update this table occasionally; unknown currencies fall back to INR.
 */
object CurrencyUtil {

    private val inrPerUnit = mapOf(
        "INR" to 1.0, "USD" to 90.0, "EUR" to 105.0, "GBP" to 120.0,
        "AED" to 24.5, "SAR" to 24.0, "QAR" to 24.7, "KWD" to 293.0, "BHD" to 239.0, "OMR" to 234.0,
        "AUD" to 59.0, "NZD" to 53.0, "CAD" to 65.0, "SGD" to 69.0, "HKD" to 11.5,
        "JPY" to 0.60, "CNY" to 12.5, "KRW" to 0.064, "TWD" to 2.9,
        "CHF" to 112.0, "SEK" to 9.5, "NOK" to 9.0, "DKK" to 14.0, "PLN" to 24.5,
        "CZK" to 4.1, "HUF" to 0.27, "TRY" to 2.1, "ILS" to 27.0, "RUB" to 1.1,
        "BRL" to 16.5, "MXN" to 4.9, "ARS" to 0.09, "CLP" to 0.095, "COP" to 0.023,
        "ZAR" to 5.0, "NGN" to 0.06, "KES" to 0.70, "EGP" to 1.8, "GHS" to 7.5,
        "PKR" to 0.32, "BDT" to 0.74, "LKR" to 0.30, "NPR" to 0.625,
        "MYR" to 21.0, "THB" to 2.7, "IDR" to 0.0055, "PHP" to 1.55, "VND" to 0.0036
    )

    /** Currency of the device region, or INR if unknown / unsupported. */
    fun active(): Currency {
        val locale = Locale.getDefault()
        return try {
            if (locale.country.isNullOrEmpty()) Currency.getInstance("INR")
            else Currency.getInstance(locale).takeIf { inrPerUnit.containsKey(it.currencyCode) }
                ?: Currency.getInstance("INR")
        } catch (_: Exception) {
            Currency.getInstance("INR")
        }
    }

    /** Converts an INR demo amount into the active currency, rounded to a tidy number. */
    fun fromInr(inr: Double): Double {
        val code = active().currencyCode
        if (code == "INR") return inr
        val v = inr / (inrPerUnit[code] ?: 1.0)
        val step = when {
            v < 20 -> 1.0
            v < 200 -> 5.0
            v < 2000 -> 10.0
            v < 20000 -> 50.0
            v < 200000 -> 500.0
            else -> 5000.0
        }
        return maxOf(step, (v / step).roundToLong() * step)
    }

    /** Same as [fromInr] but as a plain string for text-field defaults. */
    fun fromInrText(inr: Int): String = fromInr(inr.toDouble()).roundToLong().toString()
}
