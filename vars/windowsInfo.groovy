def call() {
    echo "Running on node: ${env.NODE_NAME}"
    echo "Workspace: ${env.WORKSPACE}"
    echo "Job: ${env.JOB_NAME}"
    echo "Build number: ${env.BUILD_NUMBER}"

    bat '''
    echo Current directory:
    cd

    echo Windows version:
    ver

    echo Workspace files:
    dir
    '''
}