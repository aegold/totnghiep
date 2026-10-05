@echo off
chcp 65001 > nul
setlocal EnableExtensions EnableDelayedExpansion
title DUNG TOAN BO - ADAPTIVE LMS

echo.
echo ================================================================
echo   [DỪNG TOÀN BỘ HỆ THỐNG ADAPTIVE LMS]
echo ================================================================
echo.

echo [LỚP 1] Đóng cửa sổ theo tiêu đề cửa sổ...
taskkill /F /T /FI "WINDOWTITLE eq LMS_BACKEND_SERVER*" > nul 2>&1
taskkill /F /T /FI "WINDOWTITLE eq LMS_FRONTEND_CLIENT*" > nul 2>&1
taskkill /F /T /FI "WINDOWTITLE eq LMS_AI_SERVICE*" > nul 2>&1

rem Lớp 1b: bộ lọc WINDOWTITLE của taskkill chỉ thấy tiến trình sở hữu cửa
rem sổ. Khi Windows Terminal làm ứng dụng mặc định thì cửa sổ thuộc về
rem WindowsTerminal.exe, nên phải dò tiêu đề trên mọi tiến trình.
set "FOUND=0"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "foreach ($p in Get-Process) { if ($p.MainWindowTitle -like '*LMS_BACKEND_SERVER*' -or $p.MainWindowTitle -like '*LMS_FRONTEND_CLIENT*' -or $p.MainWindowTitle -like '*LMS_AI_SERVICE*') { $p.Id } }"`) do call :killWindow %%A
if "!FOUND!"=="0" echo     - Không tìm thấy cửa sổ nào mang tiêu đề LMS.
ping -n 3 127.0.0.1 > nul

echo.
echo [LỚP 2] Giải phóng cổng 8080 (Java), 5173 (Node.js) và 8000 (Python^)...
call :killPort 8080
call :killPort 5173
call :killPort 8000
ping -n 3 127.0.0.1 > nul

rem Lớp 3 quét java/node/python còn tham chiếu dự án. Cố tình lọc theo dòng
rem lệnh: dừng toàn bộ java.exe, node.exe và python.exe sẽ làm hỏng công cụ
rem phát triển (đặc biệt là python.exe, rất dễ đụng phải tiến trình hệ thống).
echo.
echo [LỚP 3] Quét tiến trình java/node/python còn sót của dự án...
set "ORPHAN=0"
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "foreach ($p in Get-CimInstance Win32_Process) { if (($p.Name -eq 'java.exe' -or $p.Name -eq 'node.exe' -or $p.Name -eq 'python.exe') -and $p.CommandLine -match 'be-learning|fe-learning|ai-learning|uvicorn') { $p.ProcessId } }"`) do call :killOrphan %%P
if "!ORPHAN!"=="0" echo     - Không có tiến trình nào của dự án còn sót.
ping -n 2 127.0.0.1 > nul

echo.
echo [KIỂM TRA] Trạng thái cuối cùng...
call :isBusy 8080 R8080
call :isBusy 5173 R5173
call :isBusy 8000 R8000
if "!R8080!"=="BUSY" ( echo     Cổng 8080 : VẪN BỊ CHIẾM ) else ( echo     Cổng 8080 : ĐÃ GIẢI PHÓNG )
if "!R5173!"=="BUSY" ( echo     Cổng 5173 : VẪN BỊ CHIẾM ) else ( echo     Cổng 5173 : ĐÃ GIẢI PHÓNG )
if "!R8000!"=="BUSY" ( echo     Cổng 8000 : VẪN BỊ CHIẾM ) else ( echo     Cổng 8000 : ĐÃ GIẢI PHÓNG )

rem Cho terminal app thời gian đóng hẳn trước khi kết luận về cửa sổ.
ping -n 4 127.0.0.1 > nul
set "WLEFT=0"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "foreach ($p in Get-Process) { if ($p.MainWindowTitle -like '*LMS_BACKEND_SERVER*' -or $p.MainWindowTitle -like '*LMS_FRONTEND_CLIENT*' -or $p.MainWindowTitle -like '*LMS_AI_SERVICE*') { $p.Id } }"`) do set /a WLEFT+=1
if "!WLEFT!"=="0" ( echo     Cửa sổ LMS_BACKEND_SERVER : ĐÃ ĐÓNG ) else ( echo     Cửa sổ LMS_BACKEND_SERVER : VẪN CÒN )
if "!WLEFT!"=="0" ( echo     Cửa sổ LMS_FRONTEND_CLIENT: ĐÃ ĐÓNG ) else ( echo     Cửa sổ LMS_FRONTEND_CLIENT: VẪN CÒN )
if "!WLEFT!"=="0" ( echo     Cửa sổ LMS_AI_SERVICE    : ĐÃ ĐÓNG ) else ( echo     Cửa sổ LMS_AI_SERVICE    : VẪN CÒN )
echo.
echo   Lưu ý: tiêu đề cửa sổ trong Windows Terminal được sinh tự động từ tiến
echo   trình đang chạy, nên có thể đổi theo. Ba cổng và mọi tiến trình
echo   java/node/python của dự án đã được xác nhận ở trên.
echo.
echo ================================================================
echo   [THÀNH CÔNG] Toàn bộ hệ thống đã được dừng an toàn.
echo ================================================================
echo.
ping -n 9 127.0.0.1 > nul
endlocal
exit /b 0

:killWindow
set /a FOUND+=1
echo     - Cửa sổ PID %~1 đang mở, đang đóng...
taskkill /F /T /PID %~1 > nul 2>&1
goto :eof

:killPortOne
set /a KILLED+=1
echo     - Cổng %PORT% đang bị PID %~1 chiếm, đang dừng...
taskkill /F /T /PID %~1 > nul 2>&1
goto :eof

:killOrphan
set /a ORPHAN+=1
echo     - Tiến trình dự án PID %~1 còn sót, đang dừng...
taskkill /F /T /PID %~1 > nul 2>&1
goto :eof

:killPort
set "PORT=%~1"
set "KILLED=0"
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "foreach ($c in (Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue)) { $c.OwningProcess }"`) do call :killPortOne %%P
if "!KILLED!"=="0" echo     - Cổng %PORT% không bị chiếm.
goto :eof

:isBusy
rem KHÔNG dùng netstat: trên Windows tiếng Việt cột trạng thái là
rem "ĐANG LẮNG NGHE" chứ không phải "LISTENING", nên so sánh chuỗi đó sẽ báo sai.
set "PORT=%~1"
set "OUT=FREE"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "foreach ($c in (Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue)) { 'BUSY' }"`) do set "OUT=%%A"
set "%~2=%OUT%"
goto :eof