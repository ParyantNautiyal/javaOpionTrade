@echo off
echo Starting OptionTrading Application...
cd /d %~dp0
mvn clean compile
mvn exec:java -Dexec.mainClass="com.optiontrading.OptionTradingTester"
pause 