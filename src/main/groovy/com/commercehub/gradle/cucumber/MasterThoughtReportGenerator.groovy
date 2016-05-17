package com.commercehub.gradle.cucumber

import net.masterthought.cucumber.ReportBuilder

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by jgelais on 12/10/15.
 */
class MasterThoughtReportGenerator implements ReportGenerator {

    CucumberTask parentTask

    MasterThoughtReportGenerator(CucumberTask task) {
        parentTask = task
    }

    @Override
    void generateReport(List<File> jsonReports) {
        List<String> jsonReportFiles = []
        jsonReports.each {
            jsonReportFiles << it.absolutePath
        }

        setJsonFileUriToRelativePaths(jsonReportFiles)

        ReportBuilder reportBuilder = new ReportBuilder(
                jsonReportFiles,
                parentTask.reportsDir,
                '',
                '',
                parentTask.project.name,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true
        )
        reportBuilder.generateReports()
    }

    /**
     *  shortens uri in json files to relative path.
     *  @param List<String> jsonFilesList - list of fully qualified paths to the JSON files to be modified.
     */
    private void setJsonFileUriToRelativePaths(List<String> jsonFileList) {
        String absolutePath
        String relativePath
        jsonFileList.each { String fileName ->
            File thisFile = new File(fileName)
            String content = thisFile.text
            if (content.contains('"uri":')) {
                String featureRoot = "src/${parentTask.sourceSet.name}/resources/"
                Pattern pattern = Pattern.compile('("uri":(?: |)").*' + featureRoot)
                content = content.replaceAll(pattern, "\$1")
                thisFile.write(content)
            }
        }
    }
}
