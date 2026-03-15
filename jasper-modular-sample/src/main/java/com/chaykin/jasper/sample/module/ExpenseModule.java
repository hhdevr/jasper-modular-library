package com.chaykin.jasper.sample.module;

import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.model.SubreportModule;
import com.chaykin.jasper.sample.module.model.ExpenseItem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JasperSubreport(templatePath = "/reports/sub_expense_report.jrxml",
                 prefix = "Expense")
public class ExpenseModule extends SubreportModule {

    private Double totalExpenses;
    private Double growthPercent;
    private List<ExpenseItem> items;

    public ExpenseModule(Double totalExpenses,
                         Double growthPercent,
                         List<ExpenseItem> items) {
        this.totalExpenses = totalExpenses;
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
