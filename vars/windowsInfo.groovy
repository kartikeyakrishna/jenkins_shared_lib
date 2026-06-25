def call() {
    echo "Retrieving Windows environment information..."
    bat 'echo --- OS Version Info ---'
    bat 'ver'
    bat 'echo --- System Info ---'
    bat 'systeminfo | findstr /B /C:"OS Name" /C:"OS Version"'
    bat 'echo --- Current User ---'
    bat 'echo %USERNAME%'
}
