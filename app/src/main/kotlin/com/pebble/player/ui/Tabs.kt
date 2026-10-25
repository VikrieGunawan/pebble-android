@Composable
private fun StatPill(icon: String, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .glass(radius = 50.dp, lens = true, tint = cs.surfaceContainerHigh.copy(alpha = 0.5f), borderAlpha = 0.35f)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = cs.primary)
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
