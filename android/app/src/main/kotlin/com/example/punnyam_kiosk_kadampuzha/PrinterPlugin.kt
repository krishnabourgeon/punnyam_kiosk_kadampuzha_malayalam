package com.example.punnyam_kiosk_kadampuzha
import com.kioskworldline.com.UsbDriver
import com.kioskworldline.com.PrintCmd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.usb.UsbManager
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class PrinterPlugin(
    private val context: Context
) : MethodChannel.MethodCallHandler {

    private var usbDriver: UsbDriver? = null

    // The printer only understands GB2312 (see PrintCmd.PrintString), which has
    // no Malayalam glyphs — those bytes come back as literal '?' on paper.
    // Malayalam lines are rendered to a bitmap with a bundled Unicode font
    // instead and sent as an image so the printer's font ROM is bypassed.
    private val malayalamTypeface: Typeface? by lazy {
        try {
            Typeface.createFromAsset(
                context.assets,
                "flutter_assets/assets/fonts/NotoSansMalayalam-Regular.ttf"
            )
        } catch (e: Exception) {
            null
        }
    }

    init {
        usbDriver = UsbDriver(
            context.getSystemService(
                Context.USB_SERVICE
            ) as UsbManager,
            context
        )
    }

    private fun containsMalayalam(text: String): Boolean {
        return text.any { it.code in 0x0D00..0x0D7F }
    }

    private fun printLine(text: String) {
        if (text.isEmpty()) return
        if (containsMalayalam(text) && malayalamTypeface != null) {
            printTextAsBitmap(text)
        } else {
            usbDriver?.write(PrintCmd.PrintString(text, 0))
        }
    }

    private fun printTextAsBitmap(text: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
            typeface = malayalamTypeface
        }

        val printWidth = 384 // dots, matches the 32-char text line width used elsewhere
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val lineHeight = bounds.height() + 16

        val bitmap = Bitmap.createBitmap(printWidth, lineHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawText(text, 4f, (lineHeight - 12).toFloat(), paint)

        val bytes = PrintCmd.PrintBitmap(bitmap)
        if (bytes != null) {
            usbDriver?.write(bytes)
        } else {
            // Fall back to the GB2312 path (renders as '?') rather than dropping the line silently.
            usbDriver?.write(PrintCmd.PrintString(text, 0))
        }
        bitmap.recycle()
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {

        when (call.method) {

            "connect" -> {

                val connected =
                    usbDriver?.openUsbDevice() ?: false

                result.success(connected)
            }

            "printText" -> {

                val text =
                    call.argument<String>("text") ?: ""

                usbDriver?.write(
                    PrintCmd.SetClean()
                )

                usbDriver?.write(
                    PrintCmd.PrintString(
                        text,
                        0
                    )
                )

                usbDriver?.write(
                    PrintCmd.PrintFeedline(2)
                )

                result.success(true)
            }

            "cutPaper" -> {

                usbDriver?.write(
                    PrintCmd.PrintCutpaper(0)
                )

                result.success(true)
            }

            "printReceipt" -> {
val temple =  call.argument<String>("temple") ?: ""
val templeAddress =  call.argument<String>("templeAddress") ?: ""
val templePlace =  call.argument<String>("templePlace") ?: ""
                val billNo =
                    call.argument<String>("billNo") ?: ""

                val date =
                    call.argument<String>("date") ?: ""

                val mode =
                    call.argument<String>("mode") ?: ""

                val total =
                    call.argument<String>("total") ?: ""

                val website =
                    call.argument<String>("website") ?: ""
                val items =call.argument<List<Map<String, Any?>>>("items")
        ?: emptyList()

                printReceipt(
                    temple,
                    templeAddress,
                    templePlace,
                    billNo,
                    date,
                    mode,
                    total,
                    website,
                    items
                )

                result.success(true)
            }

            else -> result.notImplemented()
        }
    }

    private fun printTwoColumn(
        left: String,
        right: String
    ) {

        val width = 32

        val spaces =
            (width - left.length - right.length)
                .coerceAtLeast(1)

        val line =
            left + " ".repeat(spaces) + right

        printLine(line)
    }

    private fun printReceipt(
        temple: String,
    templeAddress: String,
    templePlace: String,
    billNo: String,
    date: String,
    mode: String,
    total: String,
    website: String,
    items: List<Map<String, Any?>>
) {

  usbDriver?.write(PrintCmd.SetClean())

printLine(temple.uppercase())
printLine(templeAddress)
printLine(templePlace)

usbDriver?.write(PrintCmd.PrintFeedline(1))

printLine("--------------------------------")

    printTwoColumn(
        "Bill No: $billNo",
        date
    )

    printLine("--------------------------------")

    for (item in items) {

        val personId =
            item["personId"]?.toString() ?: ""

        val personName =
            item["personName"]?.toString() ?: ""

        val deity =
            item["deity"]?.toString() ?: ""

        val star =
            item["star"]?.toString() ?: ""

        val pooja =
            item["pooja"]?.toString() ?: ""

        val qty =
            item["qty"]?.toString() ?: ""

        val rate =
            item["rate"]?.toString() ?: ""

        val poojaDate =
            item["date"]?.toString() ?: ""

        val address =
            item["address"]?.toString() ?: ""

        printLine("$personId. $deity")

        printLine("$personName - $star")

        printLine("$pooja  $qty x $rate")

        if (poojaDate.isNotEmpty()) {
            printLine(poojaDate)
        }

        if (address.isNotEmpty()) {
            printLine(address)
        }

        usbDriver?.write(
            PrintCmd.PrintFeedline(1)
        )
    }

    printLine("--------------------------------")

    printTwoColumn(
        "Mode: $mode",
        "Total: Rs.$total"
    )

    usbDriver?.write(
        PrintCmd.PrintFeedline(1)
    )

    printLine("Book Online $website")

    usbDriver?.write(
        PrintCmd.PrintFeedline(3)
    )

    usbDriver?.write(
        PrintCmd.PrintCutpaper(0)
    )
}

}