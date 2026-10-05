#!/bin/sh
# Gradle wrapper 启动脚本（自愈版）
# 若 gradle-wrapper.jar 不存在（本仓库为纯文本交付，未提交二进制），
# 会先从 Gradle 官方仓库下载，再执行构建。

APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "[gradlew] 未找到 gradle-wrapper.jar，正在下载..."
  mkdir -p "$APP_HOME/gradle/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$WRAPPER_JAR" "$WRAPPER_URL" || true
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL" || true
  fi
  if [ ! -f "$WRAPPER_JAR" ]; then
    echo "[gradlew] 下载失败。请改用 Android Studio 打开本目录，或自行安装 Gradle 8.9。"
    exit 1
  fi
fi

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi

exec "$JAVA_EXE" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew \
  -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
