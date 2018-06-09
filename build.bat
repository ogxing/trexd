@ECHO OFF
IF EXIST ".\build\" rd /q /s ".\build"
MKDIR build\db
MKDIR build\target\public
.\boot.exe build-cljs
MOVE .\target\public\main.js .\build\target\public\
MOVE .\target\public\index.html .\build\target\public\
.\boot.exe build-server
MOVE .\target\* .\build\