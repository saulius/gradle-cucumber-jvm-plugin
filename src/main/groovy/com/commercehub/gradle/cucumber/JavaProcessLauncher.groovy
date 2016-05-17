package com.commercehub.gradle.cucumber

import java.io.OutputStream
import groovy.util.logging.Slf4j
import org.zeroturnaround.exec.ProcessExecutor

/**
 * Created by jgelais on 6/17/15.
 */
@Slf4j
class JavaProcessLauncher {
    String mainClassName
    List<File> classpath
    List<String> args = []
    OutputStream consoleOutStream
    OutputStream consoleErrStream
    Map<String, String> systemProperties = [:]

    JavaProcessLauncher(String mainClassName, List<File> classpath) {
        this.mainClassName = mainClassName
        this.classpath = classpath
    }

    JavaProcessLauncher setArgs(List<String> args) {
        this.args = args*.toString()
        return this
    }

    JavaProcessLauncher setConsoleOutStream(OutputStream consoleOutStream) {
        this.consoleOutStream = consoleOutStream
        return this
    }

    JavaProcessLauncher setConsoleErrStream(OutputStream consoleErrStream) {
        this.consoleErrStream = consoleErrStream
        return this
    }

    JavaProcessLauncher setSystemProperties(Map<String, String> systemProperties) {
        this.systemProperties = systemProperties
        return this
    }

    int execute() {
        List<String> command = []
        command << javaCommand
        command << '-cp'
        command << classPathAsString
        if (!systemProperties.isEmpty()) {
            systemProperties.keySet().each { key ->
                command << "${key}=${systemProperties.get(key)}".toString()
            }
        }
        command << mainClassName
        command.addAll(args)

        ProcessExecutor processExecutor = new ProcessExecutor().command(command)
        if (consoleOutStream) {
            processExecutor.redirectOutput(consoleOutStream)
        }
        if (consoleErrStream) {
            processExecutor.redirectError(consoleErrStream)
        }
        log.debug("Running command [${command.join(' ')}]")
        return processExecutor.destroyOnExit().execute().exitValue
    }

    String getClassPathAsString() {
        return classpath*.absolutePath.join(System.getProperty('path.separator'))
    }

    static String getJavaCommand() {
        File javaHome = new File(System.getProperty('java.home'))
        return new File(new File(javaHome, 'bin'), javaExecutable).absolutePath
    }

    static String getJavaExecutable() {
        return System.getProperty('os.name').contains('win') ? 'java.exe' : 'java'
    }
}
