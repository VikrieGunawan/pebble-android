/** The website's `.nv`: NavButton dengan efek Liquid Glass Pill saat aktif. */
@Composable
private fun NavButton(icon: String, label: String, selected: Boolean, pillW: Dp, pillH: Dp, labelSp: Float, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.90f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "navsc")
    val tint by animateColorAsState(if (selected) cs.primary else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navtint")
    val txt by animateColorAsState(if (selected) cs.onSurface else cs.onSurfaceVariant, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navtxt")

    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(interactionSource = src, indication = null, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer { scaleX = sc; scaleY = sc }
                .size(pillW, pillH)
                .then(
                    if (selected) {
                        Modifier.glass(radius = pillH / 2, lens = true, tint = cs.primaryContainer, borderAlpha = 0.55f)
                    } else {
                        Modifier.clip(RoundedCornerShape(pillH / 2)).background(Color.Transparent)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = tint)
        }
        Text(label, fontSize = labelSp.sp, fontWeight = FontWeight.ExtraBold, color = txt, maxLines = 1)
    }
}
