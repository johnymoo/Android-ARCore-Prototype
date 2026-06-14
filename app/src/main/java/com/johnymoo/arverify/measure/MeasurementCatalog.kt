package com.johnymoo.arverify.measure

/** Which sub-diagram a measurement is shown on. */
enum class DiagramView { TOP, SIDE }

/** A pickable enum value with display text (mirrors schema.ts). */
data class OptionItem(val value: String, val label: String, val hint: String, val icon: String = "")

/** One wizard step: a caliper field + its display + diagram target. */
data class FieldGuide(
    val key: String,
    val code: String,
    val label: String,
    val hint: String,
    val view: DiagramView,
)

/**
 * Display catalog for the parametric-block measurement wizard.
 * Mirrors BrickStudio/apps/web/src/features/parametric/schema.ts so the
 * Android app, the web wizard and the backend agree on labels and order.
 */
object MeasurementCatalog {
    const val MIN_UNITS = 1
    const val MAX_UNITS = 16

    val SYSTEMS = listOf(
        OptionItem("duplo", "得宝 (DUPLO)", "20 mm 节距, 1-5 岁"),
        OptionItem("lego", "乐高 (LEGO)", "8 mm 节距, 6+ 岁"),
        OptionItem("feile", "费乐 (FEILE)", "16 mm 节距, 国产大颗粒"),
        OptionItem("generic", "通用 / 自定义", "未知规格, 按比例缩"),
    )

    val KINDS = listOf(
        OptionItem("brick", "标准砖 (brick)", "凸点在顶, 标准高度", "🧱"),
        OptionItem("plate", "板 (plate)", "1/3 高度, 凸点在顶", "▭"),
        OptionItem("tile", "瓦片 (tile)", "1/3 高度, 平顶", "◼"),
        OptionItem("slope", "斜面 (slope)", "45° 楔形, 沿 X 方向降", "◣"),
    )

    /** The 5 caliper fields in canonical order; keys match _RAW_KEYS in the backend. */
    private val CANONICAL = listOf(
        FieldGuide(
            "outer_pitch_mm", "1A", "外径距 (outer_pitch)",
            "砖块总长，跨两端凸点圆周最远点（卡尺跨外）", DiagramView.TOP,
        ),
        FieldGuide(
            "inner_pitch_mm", "1B", "内径距 (inner_pitch)",
            "相邻两凸点之间最窄缝（卡尺插入两凸点之间）", DiagramView.TOP,
        ),
        FieldGuide(
            "stud_diameter_mm", "③", "凸点直径 (stud_diameter)",
            "单个凸点直径（任选一个，卡尺卡外径）", DiagramView.TOP,
        ),
        FieldGuide(
            "brick_height_net_mm", "②", "砖块净高 (brick_height_net)",
            "底面 → 砖顶，不含凸点", DiagramView.SIDE,
        ),
        FieldGuide(
            "brick_height_total_mm", "④", "砖块总高 (brick_height_total)",
            "底面 → 凸点顶，含凸点", DiagramView.SIDE,
        ),
    )

    private val BY_KEY = CANONICAL.associateBy { it.key }

    /** All 5 canonical keys (the order/contract the backend always requires). */
    val CANONICAL_KEYS: List<String> = CANONICAL.map { it.key }

    fun codeFor(key: String): String = BY_KEY[key]?.code ?: key

    /**
     * Map the backend's `needs_measurement.fields` (list of keys) to ordered
     * wizard steps. Empty -> the canonical 5. Unknown keys -> a fallback step
     * (raw key as code + label) so the wizard never crashes on a new field.
     */
    fun stepsFor(backendKeys: List<String>): List<FieldGuide> {
        if (backendKeys.isEmpty()) return CANONICAL
        return backendKeys.map { key ->
            BY_KEY[key] ?: FieldGuide(
                key = key, code = key, label = "$key（请按字段名测量）",
                hint = "后端要求的测量项", view = DiagramView.TOP,
            )
        }
    }
}
