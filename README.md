# AI生成图片（本地免费版）

Android 应用：使用 Google MediaPipe Image Generator 在手机本地进行文字生成图片。

## 当前版本

- 不需要 OpenAI API Key。
- 不调用云端图片生成 API；生成过程在设备本机执行。
- 支持选择模型文件夹、导入到应用私有目录、初始化模型、输入提示词、生成并保存图片。
- 当前实现是**文字生成图片**。原先在线版的参考图编辑功能暂未迁移。

## 重要：模型文件尚未随 APK 提供

APK 不包含模型权重。首次使用前，必须准备与 MediaPipe Image Generator 兼容的模型目录，再在应用中选择该目录导入。仅仅下载普通 Stable Diffusion 模型文件并不保证兼容；模型必须按 MediaPipe Image Generator 要求转换并包含所需文件。

Google 的 Android 图片生成 Codelab 将该任务标为实验性功能，并建议至少 8GB RAM 的设备。124GB 存储空间不代表有 8GB RAM。低内存或不兼容 GPU 的设备可能无法初始化或生成。

官方入门说明：
https://codelabs.developers.google.com/mp-image-generation-basic-android

## 构建 APK

GitHub Actions → Android APK → Run workflow，或向构建分支推送代码自动构建。
