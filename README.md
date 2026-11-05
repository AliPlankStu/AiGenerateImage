# AI生成图片

Android 应用：上传角色参考图，输入提示词，调用 OpenAI Images API 进行图像编辑，保存结果。

## 构建 APK
GitHub Actions → Android APK → Run workflow，或推送代码自动构建。

## 使用
1. 设置页填写 OpenAI API Key（不会提交到仓库）。
2. 首页选择参考图片并输入提示词。
3. 点击生成，完成后保存到相册。

> 图像编辑依赖可用的 OpenAI 图像模型/API权限及网络。API Key仅保存在本机偏好设置中。
