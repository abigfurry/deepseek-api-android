# DeepSeek API 版本

> 一个**非官方**的第三方 DeepSeek 客户端，Android 原生（Kotlin + Jetpack Compose）从零手写实现。
> 界面按官方 App 2.9.0 的截图与反编译参数逐像素复刻，但**不包含任何官方专有代码**，只调用公开的 HTTP API。

---

## 这是什么

官方 DeepSeek 客户端走的是「手机号登录 + 官方额度」，本项目的定位是**自带 API Key** 的客户端：

- 不登录账号、不需要开发者服务器；
- 你填自己的 API Key，请求**直接**发给 DeepSeek 官方接口；
- Key 与聊天记录**只保存在你的手机上**；
- 额外支持 19 家 OpenAI 兼容的服务商，可以随时切换。

## 功能

**对话**
- 流式输出、深度思考（reasoning）展示、思考链时间轴（线与点、分段折叠）
- Markdown 渲染：标题 / 列表 / 引用 / 加粗斜体 / 行内代码 / 代码块（语法高亮、横向滚动、复制）/ 表格
- 多分支回答（‹ n/N › 切换）、重新生成、编辑重发
- 长按菜单：复制 / 分享 / 选择文本 / 重新生成 / 删除
- 选中文字浮动工具条（按官方参数实现：圆角 16dp、按钮 58~100dp、字号 10.5sp、贴边翻转）
- 生成分享图片（官方样式：logo + 正文 + 免责页脚，FileProvider 分享）
- 联网搜索：搜索步骤进思考链、来源药丸可点开「N 个网页」列表并跳转
- TTS 朗读（官方 `/v1/tools/tts` 音色，失败自动降级系统 TTS）

**语音**
- 按住说话，AudioRecord 直接上传 WAV 到 ASR 接口
- 录音浮层：Android 13+ 直接跑官方 AGSL shader（`voice_mask.agsl`），低版本用等价 Canvas 实现，两边都有 34 根跳动的声波条
- 上滑取消、识别中状态

**界面**
- 平板两栏 / 手机单栏自适应；抽屉「纸片式」拉入拉出
- 浅色 / 深色 / 跟随系统三态，系统栏、导航栏、启动画面全部随主题（无闪白）
- 字体大小 5 档 + 跟随系统
- 设置页、二级页面、会话切换均有过渡动画（逐帧驱动，不受系统「动画缩放=0」影响）

**多服务商（19 家）**
DeepSeek / OpenAI / Anthropic Claude / Google Gemini / OpenRouter / 硅基流动 / 月之暗面 Kimi / 智谱 GLM / 阿里云百炼 / 字节火山方舟 / 百度千帆 / 腾讯混元 / Groq / Mistral / xAI Grok / Ollama / LM Studio / Together AI / 自定义

- 每家独立 baseUrl、chatPath、鉴权方式（Bearer / `x-api-key` + `anthropic-version` / `?key=`）
- 「自动获取模型列表」（失败不清空已有列表，手填模型永远置顶）
- 「查询余额」：DeepSeek `/user/balance`、OpenRouter、硅基流动、Moonshot 真实查询，其余显式提示无该接口
- 设置页与首屏都能切换服务商 / 模型，并提供「申请 API Key」直达各家控制台

**其它**
- 附件：图片（自动压缩到长边 1300px）、文件、相机（变焦 + 点按对焦）
- 会话管理：置顶 / 重命名 / 删除 / 多选批量删除、对话内全文搜索、导入导出聊天记录
- 本地资料：头像（相册选图）+ 昵称
- 分享图片、复制、全选等交互细节

## 快速开始

1. 安装 `DeepSeekAPI版本-1.0.0.apk`（包名 `com.yuyuyu.deepseek`，minSdk 26 / Android 8.0+）。
2. 首次打开会进入「填入 API Key 开始使用」页：
   - 选服务商（默认 DeepSeek）
   - 点「没有 Key？去 DeepSeek 申请 ›」到官方控制台免费申请
   - 粘贴 Key（可点「显示」核对）
   - 需要的话点「模型」选具体模型，或「自动获取模型列表」
   - 点「开始使用」
3. 之后随时可以在 **设置 → API 与模型** 里改服务商、Key、模型、余额查询。

## 自己编译

环境：JDK 21、Android SDK（compileSdk 37 / buildTools 36.0.0）、Gradle 9.7。

```bash
cd dsnative
./gradlew :app:assembleRelease
```

签名配置在 `app/build.gradle` 的 `signingConfigs.release`，请换成**你自己的 keystore**（不要用仓库里那把）。

## 项目结构

```
app/src/main/java/
├── app/dsclone/                 # 源码目录名保留，包声明为 com.yuyuyu.deepseek.*
│   ├── data/                    # Settings、Store(手写 JSON 持久化)、Providers(19 家预设/模型/余额)
│   ├── net/                     # 流式 SSE 客户端、工具调用/搜索步骤解析
│   └── ui/                      # 全部界面：聊天、抽屉、输入卡、录音浮层、设置、分享图、相机…
└── com/yuyuyu/deepseek/ui/      # Legal.kt（版权与免责声明全文）
```

界面尺寸常量集中在 `ui/theme/Theme.kt` 的 `DsMetric`，每个数值都在注释里标注了来源（官方截图实测 px→dp，或反编译 `kt:<文件>:<行号>`）。

## 隐私

- 不设开发者服务器，不收集、不上传、不出售任何数据；
- 设置与聊天记录只在本机应用私有目录（`settings.json` / `sessions.json`）；
- API Key 只存本机，请求时直接发给你指定的 API 服务商；
- 除你配置的 API 地址与搜索来源图标外，应用不访问其它网络地址。

## 免责与版权

- 本项目是**第三方独立开发的非官方客户端**，与 DeepSeek（杭州深度求索人工智能基础技术研究有限公司）及其关联公司**无任何隶属、合作、授权或背书关系**。
- "DeepSeek" 名称、Logo 及相关标识的权利归权利人所有；本项目仅在客观描述所连接的 API 服务时使用，无意主张任何权利。若权利人认为使用不当，可联系 [3982548020@qq.com](mailto:3982548020@qq.com)，我会及时移除或修改。
- 本项目**不包含**官方客户端的任何专有代码：界面通过公开截图测量与公开行为观察复刻，网络交互仅使用公开 HTTP API。
- 模型输出由第三方模型生成，可能不准确，仅供参考；使用本项目产生的 API 费用由使用者自行承担。
- 本项目按「现状」提供，不对可用性、准确性作任何担保，作者不承担因使用本项目造成的任何损失。

## 开源许可

代码以 **MIT License** 发布，见 [LICENSE](LICENSE)。第三方开源库（Jetpack Compose、OkHttp、Lottie、Coil、CameraX 等）遵循各自许可证。

素材与商标归属、第三方开源库清单、界面参数获取方式，见 [NOTICE.md](NOTICE.md)。如果权利人已公开客户端素材/界面资源的开源许可，请在 NOTICE.md 顶部补上仓库地址+许可证+获取日期，便于准确署名。

## 联系

- 邮箱：3982548020@qq.com
- 侵权投诉 / 功能建议 / Bug 反馈都发这个邮箱。

## 使用教程
- [导入聊天记录 & 充值 API 余额](docs/usage-import-and-topup.md)
