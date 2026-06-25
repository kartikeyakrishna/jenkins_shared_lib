def call(Map config = [:]) {
    def sonarEnv = config.get('sonarEnv', 'SonarQube')
    echo "Starting SonarQube scan using environment config: ${sonarEnv}"
    
    withSonarQubeEnv(sonarEnv) {
        if (isUnix()) {
            sh "sonar-scanner"
        } else {
            bat "sonar-scanner.bat"
        }
    }
}
