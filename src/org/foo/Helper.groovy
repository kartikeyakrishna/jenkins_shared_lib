package org.foo

class Helper implements Serializable {
    // Reference to the pipeline steps context (allows using pipeline steps like echo, sh, etc. inside classes)
    private final def steps

    Helper(steps) {
        this.steps = steps
    }

    void logInfo(String message) {
        steps.echo "[INFO] ${message}"
    }

    void logWarning(String message) {
        steps.echo "[WARNING] ${message}"
    }
}
