package app.koma.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.ui.GroupRow
import app.koma.ui.Option
import app.koma.ui.OptionGroup
import app.koma.ui.PillShape
import app.koma.ui.SettingsCard
import app.koma.ui.SettingsGroup
import app.koma.ui.SettingsSwitchRow
import app.koma.ui.morphingShape
import app.koma.ui.theme.ColorStyle
import app.koma.ui.theme.ThemeMode
import app.koma.ui.theme.dynamicColorSupported

private val modes =
    listOf(
        Option(ThemeMode.System, R.drawable.ic_phone_android, "System", "theme-mode-system"),
        Option(ThemeMode.Light, R.drawable.ic_light_mode, "Light", "theme-mode-light"),
        Option(ThemeMode.Dark, R.drawable.ic_dark_mode, "Dark", "theme-mode-dark"),
    )

private val styles =
    mapOf(
        ColorStyle.Default to
            Option(ColorStyle.Default, R.drawable.ic_palette, "Default", "color-style-default"),
        ColorStyle.MaterialYou to
            Option(
                ColorStyle.MaterialYou,
                R.drawable.ic_auto_awesome,
                "Material You",
                "color-style-material-you",
            ),
        ColorStyle.Monochrome to
            Option(
                ColorStyle.Monochrome,
                R.drawable.ic_contrast,
                "Monochrome",
                "color-style-monochrome",
            ),
    )

private class Accent(val name: String, val color: Color)

// Around the color wheel, the muted ones last.
private val accents =
    listOf(
        Accent("Red", Color(0xFFD32F2F)),
        Accent("Deep orange", Color(0xFFE64A19)),
        Accent("Orange", Color(0xFFF57C00)),
        Accent("Amber", Color(0xFFFFB300)),
        Accent("Lime", Color(0xFF9E9D24)),
        Accent("Green", Color(0xFF43A047)),
        Accent("Teal", Color(0xFF00897B)),
        Accent("Cyan", Color(0xFF00ACC1)),
        Accent("Light blue", Color(0xFF039BE5)),
        Accent("Blue", Color(0xFF1E88E5)),
        Accent("Indigo", Color(0xFF3949AB)),
        Accent("Deep purple", Color(0xFF5E35B1)),
        Accent("Purple", Color(0xFF8E24AA)),
        Accent("Pink", Color(0xFFD81B60)),
        Accent("Brown", Color(0xFF6D4C41)),
        Accent("Blue grey", Color(0xFF546E7A)),
    )

@Composable
internal fun ThemeView(container: AppContainer) {
    val model = viewModel { ThemeViewModel(container.theme, dynamicColorSupported) }
    val page by model.page.collectAsStateWithLifecycle()
    SettingsCard(RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("Theme mode")
            OptionGroup(modes, page.mode, model::setMode)
            Heading("Color style")
            OptionGroup(page.styles.mapNotNull { styles[it] }, page.style, model::setStyle)
        }
    }
    SettingsGroup(
        listOf(
            GroupRow(page.accentShown) { shape ->
                SettingsCard(shape) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Heading("Accent color")
                        AccentGrid(page.accent, model::setAccent)
                    }
                }
            },
            GroupRow(page.trueBlackShown) { shape ->
                SettingsSwitchRow(
                    title = "True black",
                    icon = R.drawable.ic_contrast,
                    shape = shape,
                    tag = "true-black",
                    checked = page.trueBlack,
                    onCheckedChange = model::setTrueBlack,
                    subtitle = "Uses pure black backgrounds",
                )
            },
            GroupRow(page.coverColorsShown) { shape ->
                SettingsSwitchRow(
                    title = "Cover colors",
                    icon = R.drawable.ic_palette,
                    shape = shape,
                    tag = "cover-colors",
                    checked = page.coverColors,
                    onCheckedChange = model::setCoverColors,
                    subtitle = "Color titles from their cover",
                )
            },
        )
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private const val COLUMNS = 6
private val SwatchSpacing = 8.dp

/** No accent, then the presets, [COLUMNS] to a row. */
@Composable
private fun AccentGrid(selected: Int?, onSelect: (Int?) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val size = min(48.dp, (maxWidth - SwatchSpacing * (COLUMNS - 1)) / COLUMNS)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(SwatchSpacing, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(SwatchSpacing),
            maxItemsInEachRow = COLUMNS,
        ) {
            Swatch(null, selected == null, size, "No accent", "accent-none") { onSelect(null) }
            for (accent in accents) {
                val argb = accent.color.toArgb()
                Swatch(
                    accent.color,
                    selected == argb,
                    size,
                    accent.name,
                    "accent-%08X".format(argb),
                ) {
                    onSelect(argb)
                }
            }
        }
    }
}

private val SwatchShape = RoundedCornerShape(12.dp)

@Composable
private fun Swatch(
    color: Color?,
    selected: Boolean,
    size: Dp,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = morphingShape(if (selected) PillShape else SwatchShape, pressed)
    val width by animateDpAsState(if (selected) 3.dp else 1.dp)
    val edge =
        when {
            !selected -> colors.outline.copy(alpha = 0.5f)
            color == null -> colors.primary
            else -> lerp(color, Color.Black, 0.4f)
        }
    Box(
        Modifier.size(size)
            .clip(shape)
            .background(color ?: colors.surfaceVariant)
            .border(width, edge, shape)
            .selectable(
                selected,
                interactionSource = source,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) {
            Icon(
                painterResource(R.drawable.ic_format_color_reset),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
