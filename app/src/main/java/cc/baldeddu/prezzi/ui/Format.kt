package cc.baldeddu.prezzi.ui

import java.text.NumberFormat
import java.math.RoundingMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun euro(cents: Double): String = NumberFormat.getCurrencyInstance(Locale.ITALY).apply {
    roundingMode = RoundingMode.HALF_UP
}.format(BigDecimal.valueOf(cents).movePointLeft(2))
fun signedEuro(cents: Double): String = sign(cents) + euro(kotlin.math.abs(cents))
fun percent(value: Double): String = sign(value) + NumberFormat.getNumberInstance(Locale.ITALY).apply {
    minimumFractionDigits = 1; maximumFractionDigits = 1
    roundingMode = RoundingMode.HALF_UP
}.format(BigDecimal.valueOf(kotlin.math.abs(value))) + "%"
fun quantity(value: Double): String = NumberFormat.getNumberInstance(Locale.ITALY).apply {
    maximumFractionDigits = 3; roundingMode = RoundingMode.HALF_UP
}.format(BigDecimal.valueOf(value))
private fun sign(value: Double) = if (value > 0) "+" else if (value < 0) "−" else ""
fun day(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
fun updatedAt(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.of("Europe/Rome"))
    .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ITALIAN))
