package com.uriel.healthconnecttest

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.graphics.*
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.uriel.healthconnecttest.widget.*
import com.uriel.healthconnecttest.sync.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class CompanionWidgetsTest {
    @Test fun hostAllSixWidgetsAndUpdateRemoteViews() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val host=AppWidgetHost(context,5050)
        val manager=AppWidgetManager.getInstance(context)
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            CompanionWidgetProvider.providers.forEach { provider ->
                val id=host.allocateAppWidgetId()
                assertTrue("Bind ${provider.simpleName}",manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,provider)))
                runBlocking { CompanionWidgetProvider.updateAll(context) }
                instrumentation.runOnMainSync {
                    val view=host.createView(context,id,manager.getAppWidgetInfo(id))
                    assertNotNull("RemoteViews image for ${provider.simpleName}",view.findViewById<android.widget.ImageView>(R.id.companion_image)?.drawable)
                }
                host.deleteAppWidgetId(id)
            }
        } finally { host.deleteHost();instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    @Test fun renderReferenceFamilyAndVerifyProviders() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Use the isolated emulator" }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=AppWidgetManager.getInstance(context)
        CompanionWidgetProvider.providers.forEach { provider ->
            assertTrue("Registered: ${provider.simpleName}", manager.installedProviders.any { it.provider==ComponentName(context,provider) })
        }
        val today=LocalDate.of(2025,1,24)
        val values=NutritionMetric.entries.zip(listOf(1742.0,118.0,212.0,58.0,26.0)).toMap()
        val stats=NutritionStatistics(listOf(7,14,30).map { days -> NutritionAverage(days,today.minusDays(days.toLong()),today.minusDays(1),days,values,NutritionMetric.entries.associateWith { days }) },
            (1..30).map { day -> NutritionHistoryDay(today.minusDays(day.toLong()),values.mapValues { it.value*(0.8+day%4*0.07) }) },today,"UTC",0)
        val progress=NutritionWidgetProgress(1650.0,110.0,24.0,2000.0,140.0,30.0,227.5,50.4,250.0,70.0)
        val directory=File(context.getExternalFilesDir(null),"widget-qa").apply { mkdirs() }
        listOf(false,true).forEach { dark ->
            val board=Bitmap.createBitmap(1920,700,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(board);canvas.drawColor(Color.parseColor(if(dark) "#494053" else "#EEE7FA"))
            (0..5).forEach { kind ->
                val image=CompanionWidgetRenderer.render(kind,dark,if(kind<3)320 else 110,if(kind<3)220 else 100,progress,stats,7,NutritionMetric.Calories)
                assertTrue(image.width>0)
                canvas.drawBitmap(image, (kind%3)*640f,if(kind<3)0f else 460f,null)
                File(directory,"${if(dark) "dark" else "light"}-$kind.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            File(directory,"${if(dark) "dark" else "light"}-board.png").outputStream().use { board.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        // Unknown history must remain renderable and must never be synthesized as zero.
        (0..5).forEach { kind -> CompanionWidgetRenderer.render(kind,true,48,48,progress,null,30,NutritionMetric.Fiber) }
        val zero=progress.copy(caloriesKcal=0.0,proteinG=0.0,fiberG=0.0,caloriesGoal=0.0)
        CompanionWidgetRenderer.render(0,false,600,500,zero,stats,14,NutritionMetric.Protein)
    }
}
