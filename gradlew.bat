@rem Gradle wrapper 启动脚本（自愈版）
@rem 若 gradle-wrapper.jar 不存在（本仓库为纯文本交付，未提交二进制），
@rem 会先从 Gradle 官方仓库下载，再执行构建。
@echo off
setlocal

set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  echo [gradlew] 未找到 gradle-wrapper.jar，正在下载...
  if not exist "%APP_HOME%gradle\wrapper" mkdir "%APP_HOME%gradle\wrapper"
  powershell -NoProfile -Command "try { Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%' } catch { Write-Host $_.Exception.Message; exit 1 }"
  if not exist "%WRAPPER_JAR%" (
    echo [gradlew] 下载失败。请改用以下任一方式：
    echo          1^) 用 Android Studio 打开本目录（推荐，无需 wrapper）
    echo          2^) 自行安装 Gradle 8.9 后直接执行 gradle 命令
    echo          3^) 把 gradle-wrapper.jar 放到 gradle\wrapper\ 目录下
    exit /b 1
  )
)

if "%JAVA_HOME%"=="" (
  set JAVA_EXE=java.exe
) else (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
)

"%JAVA_EXE%" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
