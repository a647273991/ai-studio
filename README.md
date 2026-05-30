# 🦀 Clawd 桌宠 - 手机悬浮窗版

> 像灵感来自 [Clawd on Desk](https://github.com/rullerzhou-afk/clawd-on-desk) — 原版是桌面像素宠物，这是手机悬浮窗版本

## ✨ 功能

- 🦀 像素螃蟹悬浮在手机屏幕上，覆盖其他 App
- 8 种动画状态：闲逛、思考、打字、开心、睡觉、出错、扫地、杂耍
- 拖拽移动、单击切换状态、长按让它睡觉
- 前台服务保活，通知栏常驻
- 原版 Clawd 主题 GIF 素材

## 📱 安装方式

### 方式一：下载预编译 APK（推荐）

1. 在 GitHub Actions 中找到最新的 Artifacts
2. 下载 `clawd-pet-debug.apk`
3. 安装到手机，授予悬浮窗权限

### 方式二：本地编译

```bash
# 需要 Android SDK + JDK 17
gradle assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

## 🔧 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示在其他 App 上方 |
| `FOREGROUND_SERVICE` | 前台服务保活 |
| `POST_NOTIFICATIONS` | 显示通知栏状态 |

## 📂 项目结构

```
clawd-android/
├── app/src/main/
│   ├── java/com/clawd/pet/
│   │   ├── MainActivity.kt      # 主界面 + 权限请求
│   │   └── FloatingService.kt   # 悬浮窗核心服务
│   ├── assets/
│   │   ├── clawd-pet.html       # 自包含 WebView 页面
│   │   └── *.gif                # 原版像素动画素材
│   ├── res/                     # 布局、图标、主题
│   └── AndroidManifest.xml
├── .github/workflows/build.yml  # GitHub Actions 自动编译
└── build.gradle.kts
```

## 🎮 使用

1. 打开 App → 自动启动悬浮桌宠
2. 🦀 会浮在屏幕上方
3. **拖拽**：移动位置
4. **单击**：切换不同动画状态
5. **长按**：让它睡觉
6. 从通知栏可以返回 App 控制

---

*基于 [Clawd on Desk](https://github.com/rullerzhou-afk/clawd-on-desk) 的像素素材*
