@echo off
cd /d %~dp0
echo Running Option Trading Application...
copy lib\kiteconnect.jar target\
java -cp "target\option-trading-1.0-SNAPSHOT-jar-with-dependencies.jar;lib\kiteconnect.jar" com.optiontrading.OptionTradingTester
pause 