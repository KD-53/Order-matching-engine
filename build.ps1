# Compiles the engine and tests with plain javac, then runs the tests.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (Test-Path out) { Remove-Item -Recurse -Force out }
javac -d out/main (Get-ChildItem -Recurse src/main/java -Filter *.java).FullName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
javac -d out/test -cp out/main (Get-ChildItem -Recurse src/test/java -Filter *.java).FullName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

java -cp "out/main;out/test" engine.OrderBookTest
exit $LASTEXITCODE
