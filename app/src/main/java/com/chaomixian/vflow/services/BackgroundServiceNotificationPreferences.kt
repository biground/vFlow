package com.chaomixian.vflow.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.parser.TemplateParser
import com.chaomixian.vflow.core.types.parser.TemplateSegment
import com.chaomixian.vflow.core.types.parser.VariablePathParser
import com.chaomixian.vflow.core.workflow.GlobalVariableStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class BackgroundServiceNotificationSettings(
    val title: String,
    val text: String,
    val iconPath: String?
)

object BackgroundServiceNotificationPreferences {
    const val KEY_TITLE = "backgroundServiceNotificationTitle"
    const val KEY_TEXT = "backgroundServiceNotificationText"
    const val KEY_ICON_PATH = "backgroundServiceNotificationIconPath"

    private const val PREFS_NAME = "vFlowPrefs"
    private const val ICON_DIR_NAME = "notification_icons"
    private const val ICON_FILE_NAME = "background_service_large_icon.png"
    private const val LARGE_ICON_DP = 64
    private const val STORED_ICON_MAX_PX = 256

    fun read(context: Context): BackgroundServiceNotificationSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return normalize(
            title = prefs.getString(KEY_TITLE, null),
            text = prefs.getString(KEY_TEXT, null),
            iconPath = prefs.getString(KEY_ICON_PATH, null),
            defaultTitle = context.getString(R.string.trigger_service_notification_title),
            defaultText = context.getString(R.string.trigger_service_notification_text)
        )
    }

    fun readResolved(context: Context): BackgroundServiceNotificationSettings {
        val settings = read(context)
        val globalVariables = GlobalVariableStore.getAll(context)
        return settings.copy(
            title = renderTemplate(settings.title, globalVariables),
            text = renderTemplate(settings.text, globalVariables)
        )
    }

    fun normalize(
        title: String?,
        text: String?,
        iconPath: String?,
        defaultTitle: String,
        defaultText: String
    ): BackgroundServiceNotificationSettings {
        return BackgroundServiceNotificationSettings(
            title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: defaultTitle,
            text = text?.trim().takeUnless { it.isNullOrEmpty() } ?: defaultText,
            iconPath = iconPath?.trim().takeUnless { it.isNullOrEmpty() }
        )
    }

    fun saveText(context: Context, title: String, text: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_TITLE, title)
            putString(KEY_TEXT, text)
        }
    }

    fun saveIconFromUri(context: Context, uri: Uri): String {
        val bitmap = decodeBitmap(context, uri)
        val scaledBitmap = scaleBitmapToMax(bitmap, STORED_ICON_MAX_PX)
        if (scaledBitmap !== bitmap) {
            bitmap.recycle()
        }

        val iconFile = iconFile(context)
        iconFile.parentFile?.mkdirs()
        FileOutputStream(iconFile).use { output ->
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        if (!scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_ICON_PATH, iconFile.absolutePath)
        }
        return iconFile.absolutePath
    }

    fun clearIcon(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_ICON_PATH)
        }
        iconFile(context).delete()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_TITLE)
            remove(KEY_TEXT)
            remove(KEY_ICON_PATH)
        }
        iconFile(context).delete()
    }

    fun loadLargeIcon(context: Context): Bitmap? {
        val path = read(context).iconPath ?: return null
        val source = BitmapFactory.decodeFile(path) ?: return null
        val maxPx = (context.resources.displayMetrics.density * LARGE_ICON_DP).roundToInt()
        return scaleBitmapToMax(source, maxPx).also { scaled ->
            if (scaled !== source) {
                source.recycle()
            }
        }
    }

    fun renderTemplate(
        template: String,
        globalVariables: Map<String, VObject>
    ): String {
        return TemplateParser(template).parse().joinToString(separator = "") { segment ->
            when (segment) {
                is TemplateSegment.Text -> segment.content
                is TemplateSegment.Variable -> resolveGlobalTemplateVariable(
                    segment,
                    globalVariables
                )
            }
        }
    }

    private fun iconFile(context: Context): File {
        return File(File(context.filesDir, ICON_DIR_NAME), ICON_FILE_NAME)
    }

    private fun resolveGlobalTemplateVariable(
        segment: TemplateSegment.Variable,
        globalVariables: Map<String, VObject>
    ): String {
        val path = globalVariablePath(segment) ?: return segment.rawExpression
        val value = path.firstOrNull()?.let(globalVariables::get) ?: return ""
        if (value is VNull) return ""

        val resolved = traverseProperties(value, path.drop(1))
        return if (resolved is VNull) "" else resolved.asString()
    }

    private fun globalVariablePath(segment: TemplateSegment.Variable): List<String>? {
        if (segment.isNamedVariable) {
            return if (segment.path.firstOrNull() == VariablePathParser.NAMED_VARIABLE_NAMESPACE) {
                segment.path.drop(1)
            } else {
                segment.path
            }.takeIf { it.isNotEmpty() }
        }

        return if (
            segment.path.size >= 2 &&
            segment.path.firstOrNull() == VariablePathParser.GLOBAL_VARIABLE_NAMESPACE
        ) {
            segment.path.drop(1)
        } else {
            null
        }
    }

    private fun traverseProperties(root: VObject, pathSegments: List<String>): VObject {
        var current = root
        pathSegments.forEach { segment ->
            current = current.getProperty(segment) ?: VNull
            if (current is VNull) {
                return VNull
            }
        }
        return current
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, imageInfo, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val longestSide = max(imageInfo.size.width, imageInfo.size.height)
                if (longestSide > STORED_ICON_MAX_PX) {
                    val scale = STORED_ICON_MAX_PX.toFloat() / longestSide.toFloat()
                    decoder.setTargetSize(
                        (imageInfo.size.width * scale).roundToInt().coerceAtLeast(1),
                        (imageInfo.size.height * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } ?: throw IllegalArgumentException("Unable to decode selected image")
    }

    private fun scaleBitmapToMax(bitmap: Bitmap, maxPx: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)
        if (longestSide <= maxPx) {
            return bitmap
        }
        val scale = maxPx.toFloat() / longestSide.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
