/** Rounded-square icon tile dengan efek Liquid Glass. */
@Composable
fun IconTile(icon: String, size: Dp = 44.dp, filled: Boolean = false, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val radius = size * 0.32f
    val tint = if (filled) cs.primary else cs.primaryContainer
    Box(
        modifier
            .size(size)
            .glass(radius = radius, lens = true, tint = tint, borderAlpha = 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = if (filled) cs.onPrimary else cs.primary)
    }
}

/** Grid / list switch dengan wadah kapsul Liquid Glass. */
@Composable
fun LayoutToggle(layout: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .glass(radius = 50.dp, lens = true, tint = cs.surfaceContainerHigh.copy(alpha = 0.35f), borderAlpha = 0.4f)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf("grid_view" to 0, "view_list" to 1).forEach { (ic, v) ->
            val on = layout == v
            Box(
                Modifier
                    .size(width = 46.dp, height = 38.dp)
                    .then(
                        if (on) {
                            Modifier.glass(radius = 50.dp, lens = true, tint = cs.primary, borderAlpha = 0.6f)
                        } else {
                            Modifier.clip(RoundedCornerShape(50)).background(Color.Transparent)
                        }
                    )
                    .clickable { onChange(v) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(ic, null, Modifier.size(22.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Crumb(icon: String, label: String, cur: Boolean, dim: Boolean, extra: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = if (cur) cs.primary else cs.surfaceContainerHigh.copy(alpha = 0.25f)
    val fg = if (cur) cs.onPrimary else cs.onSurfaceVariant
    Row(
        extra
            .height(36.dp)
            .alpha(if (dim) 0.5f else 1f)
            .glass(radius = 14.dp, lens = cur, tint = bg, borderAlpha = if (cur) 0.5f else 0.25f)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = fg)
        Text(label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
    }
}
