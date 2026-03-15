package com.chaykin.jasper.sample.module;

import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.model.SubreportModule;
import com.chaykin.jasper.sample.module.model.RevenueItem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JasperSubreport(templatePath = "/reports/sub_revenue_report.jrxml",
                 prefix = "Revenue")
public class RevenueModule extends SubreportModule {

    private Double totalRevenue;
    private Double growthPercent;
    private List<RevenueItem> items;

    public RevenueModule(Double totalRevenue,
                         Double growthPercent,
                         List<RevenueItem> items) {
        this.totalRevenue = totalRevenue;
        this.growthPercent = growthPercent;
        this.items = items;
    }

    @Override
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    @Override
    public Class<?> getRootReport() {
        return ModularFinancialReportModule.class;
    }
}
