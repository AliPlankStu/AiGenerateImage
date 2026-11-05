package com.aliplankstu.aigenerateimage

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ImageGeneratorOptions
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var preview: ImageView
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private lateinit var chooseModel: Button
    private lateinit var initialize: Button
    private lateinit var generate: Button
    private var generator: ImageGenerator? = null
    private var output: Bitmap? = null
    private var modelReady = false
    private val modelDir get() = File(filesDir, "image_generator/bins")

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            chooseModel.isEnabled = false
            status.text = "正在导入模型文件，请保持应用打开…"
            thread {
                try {
                    val picked = DocumentFile.fromTreeUri(this, uri) ?: error("无法读取所选文件夹")
                    val source = picked.findFile("bins")?.takeIf { it.isDirectory } ?: picked
                    modelDir.deleteRecursively()
                    if (!modelDir.mkdirs() && !modelDir.isDirectory) error("无法创建模型目录")
                    copyDirectory(source, modelDir)
                    runOnUiThread {
                        status.text = "模型文件已导入。请点击「初始化模型」检查模型是否兼容。"
                        initialize.isEnabled = true
                    }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "导入失败：${e.message ?: "无法读取模型文件"}" }
                } finally {
                    runOnUiThread { chooseModel.isEnabled = true }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(24, 20, 38)
        window.navigationBarColor = Color.rgb(24, 20, 38)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            setBackgroundColor(0xFF171523.toInt())
        }
        fun label(value: String) = TextView(this).apply {
            text = value; textSize = 18f; setTextColor(0xFFE9DFFF.toInt()); setPadding(0, 12, 0, 10)
        }
        root.addView(label("✦ AI生成图片 · 本地免费版"))
        root.addView(TextView(this).apply {
            text = "模型在手机本地运行，不需要 API Key 或按张付费。首次使用需要导入兼容的 MediaPipe 图片生成模型。"
            textSize = 14f; setTextColor(0xFFB8AFCB.toInt())
        })
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF252136.toInt())
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        chooseModel = Button(this).apply {
            text = "选择本地模型文件夹"
            setOnClickListener { folderPicker.launch(null) }
        }
        root.addView(chooseModel)
        initialize = Button(this).apply {
            text = "初始化模型"
            isEnabled = modelDir.isDirectory && modelDir.listFiles()?.isNotEmpty() == true
            setOnClickListener { initializeModel() }
        }
        root.addView(initialize)
        root.addView(label("提示词"))
        prompt = EditText(this).apply {
            hint = "例如：一只坐在樱花树下的白色小猫，日系插画风格"
            setTextColor(Color.WHITE); setHintTextColor(0xFF9A91AE.toInt())
            setBackgroundColor(0xFF252136.toInt()); minLines = 3
            gravity = Gravity.TOP; setPadding(16, 12, 16, 12)
        }
        root.addView(prompt, LinearLayout.LayoutParams(-1, 120))
        status = TextView(this).apply {
            text = if (initialize.isEnabled) "检测到已导入的模型文件" else "尚未导入模型"
            setTextColor(0xFFB8AFCB.toInt()); setPadding(0, 12, 0, 8)
        }
        root.addView(status)
        generate = Button(this).apply {
            text = "✧ 开始本地生成"
            isEnabled = false
            setOnClickListener { generateImage() }
        }
        root.addView(generate)
        root.addView(Button(this).apply {
            text = "保存生成结果"
            setOnClickListener { saveResult() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun copyDirectory(source: DocumentFile, destination: File) {
        if (!destination.exists() && !destination.mkdirs()) error("无法创建目录")
        source.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val target = File(destination, name)
            if (child.isDirectory) {
                copyDirectory(child, target)
            } else {
                contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取文件：$name")
            }
        }
    }

    private fun initializeModel() {
        initialize.isEnabled = false
        generate.isEnabled = false
        status.text = "正在初始化本地模型…"
        thread {
            try {
                val options = ImageGeneratorOptions.builder()
                    .setImageGeneratorModelDirectory(modelDir.absolutePath)
                    .build()
                val created = ImageGenerator.createFromOptions(this, options)
                runOnUiThread {
                    generator?.close()
                    generator = created
                    modelReady = true
                    status.text = "模型初始化成功。可以输入提示词生成图片。"
                    generate.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    modelReady = false
                    status.text = "模型初始化失败：${e.message ?: "模型文件不兼容或不完整"}"
                    initialize.isEnabled = true
                }
            }
        }
    }

    private fun generateImage() {
        val text = prompt.text.toString().trim()
        if (!modelReady || generator == null) {
            status.text = "请先导入并初始化模型"; return
        }
        if (text.isBlank()) { status.text = "请输入提示词"; return }
        generate.isEnabled = false
        status.text = "正在手机本地生成，请耐心等待…"
        thread {
            try {
                val result = generator!!.generate(text, 20, 0)
                val bitmap = BitmapExtractor.extract(result?.generatedImage())
                    ?: error("模型没有返回图片")
                runOnUiThread {
                    output = bitmap
                    preview.setImageBitmap(bitmap)
                    status.text = "生成完成！图片没有上传到云端。"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "生成失败：${e.message ?: "请检查模型文件和手机性能"}" }
            } finally {
                runOnUiThread { generate.isEnabled = modelReady }
            }
        }
    }

    private fun saveResult() {
        val bitmap = output ?: run { status.text = "暂无生成结果"; return }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "AI本地生成_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AiGenerateImage")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建相册文件")
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: error("无法写入图片")
            status.text = "已保存到 Pictures/AiGenerateImage"
        } catch (e: Exception) {
            status.text = "保存失败：${e.message}"
        }
    }

    override fun onDestroy() {
        generator?.close()
        generator = null
        super.onDestroy()
    }
}
