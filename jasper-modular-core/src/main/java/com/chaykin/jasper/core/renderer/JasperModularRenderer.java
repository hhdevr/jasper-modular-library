package com.chaykin.jasper.core.renderer;

import com.chaykin.jasper.core.exception.JasperModularException;
import com.chaykin.jasper.core.model.ModularReportModule;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

import java.io.ByteArrayOutputStream;
import java.util.Map;

public class JasperModularRenderer<T extends ModularReportModule> {

    public byte[] render(T module) throws JasperModularException {
        try {
            JasperReport jasperReport = module.compileReport();
            Map<String, Object> parameters = module.fillMapParameters();

            JasperPrint print = JasperFillManager.fillReport(jasperReport,
                                                             parameters,
                                                             new JREmptyDataSource());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JasperExportManager.exportReportToPdfStream(print, outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new JasperModularException(
                    "Error while rendering report: " + module.getModuleClassName(), e);
        }
    }
}
