@echo off
rem Starts LingoLLM on the bundled Java runtime. Options: lingollm --help
rem Extra JVM options can be passed in LINGOLLM_JAVA_OPTS (e.g. -Xmx2g).
setlocal
set "DIR=%~dp0.."
"%DIR%\runtime\bin\java.exe" -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading --enable-native-access=ALL-UNNAMED %LINGOLLM_JAVA_OPTS% -jar "%DIR%\app\lingollm-launcher.jar" %*
