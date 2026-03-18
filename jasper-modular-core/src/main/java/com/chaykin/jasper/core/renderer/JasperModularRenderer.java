package com.chaykin.jasper.core.renderer;

import com.chaykin.jasper.core.exception.JasperModularException;
import com.chaykin.jasper.core.model.ModularReport;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

import java.util.Map;

public class JasperModularRenderer<T extends ModularReport> {

    public JasperPrint render(T module) throws JasperModularException {
        try {
            JasperReport jasperReport = module.compileReport();
            Map<String, Object> parameters = module.fillMapParameters();

            return JasperFillManager.fillReport(jasperReport,
                                                parameters,
                                                new JREmptyDataSource());
        } catch (Exception e) {
            throw new JasperModularException("Error while rendering report: " + module.getModuleClassName(),
                                             e);
        }
    }
}
