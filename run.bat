@echo off
cd src
javac -d . DroneSubsystem.java DroneStateMachine.java DroneStatus.java EventLogger.java FaultType.java FireEvent.java FireGUI.java FireIncidentSubsystem.java FireZone.java PerformanceMetricsGenerator.java SchedulerUDP.java
if errorlevel 1 exit /b
start "Scheduler" java -cp . SchedulerUDP
timeout /t 3 /nobreak
for /l %%i in (1,1,20) do (
    start "Drone %%i" java -cp . DroneSubsystem %%i
    timeout /t 1 /nobreak
)
cd ..
start "FireIncident" cmd /k java -cp src FireIncidentSubsystem
pause