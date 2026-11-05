package com.aliplankstu.aigenerateimage

import android.app.*
import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var source: Uri? = null
    private var resultBytes: ByteArray? = null
    private lateinit var preview: ImageView
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private lateinit var generate: Button
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { source=uri; preview.setImageURI(uri); status.text="参考图片已选择" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(24,20,38)
        window.navigationBarColor=android.graphics.Color.rgb(24,20,38)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,28,28,24); setBackgroundColor(0xFF171523.toInt()) }
        fun label(s:String)=TextView(this).apply { text=s; textSize=20f; setTextColor(0xFFE9DFFF.toInt()); setPadding(0,12,0,12) }
        root.addView(label("✦ AI生成图片"))
        root.addView(TextView(this).apply { text="上传角色参考图，描述你想要的姿势与风格"; setTextColor(0xFFB8AFCB.toInt()); textSize=14f })
        preview=ImageView(this).apply { adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER; setBackgroundColor(0xFF252136.toInt()) }
        root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        val choose=Button(this).apply { text="从相册选择图片"; setOnClickListener { pick.launch("image/*") } }
        root.addView(choose)
        root.addView(label("提示词"))
        prompt=EditText(this).apply { hint="例如：角色坐在樱花树下，日系动漫风格…"; setTextColor(-1); setHintTextColor(0xFF9A91AE.toInt()); setBackgroundColor(0xFF252136.toInt()); minLines=3; gravity=Gravity.TOP; setPadding(16,12,16,12) }
        root.addView(prompt,LinearLayout.LayoutParams(-1,120))
        status=TextView(this).apply { text="请先设置 API Key 并选择图片"; setTextColor(0xFFB8AFCB.toInt()); setPadding(0,12,0,8) }
        root.addView(status)
        generate=Button(this).apply { text="✧ 开始生成"; setOnClickListener { generateImage() } }
        root.addView(generate)
        root.addView(Button(this).apply { text="设置 API Key"; setOnClickListener { keyDialog() } })
        root.addView(Button(this).apply { text="保存生成结果"; setOnClickListener { saveResult() } })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun keyDialog() {
        val input=EditText(this).apply { hint="sk-…"; setText(prefs.getString("key","")); inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("OpenAI API Key").setMessage("密钥仅保存在本机，不会写入仓库。使用图像编辑 API 可能产生费用。").setView(input)
            .setPositiveButton("保存"){_,_->prefs.edit().putString("key",input.text.toString().trim()).apply(); status.text="API Key 已保存"}
            .setNegativeButton("取消",null).show()
    }

    private fun generateImage() {
        val key=prefs.getString("key","") ?: ""
        val uri=source
        val text=prompt.text.toString().trim()
        if(key.isBlank()){keyDialog();return}
        if(uri==null){status.text="请先选择参考图片";return}
        if(text.isBlank()){status.text="请输入提示词";return}
        generate.isEnabled=false; status.text="正在生成，请耐心等待…"
        thread {
            try {
                val boundary="----AndroidImage"+System.currentTimeMillis()
                val conn=(URL("https://api.openai.com/v1/images/edits").openConnection() as HttpURLConnection).apply {
                    requestMethod="POST"; doOutput=true; connectTimeout=30000; readTimeout=180000
                    setRequestProperty("Authorization","Bearer $key"); setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
                }
                DataOutputStream(conn.outputStream).use { out ->
                    fun field(name:String,value:String){out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")}
                    field("model","gpt-image-1"); field("prompt",text); field("size","1024x1024")
                    val mime=contentResolver.getType(uri) ?: "image/png"
                    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"reference.png\"\r\nContent-Type: $mime\r\n\r\n")
                    contentResolver.openInputStream(uri)!!.use { it.copyTo(out) }; out.writeBytes("\r\n--$boundary--\r\n")
                }
                val response=if(conn.responseCode in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
                if(conn.responseCode !in 200..299) throw Exception(JSONObject(response).optJSONObject("error")?.optString("message") ?: response)
                val b64=JSONObject(response).getJSONArray("data").getJSONObject(0).getString("b64_json")
                resultBytes=Base64.decode(b64,Base64.DEFAULT)
                runOnUiThread { preview.setImageBitmap(BitmapFactory.decodeByteArray(resultBytes,0,resultBytes!!.size)); status.text="生成完成！可保存到相册。" }
            } catch(e:Exception){runOnUiThread { status.text="生成失败：${e.message ?: "请检查网络和 API Key"}" }}
            finally {runOnUiThread {generate.isEnabled=true}}
        }
    }

    private fun saveResult() {
        val bytes=resultBytes ?: run { status.text="暂无生成结果";return }
        try {
            val values=android.content.ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"AI生成图片_${System.currentTimeMillis()}.png"); put(MediaStore.Images.Media.MIME_TYPE,"image/png"); put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AiGenerateImage") }
            val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: throw Exception("无法创建相册文件")
            contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }; status.text="已保存到 Pictures/AiGenerateImage"
        } catch(e:Exception){status.text="保存失败：${e.message}"}
    }
}
