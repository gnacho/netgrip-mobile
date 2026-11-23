package dev.gnacho.netgrip.domain.panel

import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SystemDto
import dev.gnacho.netgrip.data.dto.WanDto
import dev.gnacho.netgrip.data.dto.WirelessRadioDto
import kotlin.math.roundToInt

/** Tono del indicador de salud, igual que el panel web. */
enum class HealthTone { OK, WARN, DANGER }

/** Etiqueta de salud: excelente, buena, atencion o critica. */
enum class HealthLevel { EXCELLENT, GOOD, ATTENTION, CRITICAL }

/** Un motivo de penalizacion, con el dato que lo provoca. */
sealed interface HealthReason {
    data object NoInternet : HealthReason
    data class Memory(val usedPct: Int) : HealthReason
    data class Flash(val freePct: Int) : HealthReason
    data object Load : HealthReason
    data object WifiOff : HealthReason
}

data class HealthScore(
    val score: Int,
    val tone: HealthTone,
    val level: HealthLevel,
    val reasons: List<HealthReason>,
)

/**
 * Porta la logica de useHealthScore del panel web (app/src/hooks/
 * useHealthScore.ts) sobre los datos que trae esta app: WAN, RAM, flash,
 * carga y radios apagadas. Se quedan fuera el drift de configuracion y los
 * puertos fisicos degradados porque el movil no consulta esas fuentes.
 */
object HealthScoreCalculator {

    fun compute(
        system: SystemDto? = null,
        wan: WanDto? = null,
        mode: ModeDto? = null,
        wireless: List<WirelessRadioDto> = emptyList(),
    ): HealthScore {
        var score = 100
        val reasons = mutableListOf<HealthReason>()

        val isRouter = mode == null || mode.effectiveRole != "ap"

        if (isRouter && wan != null && wan.present && !wan.up) {
            score -= 40
            reasons += HealthReason.NoInternet
        }

        if (system != null && system.memory.total > 0) {
            val usedPct = percentOf(system.memory.total - system.memory.available, system.memory.total)
            when {
                usedPct >= 90 -> {
                    score -= 20
                    reasons += HealthReason.Memory(usedPct)
                }
                usedPct >= 75 -> {
                    score -= 10
                    reasons += HealthReason.Memory(usedPct)
                }
            }
        }

        if (system != null && system.root.total > 0) {
            val freePct = percentOf(system.root.free, system.root.total)
            when {
                freePct <= 10 -> {
                    score -= 20
                    reasons += HealthReason.Flash(freePct)
                }
                freePct <= 20 -> {
                    score -= 10
                    reasons += HealthReason.Flash(freePct)
                }
            }
        }

        val load1 = system?.load?.firstOrNull()
        if (load1 != null) {
            when {
                load1 >= 4.0 -> {
                    score -= 10
                    reasons += HealthReason.Load
                }
                load1 >= 2.0 -> {
                    score -= 5
                    reasons += HealthReason.Load
                }
            }
        }

        if (mode?.hasWifi == true && wireless.isNotEmpty() && wireless.all { !it.up }) {
            score -= 10
            reasons += HealthReason.WifiOff
        }

        score = score.coerceIn(0, 100)
        val tone = when {
            score >= 70 -> HealthTone.OK
            score >= 50 -> HealthTone.WARN
            else -> HealthTone.DANGER
        }
        val level = when {
            score >= 90 -> HealthLevel.EXCELLENT
            score >= 70 -> HealthLevel.GOOD
            score >= 50 -> HealthLevel.ATTENTION
            else -> HealthLevel.CRITICAL
        }
        return HealthScore(score, tone, level, reasons)
    }

    private fun percentOf(part: Long, total: Long): Int =
        if (total <= 0) 0 else ((part * 100.0) / total).roundToInt()
}
