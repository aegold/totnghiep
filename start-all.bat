@echo off
chcp 65001 > nul
setlocal EnableExtensions EnableDelayedExpansion
title KHOI DONG - ADAPTIVE LMS

set "ROOT=d:\Ai Tee\totnghiep"
set "BACKEND_DIR=%ROOT%\be-learning"
set "FRONTEND_DIR=%ROOT%\fe-learning"
set "AI_DIR=%ROOT%\ai-learning"
set "JAVA_DIR=C:\Users\Lenovo\.jdks\ms-21.0.10"
set "MAVEN_DIR=C:\Tools\maven"
set "BACKEND_JAR=target\adaptive-learning-0.1.0.jar"
set "BTITLE=LMS_BACKEND_SERVER"
set "FTITLE=LMS_FRONTEND_CLIENT"
set "ATITLE=LMS_AI_SERVICE"

rem Python chỉ có launcher "py" trên máy này, lệnh "python" không có trong PATH.
rem Ưu tiên bộ diễn giải trong .venv của dịch vụ AI để chạy đúng thư viện đã cài.
set "AIPY=%AI_DIR%\.venv\Scripts\python.exe"
if not exist "!AIPY!" set "AIPY=C:\Users\Lenovo\AppData\Local\Programs\Python\Python313\python.exe"

echo.
echo ================================================================
echo   [KHỞI ĐỘNG HỆ THỐNG ADAPTIVE LMS ^& AI TUTOR]
echo ================================================================
echo.

rem --- Xác định Maven: ưu tiên PATH, sau đó tới thư mục cài đặt ----
set "MVN="
for %%M in (mvn.cmd) do if not defined MVN if exist "%%~$PATH:M" set "MVN=%%M"
if not defined MVN if exist "%MAVEN_DIR%\bin\mvn.cmd" set "MVN=%MAVEN_DIR%\bin\mvn.cmd"
if not defined MVN set "MVN=mvn.cmd"

echo [KIỂM TRA] Ma trước khi khởi động...
if not exist "%BACKEND_DIR%"  ( echo   [X] Không tìm thấy thư mục backend. & goto :fail )
if not exist "%FRONTEND_DIR%" ( echo   [X] Không tìm thấy thư mục frontend. & goto :fail )
if not exist "%AI_DIR%"       ( echo   [X] Không tìm thấy thư mục dịch vụ AI. & goto :fail )
if not exist "%JAVA_DIR%\bin\java.exe" ( echo   [X] Không tìm thấy Java 21 tại %JAVA_DIR%. & goto :fail )
if not exist "%FRONTEND_DIR%\node_modules" ( echo   [X] Chưa cài đặt thư viện frontend. & echo       Chạy: cd /d "%FRONTEND_DIR%" ^&^& npm install & goto :fail )
if not exist "%AI_DIR%\.venv\Scripts\python.exe" ( echo   [!] Dịch vụ AI chưa có môi trường ảo. & echo       Sẽ dùng Python hệ thống: %AIPY% )
echo   [OK] Java 21       : %JAVA_DIR%
echo   [OK] Maven         : %MVN%
echo   [OK] Thư viện FE   : đã sẵn sàng
echo   [OK] Python AI     : %AIPY%

call :isBusy 8080 P8080
call :isBusy 5173 P5173
call :isBusy 8000 P8000
if "!P8080!"=="BUSY" goto :busy
if "!P5173!"=="BUSY" goto :busy
if "!P8000!"=="BUSY" goto :busy

rem --- Biên dịch jar nếu chưa có, thực hiện ngay tại cửa sổ này để cửa sổ
rem     Backend chỉ hiển thị đúng nhật ký của Spring Boot, không lẫn log Maven ---
if exist "%BACKEND_DIR%\%BACKEND_JAR%" (
    echo.
    echo [BIÊN DỊCH] Backend: đã có sẵn file jar, bỏ qua quá trình biên dịch.
) else (
    echo.
    echo [BIÊN DỊCH] Đang biên dịch backend, bỏ qua kiểm thử ^(mất khoảng 20-60 giây^)...
    pushd "%BACKEND_DIR%"
    call "!MVN!" -q -DskipTests package
    popd
    if exist "%BACKEND_DIR%\%BACKEND_JAR%" (
        echo   [OK] Biên dịch thành công.
    ) else (
        echo   [!] Biên dịch không tạo được file jar. Sẽ chạy bằng lệnh Maven thay thế.
    )
)

echo.
echo [KHỞI ĐỘNG] Backend ^(Spring Boot^)...
if exist "%BACKEND_DIR%\%BACKEND_JAR%" (
    rem java -jar tạo ra đúng luồng nhật ký mà IntelliJ hiển thị: biểu ngữ
    rem Spring Boot, rồi tới từng dòng INFO của ứng dụng, không có log Maven xen vào.
    rem Lệnh pause giữ cửa sổ lại nếu máy chủ bị dừng, để còn đọc được log.
    start "%BTITLE%" /D "%BACKEND_DIR%" cmd /k "chcp 65001 >nul & title %BTITLE% & set JAVA_HOME=%JAVA_DIR% & set PATH=%JAVA_DIR%\bin;%PATH% & %JAVA_DIR%\bin\java.exe -Dfile.encoding=UTF-8 -jar %BACKEND_JAR% & echo. & echo   [ĐÃ DỪNG] Backend đã kết thúc. & pause"
) else (
    start "%BTITLE%" /D "%BACKEND_DIR%" cmd /k "chcp 65001 >nul & title %BTITLE% & set JAVA_HOME=%JAVA_DIR% & set PATH=%JAVA_DIR%\bin;%MAVEN_DIR%\bin;%PATH% & !MVN! spring-boot:run & echo. & echo   [ĐÃ DỪNG] Backend đã kết thúc. & pause"
)
echo   Cửa sổ mở: %BTITLE%

