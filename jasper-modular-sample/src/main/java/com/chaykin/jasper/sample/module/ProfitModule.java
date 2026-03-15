package com.chaykin.jasper.sample.module;

import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.model.SubreportModule;
import com.chaykin.jasper.sample.module.model.ProfitBreakdown;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JasperSubreport(templatePath = "/reports/sub_profit_report.jrxml",
                 prefix = "Profit")
public class ProfitModule extends SubreportModule {

    private Double grossProfit;
    private Double operatingProfit;
    private Double netProfit;
    private Double margin;
    private List<ProfitBreakdown> breakdown;

    public ProfitModule(Double grossProfit,
                        Double operatingProfit,
                        Double netProfit,
                        Double margin,
                        List<ProfitBreakdown> breakdown) {
        this.grossProfit = grossProfit;
        this.operatingProfit = operatingProfit;
        this.netProfit = netProfit;
        this.margin = margin;
        this.breakdown = breakdown;
    }

    @Override
    public boolean isEmpty() {
        return netProfit == null;
    }

    @Override
    public Class<?> getRootReport() {
        return ModularFinancialReportModule.class;
    }
}
