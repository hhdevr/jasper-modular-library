package com.chaykin.jasper.autoconfigure;

/**
 * A JRXML template path and the first report class found to resolve to it.
 */
record ReportTemplate(Class<?> type, String path) {

}