echo [KHỞI ĐỘNG] Frontend ^(Vite^)...
start "%FTITLE%" /D "%FRONTEND_DIR%" cmd /k "chcp 65001 >nul & title %FTITLE% & npm run dev"
echo   Cửa sổ mở: %FTITLE%

echo [KHỞI ĐỘNG] Dịch vụ AI ^(FastAPI^)...
rem Dùng & chứ không phải &&: toàn bộ chuỗi nằm trong một dấu nháy kép mà cmd /k
rem truyền vào, nên && sẽ bị cmd hiểu sai thành hai điều kiện rỗng.
start "%ATITLE%" /D "%AI_DIR%" cmd /k "chcp 65001 >nul & title %ATITLE% & "%AIPY%" -m uvicorn main:app --port 8000 & echo. & echo   [ĐÃ DỪNG] Dịch vụ AI đã kết thúc. & pause"
echo   Cửa sổ mở: %ATITLE%

echo.
echo [ĐANG CHỜ] Dịch vụ lên, xin chờ...
set /a WAIT=0
set "BOK=0"
set "FOK=0"
set "AOK=0"
:wait
ping -n 3 127.0.0.1 > nul
set /a WAIT+=2
call :isBusy 8080 P8080
call :isBusy 5173 P5173
call :isBusy 8000 P8000
if "!P8080!"=="BUSY" if "!BOK!"=="0" ( set "BOK=1" & echo   [OK] Backend đang lắng nghe trên cổng 8080. )
if "!P5173!"=="BUSY" if "!FOK!"=="0" ( set "FOK=1" & echo   [OK] Frontend đang lắng nghe trên cổng 5173. )
if "!P8000!"=="BUSY" if "!AOK!"=="0" ( set "AOK=1" & echo   [OK] Dịch vụ AI đang lắng nghe trên cổng 8000. )
if "!BOK!"=="1" if "!FOK!"=="1" if "!AOK!"=="1" goto :ready
if !WAIT! GEQ 150 goto :timeout
goto :wait

:ready
echo.
echo [KIỂM TRA] Phản hồi thật của dịch vụ...
for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://localhost:8080/api/v1/health).StatusCode } catch { 'NO' }"`) do set "HEALTH=%%H"
if "!HEALTH!"=="200" ( echo   [OK] Backend API phản hồi 200. ) else ( echo   [!] Backend API không phản hồi, hãy xem cửa sổ %BTITLE%. )

set "AHEALTH="
for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://localhost:8000/api/v1/health).StatusCode } catch { 'NO' }"`) do set "AHEALTH=%%H"
if "!AHEALTH!"=="200" ( echo   [OK] Dịch vụ AI phản hồi 200. ) else ( echo   [!] Dịch vụ AI không phản hồi, hãy xem cửa sổ %ATITLE%. )

echo.
echo ================================================================
echo   [SẴN SÀNG] Hệ thống đã khởi động thành công.
echo.
echo   Backend API : http://localhost:8080/api/v1
echo   Kiểm tra     : http://localhost:8080/api/v1/health
echo   AI Service   : http://localhost:8000/api/v1
echo   Kiểm tra AI  : http://localhost:8000/api/v1/health
echo   Frontend UI  : http://localhost:5173
echo.
echo   Lưu ý: nếu cơ sở dữ liệu còn trống, hệ thống sẽ tự động khởi
echo   tạo dữ liệu mẫu học thuật (8 tài khoản, 1 khóa học, 6 node kiến
echo   thức, 1 bài thi). Các dòng thông báo này hiện trong cửa sổ %BTITLE%.
echo.
echo   Dừng toàn bộ: chạy stop-all.bat
echo ================================================================
echo.
ping -n 6 127.0.0.1 > nul
endlocal
exit /b 0

:busy
echo.
echo   [X] CỔNG 8080, 5173 HOẶC 8000 ĐANG BỊ CHIẾM.
echo       Hãy chạy stop-all.bat để giải phóng trước khi khởi động lại.
echo.
ping -n 16 127.0.0.1 > nul
endlocal
exit /b 1

:timeout
echo.
echo   [!] Quá thời gian cho phép mà dịch vụ chưa phản hồi.
echo       Backend có thể đang biên dịch hoặc gặp lỗi. Xem cửa sổ %BTITLE%.
echo.
ping -n 10 127.0.0.1 > nul
endlocal
exit /b 1

:fail
echo.
echo   [X] KHỞI ĐỘNG THẤT BẠI. Vui lòng kiểm tra các dòng [X] ở trên.
echo.
pause
endlocal
exit /b 1

:isBusy
rem KHONG dung netstat: tren Windows tieng Viet, cot trang thai cua netstat
rem khong phai "LISTENING" ma la "DANG LANG NGHE", nen so sanh chuoi do
rem se bao sai. Get-NetTCPConnection khong phu thuoc ngon ngu he thong.
set "PORT=%~1"
set "OUT=FREE"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "foreach ($c in (Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue)) { 'BUSY' }"`) do set "OUT=%%A"
rem PHẢI gán lại biến mà lời gọi truyền vào (%~2), nếu không
rem lời gọi sẽ luôn đọc được giá trị rỗng và tưởng cổng đang trống.
set "%~2=%OUT%"
goto :eof