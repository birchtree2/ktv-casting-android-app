# ktv-casting-for-android

---

本项目是 [ktv-casting](https://github.com/aspromise/ktv-casting) 的安卓前端

目前采用的分支为 [android-app](https://github.com/StarFreedomX/ktv-casting/tree/android-app) 分支

## 环境配置

如果你有构建发布的需求，复制 `app/local.properties.example` 为 `app/local.properties`，填写你的仓库信息：

```properties
repo_owner=你的GitHub用户名
repo_name=你的仓库名
```

未配置时默认使用 `KARAOKE-MASTER-ZJU/ktv-casting-android-app`。

应用内更新检查读取 `https://<repo_owner>.github.io/<repo_name>/release.json`，需在仓库 Settings 中启用 GitHub Pages（source 选 `gh-pages` 分支），并配置 CI（见 `.github/workflows/build-and-release.yml` 顶部注释）。

## 环境准备

---

```shell
# 克隆 android-app分支的rust源码
git clone -b android-app --single-branch https://github.com/KARAOKE-MASTER-ZJU/ktv-casting.git

cd ktv-casting

rustup target add aarch64-linux-android
cargo ndk -t arm64-v8a build --lib --release
# 产物输出在 ./target/aarch64-linux-android/release/libktv_casting_lib.so

# 下面是更多版本

# rustup target add armv7-linux-androideabi
# cargo ndk -t armeabi-v7a build --lib --release

# rustup target add i686-linux-android
# cargo ndk -t x86 build --lib --release

# rustup target add x86_64-linux-android
# cargo ndk -t x86_64 build --lib --release

cd ..
```
这样就得到了`libktv_casting_lib.so`

接下来克隆本项目
```shell
# 主仓库
git clone https://github.com/KARAOKE-MASTER-ZJU/ktv-casting-android-app.git

# 如果你 fork 了项目，改为你自己的仓库地址，并按上面的 Fork 配置步骤设置 local.properties

cd ktv-casting-android-app
```

打开 [Android Studio](https://developer.android.google.cn/studio?hl=zh-cn) 将文件夹打开为项目

把上一步得到的编译产物放到`app/src/main/jniLibs/arm64-v8a/libktv_casting_lib.so`(这里的架构版本根据上面-t参数选择)

直接在android studio运行调试即可
