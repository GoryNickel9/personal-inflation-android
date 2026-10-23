package cc.baldeddu.prezzi.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val colors = darkColorScheme(
    primary = Color(0xFFD9F28D), onPrimary = Color(0xFF233318),
    primaryContainer = Color(0xFF315B43), onPrimaryContainer = Color(0xFFE6F4DF),
    secondary = Color(0xFFA7D5AE), onSecondary = Color(0xFF163723),
    secondaryContainer = Color(0xFF2B4032), onSecondaryContainer = Color(0xFFD9E8DC),
    background = Color(0xFF142019), onBackground = Color(0xFFEDF2E9),
    surface = Color(0xFF1C2B22), onSurface = Color(0xFFEDF2E9),
    surfaceDim = Color(0xFF142019), surfaceBright = Color(0xFF2F4234),
    surfaceContainerLowest = Color(0xFF101912), surfaceContainerLow = Color(0xFF1C2B22),
    surfaceContainer = Color(0xFF223328), surfaceContainerHigh = Color(0xFF283B2D),
    surfaceContainerHighest = Color(0xFF304536),
    surfaceVariant = Color(0xFF293B2E), onSurfaceVariant = Color(0xFFC3CEC5),
    outline = Color(0xFF829386), outlineVariant = Color(0xFF415548),
    error = Color(0xFFF1A58E), onError = Color(0xFF531E11),
    errorContainer = Color(0xFF422B24), onErrorContainer = Color(0xFFFFDBCE),
)

@Composable
fun PrezziTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
    ), content = content)
}
