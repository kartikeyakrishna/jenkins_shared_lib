def call(Map config = [:]) {
    def projectName = config.get('projectName', env.JOB_NAME)
    echo "Starting Checkmarx scan for project: ${projectName}"
    
    // Example step wrapper executing the CxScanBuilder plugin
    step([
        $class: 'CxScanBuilder',
        credentialsId: config.get('credentialsId', 'checkmarx-creds'),
        projectName: projectName,
        serverUrl: config.get('serverUrl', 'https://checkmarx.example.com'),
        vulnerabilityThresholdResult: config.get('threshold', 'FAILURE')
    ])
}
