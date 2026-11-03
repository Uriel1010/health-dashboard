package com.uriel.healthconnecttest.widget

import android.graphics.*
import com.uriel.healthconnecttest.sync.*
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.roundToInt

/** Canvas-backed RemoteViews keep gradients, rounded bars and rings identical on launchers. */
object CompanionWidgetRenderer {
    fun render(kind: Int, dark: Boolean, width: Int, height: Int, progress: NutritionWidgetProgress,
        statistics: NutritionStatistics?, period: Int, metric: NutritionMetric): Bitmap {
        val mini = kind >= 3
        val w = if (mini) 110f else 320f
        val h = if (mini) 100f else 220f
        // Bound binder payload independently of launcher density and oversized resize requests.
        val bitmap = Bitmap.createBitmap((width * 2).coerceAtMost(1000), (height * 2).coerceAtMost(700), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val scale = min(bitmap.width / w, bitmap.height / h)
        c.translate((bitmap.width - w * scale) / 2, (bitmap.height - h * scale) / 2)
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val ink = Color.parseColor(if (dark) "#F7F3FF" else "#190E32")
        val muted = Color.parseColor(if (dark) "#D5CDE4" else "#49385E")
        val track = Color.parseColor(if (dark) "#50465F" else "#E1D9EF")
        val purple = Color.parseColor("#8B28EF")
        fun text(s: String, x: Float, y: Float, size: Float = 13f, bold: Boolean = false, color: Int = ink) {
            p.shader = null; p.style = Paint.Style.FILL; p.color = color; p.textSize = size
            p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            c.drawText(s, x, y, p)
        }
        fun line(x: Float, y: Float, end: Float) { p.shader=null; p.color=track; p.strokeWidth=0.7f; c.drawLine(x,y,end,y,p) }
        fun bar(x: Float, y: Float, length: Float, percent: Int, thickness: Float = 9f) {
            p.shader=null; p.color=track
            c.drawRoundRect(x,y,x+length,y+thickness,5f,5f,p)
            if (percent > 0) {
                p.shader=LinearGradient(x,y,x,y+thickness,Color.parseColor("#CA8AFF"),purple,Shader.TileMode.CLAMP)
                c.drawRoundRect(x,y,x+length*percent.coerceIn(0,100)/100,y+thickness,5f,5f,p); p.shader=null
            }
        }
        fun glyph(x: Float, y: Float, color: Int, type: Int = 0, size: Float = 20f) {
            c.save(); c.translate(x,y); c.scale(size/24,size/24)
            p.shader=null; p.color=color; p.style=Paint.Style.STROKE; p.strokeWidth=2f; p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
            val path = Path()
            when(type) {
                1 -> { path.moveTo(4f,3f);path.lineTo(4f,10f);path.quadTo(7f,15f,10f,10f);path.lineTo(10f,3f);path.moveTo(7f,3f);path.lineTo(7f,22f);path.moveTo(18f,3f);path.lineTo(18f,22f);path.moveTo(18f,3f);path.quadTo(12f,10f,18f,13f) }
                2 -> { path.moveTo(3f,20f);path.quadTo(3f,5f,21f,3f);path.quadTo(23f,20f,3f,20f);path.moveTo(3f,22f);path.lineTo(16f,9f) }
                3 -> { path.moveTo(3f,19f);path.quadTo(0f,8f,7f,3f);path.lineTo(11f,3f);path.lineTo(11f,7f);path.lineTo(7f,8f);path.lineTo(8f,13f);path.cubicTo(14f,6f,24f,11f,21f,18f);path.quadTo(13f,24f,3f,19f) }
                else -> { path.moveTo(12f,2f);path.cubicTo(23f,13f,23f,21f,12f,22f);path.cubicTo(0f,21f,3f,13f,7f,10f);path.lineTo(9f,15f);path.quadTo(14f,11f,12f,2f) }
            }
            c.drawPath(path,p); p.style=Paint.Style.FILL;c.restore()
        }
        fun heart(cx: Float, cy: Float, size: Float) {
            c.save();c.translate(cx-size/2,cy-size/2);c.scale(size/24,size/24)
            p.shader=null;p.color=if(dark) Color.parseColor("#C6E7CE") else Color.parseColor("#9A7BC0");p.strokeWidth=1.8f;p.style=Paint.Style.STROKE
            val a=Path();a.moveTo(12f,21f);a.cubicTo(6f,17f,1f,12f,2f,7f);a.cubicTo(3f,1f,10f,2f,12f,6f);a.cubicTo(15f,1f,22f,2f,22f,8f);a.cubicTo(22f,13f,17f,18f,12f,21f)
            c.drawPath(a,p);val b=Path();b.moveTo(0f,12f);b.lineTo(7f,12f);b.lineTo(9f,8f);b.lineTo(12f,16f);b.lineTo(15f,10f);b.lineTo(17f,12f);b.lineTo(24f,12f);c.drawPath(b,p);p.style=Paint.Style.FILL;c.restore()
        }
        val colors = listOf("#8B28EF","#ED63CD","#31AFF5","#FF9837","#59BE71").map(Color::parseColor)
        fun percent(value:Double,goal:Double) = if(goal>0) (value/goal*100).roundToInt().coerceIn(0,100) else 0
        val percentages = listOf(percent(progress.caloriesKcal,progress.caloriesGoal),percent(progress.proteinG,progress.proteinGoal),percent(progress.carbsG,progress.carbsGoal),percent(progress.fatG,progress.fatGoal),percent(progress.fiberG,progress.fiberGoal))
        fun rings(cx: Float, cy: Float, radius: Float) {
            percentages.forEachIndexed { index, value ->
                val r=radius-index*(radius/7.5f);p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=radius/10;p.strokeCap=Paint.Cap.ROUND;p.color=track
                c.drawCircle(cx,cy,r,p);p.color=colors[index]
                if(value>0)c.drawArc(cx-r,cy-r,cx+r,cy+r,-90f,value*3.6f,false,p)
            };p.style=Paint.Style.FILL;heart(cx,cy,radius*0.48f)
        }
        p.shader=LinearGradient(0f,0f,w,h,Color.parseColor(if(dark) "#393147" else "#FFFCFF"),Color.parseColor(if(dark) "#211D30" else "#F5F0FF"),Shader.TileMode.CLAMP)
        c.drawRoundRect(1f,1f,w-1,h-1,20f,20f,p);p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=Color.parseColor(if(dark) "#625470" else "#E3D8F5")
        c.drawRoundRect(1f,1f,w-1,h-1,20f,20f,p);p.style=Paint.Style.FILL
        val fmt=NumberFormat.getIntegerInstance()
        fun amount(v:Double)=fmt.format(v)
        when(kind%3) {
            0 -> if(mini) {
                glyph(12f,12f,purple,size=16f);text("Today",34f,25f,13f,true)
                bar(13f,40f,84f,progress.caloriesProgress)
                text("${amount(progress.caloriesKcal)} / ${amount(progress.caloriesGoal)}",13f,66f,12f,true)
                text("P ${amount(progress.proteinG)} g",13f,85f,10f);text("F ${amount(progress.fiberG)} g",65f,85f,10f)
            } else {
                glyph(17f,17f,purple,1,25f);text("Today's progress",57f,29f,16f,true);text("Nutrition intake · Today",57f,48f,12f,false,muted);text("›",291f,34f,25f)
                val labels=listOf("Calories","Protein","Fiber")
                val values=listOf(progress.caloriesKcal,progress.proteinG,progress.fiberG)
                val goals=listOf(progress.caloriesGoal,progress.proteinGoal,progress.fiberGoal)
                val percents=listOf(percentages[0],percentages[1],percentages[4])
                labels.forEachIndexed { i,label -> val y=82f+i*48
                    glyph(19f,y-13,Color.parseColor(listOf("#FF6868","#B67BE8","#53B975")[i]),listOf(0,3,2)[i],21f)
                    text(label,53f,y,13f,true);text("${amount(values[i])} / ${amount(goals[i])}${if(i>0) " g" else ""}",174f,y,12f)
                    text("${percents[i]}%",277f,y,12f,true);bar(53f,y+10,247f,percents[i])
                }
            }
            1 -> if(mini) rings(55f,50f,43f) else {
                glyph(18f,17f,purple,1,25f);text("Daily rings",58f,29f,16f,true);text("Nutrition · Today",58f,48f,12f,false,muted);text("›",291f,34f,25f)
                rings(88f,136f,65f)
                listOf("Calories","Protein","Carbs","Fat","Fiber").forEachIndexed { i,label ->
                    val y=87f+i*25;p.color=colors[i];c.drawCircle(179f,y-4,5f,p);text(label,192f,y,13f);text("${percentages[i]}%",277f,y,13f,true)
                }
            }
            2 -> {
                val window=statistics?.windows?.firstOrNull { it.days==period }
                val value=window?.values?.get(metric)
                val display=value?.let { amount(it) } ?: "—"
                val points=statistics?.points.orEmpty().filter { window!=null && !it.date.isBefore(window.from) && !it.date.isAfter(window.to) }.takeLast(10)
                fun spark(x:Float,y:Float,bw:Float,bh:Float) {
                    val max=points.mapNotNull { it.values[metric] }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                    points.forEachIndexed { i,point ->
                        val v=point.values[metric];val left=x+i*bw/10
                        p.shader=LinearGradient(left,y-bh,left,y,Color.parseColor("#DDB5FF"),purple,Shader.TileMode.CLAMP)
                        if(v!=null)c.drawRoundRect(left,y-(bh*v/max).toFloat().coerceAtLeast(2f),left+bw/10-3,y,3f,3f,p)
                        else {p.shader=null;p.color=track;c.drawCircle(left+2,y-2,2f,p)}
                    };p.shader=null
                }
                if(mini) {
                    glyph(13f,11f,purple,size=17f);text("Avg (${period}d)",36f,24f,12f,true)
                    text("$display ${metric.unit}",13f,47f,17f,true);spark(14f,87f,85f,28f)
                    if(window==null)text("Sync in Trends",13f,80f,10f,false,muted)
                } else {
                    glyph(18f,17f,if(metric==NutritionMetric.Fiber)colors[4] else purple,if(metric==NutritionMetric.Fiber)2 else if(metric==NutritionMetric.Protein)3 else 0,26f)
                    text("Nutrition average",60f,29f,16f,true);text("${metric.label} · $period days",60f,48f,12f,false,muted);text("›",291f,34f,25f)
                    text("$display ${metric.unit}",18f,92f,26f,true);text("avg per day",18f,112f,13f,false,muted);spark(185f,118f,116f,43f)
                    line(18f,132f,302f)
                    val count=window?.coverage?.get(metric) ?: 0
                    text("▦",19f,155f,16f,false,muted);text("Data coverage: $count / $period days (${count*100/period}%)",42f,155f,12f)
                    val dateFormat=DateTimeFormatter.ofPattern("MMM d, yyyy")
                    text("▦",19f,178f,16f,false,muted);text(window?.let { "Saved: ${it.from.format(dateFormat)} – ${it.to.format(dateFormat)}" } ?: "Open Trends to synchronize",42f,178f,11f)
                    text("ⓘ",19f,202f,16f,false,muted);text("Today is excluded from this average.",42f,202f,12f)
                }
            }
        }
        return bitmap
    }
}
