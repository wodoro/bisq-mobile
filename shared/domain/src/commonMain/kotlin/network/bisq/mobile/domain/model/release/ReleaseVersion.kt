package network.bisq.mobile.domain.model.release

/**
 * A dotted numeric version compared the way bisq2's `bisq.common.platform.Version` compares them:
 * part by part as integers, with missing trailing parts counting as zero, so "0.15" equals "0.15.0".
 * Anything else, such as a "v" prefix or an "-rc1" suffix, is not a version and does not parse.
 */
class ReleaseVersion private constructor(
    private val parts: List<Int>,
    private val text: String,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val result = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is ReleaseVersion && compareTo(other) == 0

    // Trailing zeros dropped so that versions equal under compareTo hash alike.
    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString(): String = text

    companion object {
        private val FORMAT = Regex("[0-9]+(\\.[0-9]+)*")

        fun parse(value: String): ReleaseVersion? {
            if (!FORMAT.matches(value)) return null
            // toIntOrNull rejects parts that overflow an Int, which bisq2 would reject as well.
            val parts = value.split('.').map { it.toIntOrNull() ?: return null }
            return ReleaseVersion(parts, value)
        }
    }
}
