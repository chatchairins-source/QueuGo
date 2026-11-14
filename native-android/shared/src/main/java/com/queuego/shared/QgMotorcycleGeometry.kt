package com.queuego.shared

/** One normalized motorcycle outline shared by Compose and map marker bitmaps. */
internal fun qgMotorcycleGeometry(
    drawLine: (Float, Float, Float, Float) -> Unit,
    drawCircle: (Float, Float, Float) -> Unit
) {
    drawCircle(.25f, .67f, .13f)
    drawCircle(.75f, .67f, .13f)
    drawLine(.38f, .67f, .55f, .67f)
    drawLine(.55f, .67f, .63f, .46f)
    drawLine(.63f, .46f, .46f, .46f)
    drawLine(.46f, .46f, .38f, .33f)
    drawLine(.38f, .33f, .25f, .33f)
    drawLine(.55f, .67f, .42f, .46f)
    drawLine(.63f, .46f, .75f, .67f)
    drawLine(.58f, .29f, .71f, .29f)
}
