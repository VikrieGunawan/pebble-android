@Composable
internal fun Btn(label: String, icon: String?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val radius = if (primary) 18.dp else 16.dp
    val tint = if (primary) cs.primary else cs.surfaceContainerHigh
    val fg = if (primary) cs.onPrimary else cs.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(radius = radius, lens = true, tint = tint, borderAlpha = if (primary) 0.65f else 0.4f)
            .clickable(onClick = onClick)
            .padding(vertical = if (primary) 15.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = fg)
        Text(
            label,
            Modifier.padding(start = if (icon != null) 8.dp else 0.dp),
            color = fg,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
