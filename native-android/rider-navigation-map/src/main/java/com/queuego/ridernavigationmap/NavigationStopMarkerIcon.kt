package com.queuego.ridernavigationmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import kotlin.math.roundToInt

internal fun navigationStopMarkerIcon(
    context: Context,
    stop: NavigationMapStop,
    active: Boolean
): Icon {
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    fun px(dp: Float): Float = dp * density

    val bitmap = Bitmap.createBitmap(
        px(54f).roundToInt(),
        px(62f).roundToInt(),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val kindColor = if (stop.kind == NavigationStopKind.PICKUP) {
        android.graphics.Color.rgb(230, 0, 45)
    } else {
        android.graphics.Color.rgb(23, 23, 27)
    }
    val outlineColor = if (active) {
        android.graphics.Color.rgb(230, 0, 45)
    } else {
        android.graphics.Color.rgb(207, 199, 202)
    }

    paint.style = Paint.Style.FILL
    paint.color = kindColor
    val pointer = Path().apply {
        moveTo(px(22f), px(44f))
        lineTo(px(32f), px(44f))
        lineTo(px(27f), px(56f))
        close()
    }
    canvas.drawPath(pointer, paint)

    val body = RectF(px(7f), px(4f), px(47f), px(45f))
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(body, px(14f), px(14f), paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = px(if (active) 3f else 2f)
    paint.color = outlineColor
    canvas.drawRoundRect(body, px(14f), px(14f), paint)

    paint.style = Paint.Style.FILL
    paint.color = kindColor
    canvas.drawCircle(px(11f), px(10f), px(9f), paint)
    paint.color = android.graphics.Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = px(if (stop.sequence >= 10) 8f else 10f)
    canvas.drawText(stop.sequence.toString(), px(11f), px(13.5f), paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = px(1.9f)
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = kindColor

    val cx = px(28f)
    val cy = px(25f)
    if (stop.kind == NavigationStopKind.PICKUP) {
        canvas.drawRect(RectF(cx - px(8f), cy - px(2f), cx + px(8f), cy + px(9f)), paint)
        canvas.drawLine(cx - px(9f), cy - px(3f), cx + px(9f), cy - px(3f), paint)
        canvas.drawLine(cx - px(7f), cy - px(7f), cx + px(7f), cy - px(7f), paint)
        canvas.drawLine(cx - px(7f), cy - px(7f), cx - px(9f), cy - px(3f), paint)
        canvas.drawLine(cx + px(7f), cy - px(7f), cx + px(9f), cy - px(3f), paint)
        canvas.drawRect(RectF(cx - px(3f), cy + px(3f), cx + px(3f), cy + px(9f)), paint)
    } else {
        canvas.drawCircle(cx, cy - px(4f), px(4f), paint)
        canvas.drawArc(
            RectF(cx - px(8f), cy + px(2f), cx + px(8f), cy + px(17f)),
            198f,
            144f,
            false,
            paint
        )
    }

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}
