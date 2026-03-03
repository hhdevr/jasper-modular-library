package com.chaykin.jasper.sample.module;

import com.chaykin.jasper.core.annotation.JasperModularReport;
import com.chaykin.jasper.core.model.ModularReportModule;
import com.chaykin.jasper.sample.module.model.CompanyDetails;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JasperModularReport(templatePath = "/reports/modular_financial_report.jrxml")
public class ModularFinancialReportModule extends ModularReportModule {

    private CompanyDetails companyDetails;

    private String period;

    private String currency;

    private Double totalRevenue;

    private Double totalExpenses;

    private Double netProfit;

    private RevenueModule revenueModule;

    private ExpenseModule expenseModule;

    private ProfitModule profitModule;

    public ModularFinancialReportModule(CompanyDetails companyDetails,
                                        String period,
                                        String currency,
                                        Double totalRevenue,
                                        Double totalExpenses,
                                        Double netProfit,
                                        RevenueModule revenueModule,
                                        ExpenseModule expenseModule,
                                        ProfitModule profitModule) {
        this.companyDetails = companyDetails;
        this.period = period;
        this.currency = currency;
        this.totalRevenue = totalRevenue;
        this.totalExpenses = totalExpenses;
        this.netProfit = netProfit;
        this.revenueModule = revenueModule;
        this.expenseModule = expenseModule;
        this.profitModule = profitModule;
    }
}
