package com.commercehub.gradle.cucumber

import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool
import net.masterthought.cucumber.ReportParser
import net.masterthought.cucumber.json.Feature
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSet

import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by jgelais on 6/16/15.
 */
@Slf4j
class CucumberRunner {
    private static final String PLUGIN = '--plugin'

    CucumberRunnerOptions options
    CucumberTestResultCounter testResultCounter
    Map<String, String> systemProperties

    CucumberRunner(CucumberRunnerOptions options, CucumberTestResultCounter testResultCounter,
                   Map<String, String> systemProperties) {
        this.options = options
        this.testResultCounter = testResultCounter
        this.systemProperties = systemProperties
    }

    boolean run(SourceSet sourceSet, File resultsDir, File reportsDir) {
        AtomicBoolean hasFeatureParseErrors = new AtomicBoolean(false)

        def features = sourceSet.resources.matching {
            options.featureRoots.each {
                include("${it}/**/*.feature")
            }
        }

        // cleanup first
        resultsDir.deleteDir()
        resultsDir.mkdirs()

        testResultCounter.beforeSuite(features.files.size())

        if (options.maxParallelForks > 1) {
          GParsPool.withPool(options.maxParallelForks) {
            features.collate(options.maxParallelForks).eachWithIndexParallel { featureGroup, index ->
              List<String> featureFiles = featureGroup.collect { File featureFile -> featureFile.absolutePath }

              File resultsFile = new File(resultsDir, "run-${index}.json")
              File consoleOutLogFile = new File(resultsDir, "run-out-${index}.log")
              File consoleErrLogFile = new File(resultsDir, "run-err-${index}.log")
              File junitResultsFile = new File(resultsDir, "junit-run-${index}.xml")

              List<String> args = []
              options.stepDefinitionRoots.each {
                args << '--glue'
                args << it
              }
              args << PLUGIN
              args << "json:${resultsFile.absolutePath}"
              if (options.junitReport) {
                args << PLUGIN
                args << "junit:${junitResultsFile.absolutePath}"
              }
              if (options.isDryRun) {
                args << '--dry-run'
              }
              if (options.isMonochrome) {
                args << '--monochrome'
              }
              if (options.isStrict) {
                args << '--strict'
              }
              if (!options.tags.isEmpty()) {
                args << '--tags'
                args << options.tags.join(',')
              }
              args << '--snippets'
              args << options.snippets
              featureFiles.each { featureFile ->
                args << featureFile
              }

              new JavaProcessLauncher('cucumber.api.cli.Main', sourceSet.runtimeClasspath.toList())
              .setArgs(args)
              .setConsoleOutStream(consoleOutLogFile.newOutputStream())
              .setConsoleErrStream(consoleErrLogFile.newDataOutputStream())
              .setSystemProperties(systemProperties)
              .execute()
              if (resultsFile.exists()) {
                List<CucumberFeatureResult> results = parseFeatureResult(resultsFile).collect {
                  log.debug("Logging result for $it.name")
                  createResult(it)
                }
                results.each { CucumberFeatureResult result ->
                  testResultCounter.afterFeature(result)
                }
              } else {
                hasFeatureParseErrors.set(true)
                if (consoleErrLogFile.exists()) {
                  log.error(consoleErrLogFile.text)
                }
              }
            }
          }
        } else {
          List<String> featureFiles = features.collect { File featureFile -> featureFile.absolutePath }
          long ts = System.currentTimeMillis()
          File resultsFile = new File(resultsDir, "run-${ts}.json")
          File junitResultsFile = new File(resultsDir, "junit-run-${ts}.xml")

          List<String> args = []
          options.stepDefinitionRoots.each {
            args << '--glue'
            args << it
          }
          args << PLUGIN
          args << "json:${resultsFile.absolutePath}"
          if (options.junitReport) {
            args << PLUGIN
            args << "junit:${junitResultsFile.absolutePath}"
          }
          args << PLUGIN
          args << "pretty"
          if (options.isDryRun) {
            args << '--dry-run'
          }
          if (options.isMonochrome) {
            args << '--monochrome'
          }
          if (options.isStrict) {
            args << '--strict'
          }
          if (!options.tags.isEmpty()) {
            args << '--tags'
            args << options.tags.join(',')
          }
          args << '--snippets'
          args << options.snippets
          featureFiles.each { featureFile ->
            args << featureFile
          }

          new JavaProcessLauncher('cucumber.api.cli.Main', sourceSet.runtimeClasspath.toList())
          .setArgs(args)
          .setConsoleOutStream(System.out)
          .setConsoleErrStream(System.err)
          .setSystemProperties(systemProperties)
          .execute()

          if (resultsFile.exists()) {
            List<CucumberFeatureResult> results = parseFeatureResult(resultsFile).collect {
              log.debug("Logging result for $it.name")
              createResult(it)
            }
            results.each { CucumberFeatureResult result ->
              testResultCounter.afterFeature(result)
            }
          } else {
            hasFeatureParseErrors.set(true)
          }
        }

        if (hasFeatureParseErrors.get()) {
            throw new GradleException('One or more feature files failed to parse. See error output above')
        }

        testResultCounter.afterSuite()
        return !testResultCounter.hadFailures()
    }

    String getFeatureNameFromFile(File file, SourceSet sourceSet) {
        String featureName = file.name
        sourceSet.resources.srcDirs.each { File resourceDir ->
            if (isFileChildOfDirectory(file, resourceDir)) {
                featureName = convertPathToPackage(getReleativePath(file, resourceDir))
            }
        }

        return featureName
    }

    List<Feature> parseFeatureResult(File jsonReport) {
        return new ReportParser([jsonReport.absolutePath]).features[jsonReport.absolutePath]
    }

    CucumberFeatureResult createResult(Feature feature) {
        feature.processSteps()
        CucumberFeatureResult result = new CucumberFeatureResult(
                totalScenarios: feature.numberOfScenarios,
                failedScenarios: feature.numberOfScenariosFailed,
                totalSteps: feature.numberOfSteps,
                failedSteps: feature.numberOfFailures,
                skippedSteps: feature.numberOfSkipped,
                pendingSteps: feature.numberOfPending
        )

        return result
    }

    private String convertPathToPackage(Path path) {
        return path.toString().replace(File.separator, '.')
    }

    private Path getReleativePath(File file, File dir) {
        return Paths.get(dir.toURI()).relativize(Paths.get(file.toURI()))
    }

    private boolean isFileChildOfDirectory(File file, File dir) {
        Path child = Paths.get(file.toURI())
        Path parent = Paths.get(dir.toURI())
        return child.startsWith(parent)
    }
}
